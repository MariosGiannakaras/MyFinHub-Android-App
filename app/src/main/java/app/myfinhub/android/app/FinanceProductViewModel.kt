package app.myfinhub.android.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.data.AppendCanonicalEvent
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.CanonicalFinanceMutation
import app.myfinhub.android.core.data.DeactivateCanonicalCard
import app.myfinhub.android.core.data.DeleteCanonicalActivity
import app.myfinhub.android.core.data.EditCanonicalActivity
import app.myfinhub.android.core.data.EncryptedFinanceLocalStore
import app.myfinhub.android.core.data.FinanceLocalSnapshot
import app.myfinhub.android.core.data.FinanceRepository
import app.myfinhub.android.core.data.FinanceSyncState
import app.myfinhub.android.core.data.PendingCanonicalMutationIntent
import app.myfinhub.android.core.data.PendingMutationKind
import app.myfinhub.android.core.data.PendingMutationSyncState
import app.myfinhub.android.core.data.reconcileSatisfiedPendingMutations
import app.myfinhub.android.core.data.UpsertOverallBudget
import app.myfinhub.android.core.data.canonicalCards
import app.myfinhub.android.core.network.AndroidConnectivityObserver
import app.myfinhub.android.core.network.ApiFailureKind
import app.myfinhub.android.core.network.ApiResult
import app.myfinhub.android.core.network.NetworkClientFactory
import app.myfinhub.android.core.network.NetworkStatus
import app.myfinhub.android.core.network.OkHttpMyFinHubApi
import app.myfinhub.android.core.ui.UserNotice
import app.myfinhub.android.core.ui.apiFailureMessage
import app.myfinhub.android.core.ui.offlineUserNotice
import app.myfinhub.android.core.ui.toUserNotice
import app.myfinhub.android.core.ui.unexpectedUserNotice
import app.myfinhub.android.feature.activity.ActivityAction
import app.myfinhub.android.feature.activity.reduceActivity
import app.myfinhub.android.feature.home.HomeAction
import app.myfinhub.android.feature.home.reduceHomeState
import app.myfinhub.android.feature.plan.PlanAction
import app.myfinhub.android.feature.plan.reducePlan
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.reduceQuickEntry
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface FinanceProductState {
    data object Idle : FinanceProductState
    data object Loading : FinanceProductState
    data class Ready(
        val projection: CanonicalProductProjection,
        val saving: Boolean = false,
        val issue: FinanceSyncIssue? = null,
        val offline: Boolean = false,
        val pendingTransactionCount: Int = 0,
        val pendingChangeCount: Int = 0,
        val latestPendingChange: PendingChangeUi? = null,
        val pendingReviewCount: Int = 0,
    ) : FinanceProductState
    data class Failure(
        val message: String,
        val retryable: Boolean,
    ) : FinanceProductState
    data object AuthRejected : FinanceProductState
}

enum class FinanceSyncIssueKind { REVISION_CONFLICT, SAVE_FAILED, WAITING_FOR_NETWORK }

data class FinanceSyncIssue(
    val kind: FinanceSyncIssueKind,
    val message: String,
)

data class PendingChangeUi(
    val label: String,
    val statusLabel: String,
    val canUndo: Boolean,
)

/**
 * Production finance controller with a server-canonical, offline-first read/write boundary.
 *
 * The encrypted device cache contains the last server-accepted canonical document plus a separate
 * ordered queue of stable canonical mutation intents. Every supported finance mutation can be
 * applied optimistically offline and survives process death. Reconnect always loads the newest
 * server revision first. Only intents known never to have been sent replay automatically; an intent
 * is persisted as NEEDS_REVIEW before crossing the write boundary so ambiguous failures are never
 * blindly retried.
 */
class FinanceProductViewModel(application: Application) : AndroidViewModel(application) {
    private val connectivityObserver = AndroidConnectivityObserver(application)
    val networkStatus: StateFlow<NetworkStatus> = connectivityObserver.status.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = connectivityObserver.current(),
    )

    private val repository = FinanceRepository(
        OkHttpMyFinHubApi(
            configuration = AppConfiguration.fromBuildConfig(),
            client = NetworkClientFactory.create(),
        ),
    )
    private val localStore = EncryptedFinanceLocalStore(application)

    private val mutableLastSuccessfulSync = MutableStateFlow<String?>(null)
    val lastSuccessfulSync: StateFlow<String?> = mutableLastSuccessfulSync.asStateFlow()

    private val mutableState = MutableStateFlow<FinanceProductState>(FinanceProductState.Idle)
    val state: StateFlow<FinanceProductState> = mutableState.asStateFlow()

    private val mutableNotices = MutableSharedFlow<UserNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<UserNotice> = mutableNotices.asSharedFlow()

    /** Emits only after a card deactivation is accepted by the canonical server revision. */
    private val mutableCommittedCardDeletions = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val committedCardDeletions: SharedFlow<String> = mutableCommittedCardDeletions.asSharedFlow()

    private var currentSession: AuthSession? = null
    private var automaticSyncAllowed = true
    private var lastServerDocument: CanonicalFinanceDocument? = null
    private var pendingMutations: List<PendingCanonicalMutationIntent> = emptyList()
    private var reloadWhenOnline = false
    private var mutationLaunchInFlight = false
    private var loadJob: Job? = null
    private var mutationJob: Job? = null
    private var autoSyncJob: Job? = null

    init {
        viewModelScope.launch {
            networkStatus.drop(1).collect { status ->
                if (status != NetworkStatus.ONLINE || !automaticSyncAllowed || currentSession == null) return@collect
                val ready = mutableState.value as? FinanceProductState.Ready
                if (reloadWhenOnline || ready?.offline == true || pendingMutations.any { it.syncState == PendingMutationSyncState.NEVER_SENT }) {
                    loadFresh(preserveUi = true)
                }
            }
        }
    }

    fun attachSession(session: AuthSession, allowAutomaticSync: Boolean = true) {
        val previous = currentSession
        if (previous != null && previous.userId != session.userId) {
            cancelOperations()
            lastServerDocument = null
            pendingMutations = emptyList()
            repository.clear()
        }
        currentSession = session
        automaticSyncAllowed = allowAutomaticSync

        val ready = mutableState.value as? FinanceProductState.Ready
        if (previous?.userId == session.userId && ready != null) {
            if (canUseServer() && (ready.offline || reloadWhenOnline || pendingMutations.isNotEmpty())) {
                loadFresh(preserveUi = true)
            }
            return
        }
        loadFresh(preserveUi = false)
    }

    /** Clears only volatile controller state. The encrypted cache survives lock/relaunch by design. */
    fun clear() {
        cancelOperations()
        currentSession = null
        automaticSyncAllowed = true
        lastServerDocument = null
        pendingMutations = emptyList()
        reloadWhenOnline = false
        mutableLastSuccessfulSync.value = null
        repository.clear()
        mutableState.value = FinanceProductState.Idle
    }

    fun retryLoad() {
        if (currentSession == null || loadJob?.isActive == true) return
        loadFresh(preserveUi = false)
    }

    /** Explicit recovery for ambiguous queued work. A fresh server load always happens first. */
    fun retryPendingMutation() {
        if (!canUseServer()) {
            mutableNotices.tryEmit(offlineUserNotice("Συγχρονισμός εκκρεμών αλλαγών"))
            return
        }
        if (pendingMutations.isEmpty()) {
            loadFresh(preserveUi = true)
            return
        }
        loadFresh(preserveUi = true, includeNeedsReview = true)
    }

    fun discardPendingAndReload() {
        // Do not silently discard durable offline work. This route now means "reload/reconcile".
        reloadWhenOnline = false
        loadFresh(preserveUi = false)
    }

    fun undoLatestPendingMutation() {
        val latest = pendingMutations.lastOrNull() ?: return
        if (latest.syncState != PendingMutationSyncState.NEVER_SENT) {
            mutableNotices.tryEmit(
                UserNotice(
                    message = "Η τελευταία αλλαγή έχει ήδη περάσει στο στάδιο επιβεβαίωσης.",
                    details = "Ενέργεια: Αναίρεση αλλαγής\nΚατηγορία: RECONCILIATION_REQUIRED\nΘα φορτωθεί πρώτα η τρέχουσα κατάσταση του server.",
                    diagnosticCode = "MFH-OFFLINE-UNDO-RECONCILE",
                ),
            )
            return
        }
        if (loadJob?.isActive == true || mutationJob?.isActive == true) return

        autoSyncJob?.cancel()
        autoSyncJob = null
        viewModelScope.launch {
            val previous = (mutableState.value as? FinanceProductState.Ready)?.projection
            pendingMutations = undoLatestNeverSentPendingMutation(pendingMutations)
            persistLocalSnapshot()
            renderLocalState(
                previous = previous,
                offline = !canUseServer(),
                issue = reviewIssueOrNull(),
            )
            mutableNotices.emit(
                UserNotice(
                    message = "Η τελευταία εκκρεμής αλλαγή αναιρέθηκε.",
                    details = "Ενέργεια: Αναίρεση αλλαγής\nΚατηγορία: LOCAL_PENDING_UNDONE",
                    diagnosticCode = "MFH-OFFLINE-PENDING-UNDONE",
                ),
            )
            if (canUseServer() && pendingMutations.any { it.syncState == PendingMutationSyncState.NEVER_SENT }) {
                scheduleAutoSync()
            }
        }
    }

    fun onHomeAction(action: HomeAction) {
        updateReady { ready ->
            ready.copy(projection = ready.projection.copy(homeState = reduceHomeState(ready.projection.homeState, action)))
        }
    }

    fun onActivityAction(action: ActivityAction) {
        when (action) {
            is ActivityAction.SaveEdit -> {
                val ready = mutableState.value as? FinanceProductState.Ready ?: return
                if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
                applyMutation(
                    EditCanonicalActivity(
                        transactionId = action.id,
                        note = action.note,
                        category = action.category,
                        nowIso = Instant.now().toString(),
                    ),
                )
            }
            is ActivityAction.Delete -> deleteTransaction(action.id)
            else -> updateReady { ready ->
                ready.copy(projection = ready.projection.copy(activityState = reduceActivity(ready.projection.activityState, action)))
            }
        }
    }

    private fun deleteTransaction(transactionId: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val id = transactionId.trim()
        if (id.isBlank()) return
        applyMutation(DeleteCanonicalActivity(id, Instant.now().toString()))
    }

    fun deleteCard(cardId: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val normalizedCardId = cardId.trim()
        if (normalizedCardId.isBlank() || ready.projection.document.canonicalCards().none { it.id == normalizedCardId && it.active }) {
            mutableNotices.tryEmit(
                UserNotice(
                    message = "Η κάρτα δεν είναι πλέον διαθέσιμη για διαγραφή.",
                    details = "Ενέργεια: Διαγραφή κάρτας\nΚατηγορία: INVALID_CARD_STATE",
                    diagnosticCode = "MFH-APP-INVALID_CARD_STATE",
                ),
            )
            return
        }
        applyMutation(
            DeactivateCanonicalCard(
                cardId = normalizedCardId,
                nowIso = Instant.now().toString(),
            ),
        )
    }

    fun onQuickEntryAction(action: QuickEntryAction) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        if (action != QuickEntryAction.Save) {
            mutableState.value = ready.copy(
                projection = ready.projection.copy(
                    quickEntryState = reduceQuickEntry(ready.projection.quickEntryState, action),
                ),
            )
            return
        }

        val preview = reduceQuickEntry(ready.projection.quickEntryState, QuickEntryAction.Save)
        mutableState.value = ready.copy(projection = ready.projection.copy(quickEntryState = preview))
        if (preview.validationMessage != null) return

        val eventId = "evt-android-${UUID.randomUUID()}"
        val now = Instant.now().toString()
        val mutation = runCatching {
            createQuickEntryCanonicalMutation(
                document = ready.projection.document,
                state = preview,
                eventId = eventId,
                nowIso = now,
            )
        }.getOrElse {
            setQuickEntryError("Η κίνηση δεν είναι έγκυρη.")
            mutableNotices.tryEmit(
                unexpectedUserNotice(
                    operation = "Δημιουργία κίνησης",
                    throwable = it,
                    message = "Η κίνηση δεν μπόρεσε να προετοιμαστεί.",
                ),
            )
            return
        }
        applyMutation(mutation)
    }

    fun onPlanAction(action: PlanAction) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        if (action != PlanAction.SaveBudget) {
            mutableState.value = ready.copy(projection = ready.projection.copy(planState = reducePlan(ready.projection.planState, action)))
            return
        }
        val validated = reducePlan(ready.projection.planState, PlanAction.SaveBudget)
        mutableState.value = ready.copy(projection = ready.projection.copy(planState = validated))
        val amount = validated.budget.monthlyLimitText.replace(',', '.').toDoubleOrNull()
        val threshold = validated.budget.alertThresholdText.toIntOrNull()
        if (amount == null || amount <= 0.0 || threshold == null || threshold !in 1..100) return

        applyMutation(
            UpsertOverallBudget(
                month = YearMonth.now().toString(),
                amount = amount,
                alertThreshold = threshold,
                budgetId = "budget-android-${UUID.randomUUID()}",
                nowIso = Instant.now().toString(),
            ),
        )
    }

    private fun loadFresh(
        preserveUi: Boolean,
        includeNeedsReview: Boolean = false,
    ) {
        val session = currentSession ?: return
        if (loadJob?.isActive == true || mutationJob?.isActive == true) return
        val previous = (mutableState.value as? FinanceProductState.Ready)?.projection.takeIf { preserveUi }

        loadJob = viewModelScope.launch {
            val cached = localStore.load(session.userId)
            if (cached != null) {
                lastServerDocument = cached.serverDocument
                pendingMutations = cached.pendingMutations
                mutableLastSuccessfulSync.value = cached.lastSuccessfulSync
            }

            if (!canUseServer()) {
                reloadWhenOnline = true
                if (lastServerDocument != null) {
                    renderLocalState(previous = previous, offline = true)
                } else {
                    mutableState.value = FinanceProductState.Failure(
                        message = "Δεν υπάρχει ακόμη αποθηκευμένο αντίγραφο οικονομικών δεδομένων για χρήση χωρίς σύνδεση.",
                        retryable = true,
                    )
                }
                return@launch
            }

            synchronizePendingFromServer(
                session = session,
                previousProjection = previous,
                includeNeedsReview = includeNeedsReview,
            )
        }
    }

    private suspend fun synchronizePendingFromServer(
        session: AuthSession,
        previousProjection: CanonicalProductProjection?,
        includeNeedsReview: Boolean,
    ) {
        mutableState.value = if (previousProjection == null && lastServerDocument == null) {
            FinanceProductState.Loading
        } else {
            lastServerDocument?.let { document ->
                readyForDocument(
                    document = applyAllPending(document),
                    previous = previousProjection,
                    saving = true,
                    offline = false,
                )
            } ?: FinanceProductState.Loading
        }

        repository.load(session)
        when (val loaded = repository.state.value) {
            is FinanceSyncState.Ready -> {
                lastServerDocument = loaded.envelope.document
                recordSuccessfulSync(loaded.envelope.lastSavedAt)
                reconcileCommittedPending(loaded.envelope.document)
                persistLocalSnapshot()

                val eligible = if (includeNeedsReview) {
                    pendingMutations
                } else {
                    pendingMutations.takeWhile { it.syncState == PendingMutationSyncState.NEVER_SENT }
                }
                if (eligible.isNotEmpty()) {
                    replayPendingMutations(
                        session = session,
                        serverDocument = loaded.envelope.document,
                        previousProjection = previousProjection,
                        eligible = eligible,
                    )
                } else {
                    renderLocalState(previous = previousProjection, offline = false)
                }
            }
            is FinanceSyncState.Error -> {
                if (loaded.failure.kind == ApiFailureKind.NETWORK && lastServerDocument != null) {
                    reloadWhenOnline = true
                    renderLocalState(previous = previousProjection, offline = true)
                    mutableNotices.emit(offlineUserNotice("Ανανέωση οικονομικών δεδομένων"))
                } else {
                    handleLoadFailure(loaded.failure, "Φόρτωση οικονομικών δεδομένων")
                }
            }
            else -> failLoad("Δεν ήταν δυνατή η φόρτωση των οικονομικών δεδομένων.")
        }
    }

    private suspend fun replayPendingMutations(
        session: AuthSession,
        serverDocument: CanonicalFinanceDocument,
        previousProjection: CanonicalProductProjection?,
        eligible: List<PendingCanonicalMutationIntent>,
    ) {
        if (!canUseServer()) {
            renderLocalState(previousProjection, offline = true)
            return
        }
        val eligibleIds = eligible.map(PendingCanonicalMutationIntent::intentId).toSet()
        // Persist NEEDS_REVIEW before crossing the network write boundary. Any transport failure
        // after this point is ambiguous and automatic reconnect must not replay these intents.
        pendingMutations = pendingMutations.map { pending ->
            if (pending.intentId in eligibleIds) pending.copy(syncState = PendingMutationSyncState.NEEDS_REVIEW) else pending
        }
        persistLocalSnapshot()

        val candidateDocument = runCatching {
            eligible.fold(serverDocument) { document, pending -> pending.asMutation().apply(document) }
        }.getOrElse { error ->
            renderLocalState(previousProjection, offline = false)
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προετοιμασία εκκρεμών αλλαγών",
                    throwable = error,
                    message = "Οι εκκρεμείς αλλαγές διατηρήθηκαν αλλά δεν μπόρεσαν να προετοιμαστούν για συγχρονισμό.",
                ),
            )
            return
        }

        mutableState.value = readyForDocument(
            document = applyAllPending(serverDocument),
            previous = previousProjection,
            saving = true,
            offline = false,
        )
        repository.save(session, candidateDocument)
        when (val saved = repository.state.value) {
            is FinanceSyncState.Ready -> {
                val committed = pendingMutations.filter { it.intentId in eligibleIds }
                pendingMutations = pendingMutations.filterNot { it.intentId in eligibleIds }
                lastServerDocument = saved.envelope.document
                recordSuccessfulSync(saved.envelope.lastSavedAt)
                committed.mapNotNull(PendingCanonicalMutationIntent::affectedCardId).distinct().forEach { cardId ->
                    mutableCommittedCardDeletions.emit(cardId)
                }
                reconcileCommittedPending(saved.envelope.document)
                persistLocalSnapshot()
                renderLocalState(previousProjection, offline = false)
            }
            is FinanceSyncState.Conflict -> {
                persistLocalSnapshot()
                renderLocalState(
                    previous = previousProjection,
                    offline = false,
                    issue = FinanceSyncIssue(
                        FinanceSyncIssueKind.REVISION_CONFLICT,
                        "Οι εκκρεμείς αλλαγές διατηρήθηκαν. Φόρτωσε τα νεότερα δεδομένα πριν επιλέξεις νέα επανάληψη.",
                    ),
                )
            }
            is FinanceSyncState.Error -> {
                persistLocalSnapshot()
                if (saved.failure.kind.isAuthRejection()) {
                    mutableNotices.emit(saved.failure.toUserNotice("Συγχρονισμός εκκρεμών αλλαγών"))
                    repository.clear()
                    mutableState.value = FinanceProductState.AuthRejected
                } else {
                    renderLocalState(
                        previous = previousProjection,
                        offline = saved.failure.kind == ApiFailureKind.NETWORK,
                        issue = FinanceSyncIssue(
                            FinanceSyncIssueKind.SAVE_FAILED,
                            "Οι εκκρεμείς αλλαγές διατηρήθηκαν στη συσκευή. Απαιτείται νέα φόρτωση του server πριν από ρητή επανάληψη.",
                        ),
                    )
                    mutableNotices.emit(saved.failure.toUserNotice("Συγχρονισμός εκκρεμών αλλαγών"))
                }
            }
            else -> {
                persistLocalSnapshot()
                renderLocalState(
                    previous = previousProjection,
                    offline = false,
                    issue = FinanceSyncIssue(
                        FinanceSyncIssueKind.SAVE_FAILED,
                        "Οι εκκρεμείς αλλαγές διατηρήθηκαν στη συσκευή και χρειάζονται ρητή επανάληψη.",
                    ),
                )
            }
        }
    }

    private fun applyMutation(mutation: CanonicalFinanceMutation) {
        val session = currentSession ?: return
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return

        mutationLaunchInFlight = true
        mutationJob = viewModelScope.launch {
            try {
                val queued = queueLocalMutation(
                    session = session,
                    mutation = mutation,
                    previousProjection = ready.projection,
                )
                if (queued && canUseServer()) {
                    scheduleAutoSync()
                }
            } finally {
                mutationLaunchInFlight = false
            }
        }
    }

    private fun scheduleAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            delay(UNDO_GRACE_PERIOD_MILLIS)
            if (canUseServer() && currentSession != null && pendingMutations.any { it.syncState == PendingMutationSyncState.NEVER_SENT }) {
                loadFresh(preserveUi = true)
            }
        }
    }

    private suspend fun queueLocalMutation(
        session: AuthSession,
        mutation: CanonicalFinanceMutation,
        previousProjection: CanonicalProductProjection,
    ): Boolean {
        val serverDocument = lastServerDocument ?: localStore.load(session.userId)?.serverDocument
        if (serverDocument == null) {
            mutableNotices.emit(
                UserNotice(
                    message = "Δεν υπάρχει ασφαλές τοπικό αντίγραφο για αυτή την αλλαγή χωρίς σύνδεση.",
                    details = "Ενέργεια: Αποθήκευση offline αλλαγής\nΚατηγορία: OFFLINE_CACHE_MISSING",
                    diagnosticCode = "MFH-OFFLINE-CACHE-MISSING",
                ),
            )
            return false
        }
        lastServerDocument = serverDocument

        val currentOptimisticDocument = runCatching { applyAllPending(serverDocument) }.getOrElse { error ->
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Ανάκτηση τοπικών αλλαγών",
                    throwable = error,
                    message = "Οι υπάρχουσες τοπικές αλλαγές δεν μπόρεσαν να εφαρμοστούν με ασφάλεια.",
                ),
            )
            return false
        }
        runCatching { mutation.apply(currentOptimisticDocument) }.getOrElse { error ->
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προετοιμασία αλλαγής",
                    throwable = error,
                    message = "Η αλλαγή δεν μπόρεσε να εφαρμοστεί με ασφάλεια.",
                ),
            )
            return false
        }

        val intent = PendingCanonicalMutationIntent.fromMutation(
            mutation = mutation,
            intentId = "mutation-android-${UUID.randomUUID()}",
        )
        val previousQueue = pendingMutations
        // Preserve each user action until server confirmation so Undo can reverse the latest
        // action causally instead of losing intermediate edits/budget changes to compaction.
        pendingMutations = pendingMutations + intent
        val optimisticDocument = runCatching { applyAllPending(serverDocument) }.getOrElse { error ->
            pendingMutations = previousQueue
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προεπισκόπηση τοπικής αλλαγής",
                    throwable = error,
                    message = "Η αλλαγή ακυρώθηκε επειδή δεν μπόρεσε να εμφανιστεί με ασφάλεια.",
                ),
            )
            return false
        }
        persistLocalSnapshot()

        var projection = runCatching {
            projectCanonicalProduct(optimisticDocument, LocalDate.now(), previousProjection)
        }.getOrElse { error ->
            pendingMutations = previousQueue
            persistLocalSnapshot()
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προεπισκόπηση τοπικής αλλαγής",
                    throwable = error,
                    message = "Η αλλαγή ακυρώθηκε επειδή δεν μπόρεσε να εμφανιστεί με ασφάλεια.",
                ),
            )
            return false
        }
        if (mutation is AppendCanonicalEvent) {
            projection = projection.copy(
                quickEntryState = projection.quickEntryState.copy(
                    persisted = false,
                    dirty = false,
                    validationMessage = null,
                ),
            )
        }
        mutableState.value = readyForProjection(
            projection = projection,
            saving = false,
            offline = !canUseServer(),
        )

        if (!canUseServer()) {
            reloadWhenOnline = true
            mutableNotices.emit(
                UserNotice(
                    message = "Η αλλαγή αποθηκεύτηκε στη συσκευή και περιμένει συγχρονισμό.",
                    details = "Ενέργεια: Αποθήκευση offline αλλαγής\nΚατηγορία: PENDING_SYNC\nΘα φορτωθεί πρώτα η νεότερη κατάσταση από τον server πριν σταλεί η αλλαγή.",
                    diagnosticCode = "MFH-OFFLINE-PENDING-SYNC",
                ),
            )
        }
        return true
    }

    private suspend fun reconcileCommittedPending(serverDocument: CanonicalFinanceDocument) {
        if (pendingMutations.isEmpty()) return
        val remaining = reconcileSatisfiedPendingMutations(serverDocument, pendingMutations)
        val remainingIds = remaining.map(PendingCanonicalMutationIntent::intentId).toSet()
        val committed = pendingMutations.filterNot { it.intentId in remainingIds }
        pendingMutations = remaining
        committed.mapNotNull(PendingCanonicalMutationIntent::affectedCardId).distinct().forEach { cardId ->
            mutableCommittedCardDeletions.emit(cardId)
        }
    }

    private suspend fun renderLocalState(
        previous: CanonicalProductProjection?,
        offline: Boolean,
        issue: FinanceSyncIssue? = reviewIssueOrNull(),
    ) {
        val serverDocument = lastServerDocument ?: return
        mutableState.value = readyForDocument(
            document = applyAllPending(serverDocument),
            previous = previous,
            saving = false,
            offline = offline,
            issue = issue,
        )
    }

    private fun readyForDocument(
        document: CanonicalFinanceDocument,
        previous: CanonicalProductProjection?,
        saving: Boolean,
        offline: Boolean,
        issue: FinanceSyncIssue? = null,
    ): FinanceProductState.Ready {
        val projection = projectCanonicalProduct(document, LocalDate.now(), previous)
        return FinanceProductState.Ready(
            projection = markPendingTransactions(projection),
            saving = saving,
            issue = issue,
            offline = offline,
            pendingTransactionCount = pendingTransactionIds().size,
            pendingChangeCount = pendingMutations.size,
            latestPendingChange = latestPendingChangeUi(),
            pendingReviewCount = pendingMutations.count { it.syncState == PendingMutationSyncState.NEEDS_REVIEW },
        )
    }

    private fun readyForProjection(
        projection: CanonicalProductProjection,
        saving: Boolean = false,
        offline: Boolean = false,
        issue: FinanceSyncIssue? = null,
    ): FinanceProductState.Ready = FinanceProductState.Ready(
        projection = markPendingTransactions(projection),
        saving = saving,
        issue = issue,
        offline = offline,
        pendingTransactionCount = pendingTransactionIds().size,
        pendingChangeCount = pendingMutations.size,
        latestPendingChange = latestPendingChangeUi(),
        pendingReviewCount = pendingMutations.count { it.syncState == PendingMutationSyncState.NEEDS_REVIEW },
    )

    private fun applyAllPending(serverDocument: CanonicalFinanceDocument): CanonicalFinanceDocument =
        pendingMutations.fold(serverDocument) { document, pending -> pending.asMutation().apply(document) }

    private fun markPendingTransactions(projection: CanonicalProductProjection): CanonicalProductProjection {
        val ids = pendingTransactionIds()
        if (ids.isEmpty()) return projection
        return projection.copy(
            activityState = projection.activityState.copy(
                items = projection.activityState.items.map { item ->
                    if (item.id in ids) item.copy(pendingSync = true) else item
                },
            ),
        )
    }

    private fun pendingTransactionIds(): Set<String> =
        pendingMutations.mapNotNull(PendingCanonicalMutationIntent::affectedTransactionId)
            .filter(String::isNotBlank)
            .toSet()

    private fun latestPendingChangeUi(): PendingChangeUi? = pendingMutations.lastOrNull()?.let { pending ->
        PendingChangeUi(
            label = when (pending.kind) {
                PendingMutationKind.APPEND_EVENT -> "Νέα κίνηση"
                PendingMutationKind.EDIT_ACTIVITY -> "Επεξεργασία κίνησης"
                PendingMutationKind.DELETE_ACTIVITY -> "Διαγραφή κίνησης"
                PendingMutationKind.UPSERT_OVERALL_BUDGET -> "Αλλαγή budget"
                PendingMutationKind.DEACTIVATE_CARD -> "Διαγραφή κάρτας"
            },
            statusLabel = if (pending.syncState == PendingMutationSyncState.NEVER_SENT) {
                "Προς συγχρονισμό"
            } else {
                "Αναμονή επιβεβαίωσης από τον server"
            },
            canUndo = pending.syncState == PendingMutationSyncState.NEVER_SENT,
        )
    }

    private fun reviewIssueOrNull(): FinanceSyncIssue? =
        if (pendingMutations.any { it.syncState == PendingMutationSyncState.NEEDS_REVIEW }) {
            FinanceSyncIssue(
                FinanceSyncIssueKind.SAVE_FAILED,
                "Υπάρχουν αλλαγές που μπορεί να έχουν φτάσει στον server. Απαιτείται νέα φόρτωση πριν από ρητή επανάληψη.",
            )
        } else {
            null
        }

    private suspend fun persistLocalSnapshot() {
        val session = currentSession ?: return
        val serverDocument = lastServerDocument ?: return
        localStore.save(
            FinanceLocalSnapshot(
                userId = session.userId,
                serverDocument = serverDocument,
                pendingMutations = pendingMutations,
                lastSuccessfulSync = mutableLastSuccessfulSync.value,
            ),
        )
    }

    private suspend fun handleLoadFailure(failure: ApiResult.Failure, operation: String) {
        if (failure.kind == ApiFailureKind.NETWORK) reloadWhenOnline = true
        mutableNotices.emit(failure.toUserNotice(operation))
        if (failure.kind.isAuthRejection()) {
            repository.clear()
            mutableState.value = FinanceProductState.AuthRejected
        } else if (lastServerDocument != null && failure.kind == ApiFailureKind.NETWORK) {
            renderLocalState(
                previous = (mutableState.value as? FinanceProductState.Ready)?.projection,
                offline = true,
            )
        } else {
            mutableState.value = FinanceProductState.Failure(apiFailureMessage(failure.kind), failure.retryable)
        }
    }

    private suspend fun failLoad(message: String) {
        mutableState.value = FinanceProductState.Failure(message, true)
        mutableNotices.emit(
            UserNotice(
                message = message,
                details = "Ενέργεια: Φόρτωση οικονομικών δεδομένων\nΚατηγορία: UNEXPECTED_SYNC_STATE",
                diagnosticCode = "MFH-APP-UNEXPECTED_SYNC_STATE",
            ),
        )
    }

    private fun setQuickEntryError(message: String) {
        updateReady { ready ->
            ready.copy(
                projection = ready.projection.copy(
                    quickEntryState = ready.projection.quickEntryState.copy(
                        validationMessage = message,
                        savedSummary = null,
                        persisted = false,
                    ),
                ),
            )
        }
    }

    private fun cancelOperations() {
        loadJob?.cancel()
        mutationJob?.cancel()
        autoSyncJob?.cancel()
        loadJob = null
        mutationJob = null
        autoSyncJob = null
        mutationLaunchInFlight = false
    }

    private fun recordSuccessfulSync(serverTimestamp: String) {
        mutableLastSuccessfulSync.value = serverTimestamp.takeIf(String::isNotBlank) ?: Instant.now().toString()
        reloadWhenOnline = false
    }

    private fun updateReady(transform: (FinanceProductState.Ready) -> FinanceProductState.Ready) {
        val current = mutableState.value as? FinanceProductState.Ready ?: return
        if (current.saving || current.issue != null) return
        mutableState.value = transform(current)
    }

    private fun canUseServer(): Boolean =
        automaticSyncAllowed && connectivityObserver.current() == NetworkStatus.ONLINE

    private fun offlineMutationNotice(message: String): UserNotice = UserNotice(
        message = message,
        details = "Ενέργεια: Offline συγχρονισμός\nΚατηγορία: CONNECTION_REQUIRED\nΟι τοπικές αλλαγές παραμένουν κρυπτογραφημένες στη συσκευή.",
        diagnosticCode = "MFH-OFFLINE-CONNECTION-REQUIRED",
    )

    private fun ApiFailureKind.isAuthRejection(): Boolean =
        this == ApiFailureKind.AUTH_REQUIRED || this == ApiFailureKind.MFA_REQUIRED

    private companion object {
        const val UNDO_GRACE_PERIOD_MILLIS = 5_000L
    }
}
