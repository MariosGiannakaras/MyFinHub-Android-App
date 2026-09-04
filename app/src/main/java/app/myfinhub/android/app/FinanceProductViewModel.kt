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
import app.myfinhub.android.core.data.PendingTransactionIntent
import app.myfinhub.android.core.data.PendingTransactionSyncState
import app.myfinhub.android.core.data.UpsertOverallBudget
import app.myfinhub.android.core.data.canonicalCards
import app.myfinhub.android.core.data.canonicalEvents
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

/**
 * Production finance controller with a server-canonical, offline-first read/write boundary.
 *
 * The encrypted device cache contains only the last server-accepted canonical document plus a
 * separate queue of stable-id transaction intents. Offline-created transactions are never folded
 * into the cached server snapshot. Reconnect always loads the newest server revision first. Only
 * intents known to have never been sent are replayed automatically; a write that has crossed the
 * network boundary is marked NEEDS_REVIEW before the attempt so an ambiguous failure can never be
 * retried blindly after reconnect or process death.
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
    private var pendingTransactions: List<PendingTransactionIntent> = emptyList()

    /** Non-transaction online mutation retained for explicit conflict/failure recovery. */
    private var pendingMutation: CanonicalFinanceMutation? = null
    private var reloadWhenOnline = false
    private var mutationLaunchInFlight = false
    private var loadJob: Job? = null
    private var mutationJob: Job? = null

    init {
        viewModelScope.launch {
            networkStatus.drop(1).collect { status ->
                if (status != NetworkStatus.ONLINE || !automaticSyncAllowed || currentSession == null) return@collect
                val ready = mutableState.value as? FinanceProductState.Ready
                if (reloadWhenOnline || ready?.offline == true || pendingTransactions.any { it.syncState == PendingTransactionSyncState.NEVER_SENT }) {
                    loadFresh(preserveUi = true)
                }
            }
        }
    }

    fun attachSession(session: AuthSession, allowAutomaticSync: Boolean = true) {
        val previous = currentSession
        if (previous != null && previous.userId != session.userId) {
            cancelOperations()
            pendingMutation = null
            lastServerDocument = null
            pendingTransactions = emptyList()
            repository.clear()
        }
        currentSession = session
        automaticSyncAllowed = allowAutomaticSync

        val ready = mutableState.value as? FinanceProductState.Ready
        if (previous?.userId == session.userId && ready != null) {
            if (canUseServer() && (ready.offline || reloadWhenOnline || pendingTransactions.isNotEmpty())) {
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
        pendingMutation = null
        lastServerDocument = null
        pendingTransactions = emptyList()
        reloadWhenOnline = false
        mutableLastSuccessfulSync.value = null
        repository.clear()
        mutableState.value = FinanceProductState.Idle
    }

    fun retryLoad() {
        if (currentSession == null || loadJob?.isActive == true) return
        loadFresh(preserveUi = false)
    }

    /** Explicit recovery for either an ambiguous queued transaction or a non-transaction mutation. */
    fun retryPendingMutation() {
        if (!canUseServer()) {
            mutableNotices.tryEmit(offlineUserNotice("Συγχρονισμός εκκρεμών κινήσεων"))
            return
        }
        if (pendingTransactions.isNotEmpty()) {
            loadFresh(preserveUi = true, includeNeedsReview = true)
            return
        }

        val session = currentSession ?: return
        val mutation = pendingMutation ?: return
        if (mutationLaunchInFlight || mutationJob?.isActive == true) return
        val previousProjection = (mutableState.value as? FinanceProductState.Ready)?.projection
        mutationLaunchInFlight = true
        mutationJob = viewModelScope.launch {
            try {
                mutableState.value = FinanceProductState.Loading
                repository.load(session)
                when (val loaded = repository.state.value) {
                    is FinanceSyncState.Ready -> {
                        lastServerDocument = loaded.envelope.document
                        recordSuccessfulSync(loaded.envelope.lastSavedAt)
                        persistLocalSnapshot()
                        saveMutation(
                            session = session,
                            mutation = mutation,
                            baseDocument = loaded.envelope.document,
                            previousProjection = previousProjection,
                        )
                    }
                    is FinanceSyncState.Error -> handleLoadFailure(loaded.failure, "Επαναφόρτωση οικονομικών δεδομένων")
                    else -> failLoad("Δεν ήταν δυνατή η επαναφόρτωση των δεδομένων.")
                }
            } finally {
                mutationLaunchInFlight = false
            }
        }
    }

    fun discardPendingAndReload() {
        pendingMutation = null
        reloadWhenOnline = false
        loadFresh(preserveUi = false)
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
                if (action.id in pendingTransactionIds()) {
                    mutableNotices.tryEmit(
                        offlineMutationNotice("Η τοπική κίνηση μπορεί να διορθωθεί αφού συγχρονιστεί ή να ακυρωθεί τώρα."),
                    )
                    return
                }
                if (!canUseServer()) {
                    mutableNotices.tryEmit(offlineMutationNotice("Η επεξεργασία συγχρονισμένης κίνησης χρειάζεται επαληθευμένη σύνδεση."))
                    return
                }
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

        if (id in pendingTransactionIds()) {
            viewModelScope.launch {
                pendingTransactions = pendingTransactions.filterNot { it.eventId == id }
                persistLocalSnapshot()
                renderLocalState(previous = ready.projection, offline = !canUseServer())
                mutableNotices.emit(
                    UserNotice(
                        message = "Η τοπική κίνηση ακυρώθηκε.",
                        details = "Ενέργεια: Ακύρωση εκκρεμούς κίνησης\nΚατηγορία: LOCAL_PENDING_REMOVED",
                        diagnosticCode = "MFH-OFFLINE-PENDING-REMOVED",
                    ),
                )
            }
            return
        }

        if (!canUseServer()) {
            mutableNotices.tryEmit(offlineMutationNotice("Η διαγραφή συγχρονισμένης κίνησης χρειάζεται επαληθευμένη σύνδεση."))
            return
        }
        applyMutation(DeleteCanonicalActivity(id, Instant.now().toString()))
    }

    fun deleteCard(cardId: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        if (!canUseServer()) {
            mutableNotices.tryEmit(offlineMutationNotice("Η διαγραφή κάρτας χρειάζεται επαληθευμένη σύνδεση."))
            return
        }
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
        if (!canUseServer()) {
            mutableNotices.tryEmit(offlineMutationNotice("Η αλλαγή budget χρειάζεται επαληθευμένη σύνδεση."))
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
                pendingTransactions = cached.pendingTransactions
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

            mutableState.value = if (previous == null) FinanceProductState.Loading else {
                (mutableState.value as? FinanceProductState.Ready)?.copy(saving = true, issue = null)
                    ?: FinanceProductState.Loading
            }
            repository.load(session)
            when (val loaded = repository.state.value) {
                is FinanceSyncState.Ready -> {
                    lastServerDocument = loaded.envelope.document
                    recordSuccessfulSync(loaded.envelope.lastSavedAt)
                    reconcileCommittedPending(loaded.envelope.document)
                    persistLocalSnapshot()

                    val eligible = pendingTransactions.filter {
                        it.syncState == PendingTransactionSyncState.NEVER_SENT ||
                            (includeNeedsReview && it.syncState == PendingTransactionSyncState.NEEDS_REVIEW)
                    }
                    if (eligible.isNotEmpty()) {
                        replayPendingTransactions(
                            session = session,
                            serverDocument = loaded.envelope.document,
                            previousProjection = previous,
                            eligible = eligible,
                        )
                    } else {
                        renderLocalState(previous = previous, offline = false)
                    }
                }
                is FinanceSyncState.Error -> {
                    if (loaded.failure.kind == ApiFailureKind.NETWORK && lastServerDocument != null) {
                        reloadWhenOnline = true
                        renderLocalState(previous = previous, offline = true)
                        mutableNotices.emit(offlineUserNotice("Ανανέωση οικονομικών δεδομένων"))
                    } else {
                        handleLoadFailure(loaded.failure, "Φόρτωση οικονομικών δεδομένων")
                    }
                }
                else -> failLoad("Δεν ήταν δυνατή η φόρτωση των οικονομικών δεδομένων.")
            }
        }
    }

    private suspend fun replayPendingTransactions(
        session: AuthSession,
        serverDocument: CanonicalFinanceDocument,
        previousProjection: CanonicalProductProjection?,
        eligible: List<PendingTransactionIntent>,
    ) {
        if (!canUseServer()) {
            renderLocalState(previousProjection, offline = true)
            return
        }
        val eligibleIds = eligible.map(PendingTransactionIntent::eventId).toSet()
        // Persist NEEDS_REVIEW before crossing the write boundary. From here on, any transport
        // failure is ambiguous and automatic reconnect must not send these intents again.
        pendingTransactions = pendingTransactions.map { pending ->
            if (pending.eventId in eligibleIds) pending.copy(syncState = PendingTransactionSyncState.NEEDS_REVIEW) else pending
        }
        persistLocalSnapshot()

        val candidateDocument = runCatching {
            eligible.fold(serverDocument) { document, pending -> pending.asMutation().apply(document) }
        }.getOrElse { error ->
            renderLocalState(previousProjection, offline = false)
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προετοιμασία εκκρεμών κινήσεων",
                    throwable = error,
                    message = "Οι εκκρεμείς κινήσεις διατηρήθηκαν αλλά δεν μπόρεσαν να προετοιμαστούν για συγχρονισμό.",
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
                pendingTransactions = pendingTransactions.filterNot { it.eventId in eligibleIds }
                lastServerDocument = saved.envelope.document
                recordSuccessfulSync(saved.envelope.lastSavedAt)
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
                        "Οι εκκρεμείς κινήσεις διατηρήθηκαν. Φόρτωσε τα νεότερα δεδομένα πριν επιλέξεις νέα επανάληψη.",
                    ),
                )
            }
            is FinanceSyncState.Error -> {
                persistLocalSnapshot()
                if (saved.failure.kind.isAuthRejection()) {
                    mutableNotices.emit(saved.failure.toUserNotice("Συγχρονισμός εκκρεμών κινήσεων"))
                    repository.clear()
                    mutableState.value = FinanceProductState.AuthRejected
                } else {
                    renderLocalState(
                        previous = previousProjection,
                        offline = saved.failure.kind == ApiFailureKind.NETWORK,
                        issue = FinanceSyncIssue(
                            FinanceSyncIssueKind.SAVE_FAILED,
                            "Οι εκκρεμείς κινήσεις διατηρήθηκαν στη συσκευή. Απαιτείται ρητή επανάληψη μετά από νέα φόρτωση του server.",
                        ),
                    )
                    mutableNotices.emit(saved.failure.toUserNotice("Συγχρονισμός εκκρεμών κινήσεων"))
                }
            }
            else -> {
                persistLocalSnapshot()
                renderLocalState(
                    previous = previousProjection,
                    offline = false,
                    issue = FinanceSyncIssue(
                        FinanceSyncIssueKind.SAVE_FAILED,
                        "Οι εκκρεμείς κινήσεις διατηρήθηκαν στη συσκευή και χρειάζονται ρητή επανάληψη.",
                    ),
                )
            }
        }
    }

    private fun applyMutation(mutation: CanonicalFinanceMutation) {
        val session = currentSession ?: return
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return

        if (!canUseServer()) {
            if (mutation is AppendCanonicalEvent) {
                mutationLaunchInFlight = true
                mutationJob = viewModelScope.launch {
                    try {
                        queueOfflineTransaction(session, mutation, ready.projection)
                    } finally {
                        mutationLaunchInFlight = false
                    }
                }
            } else {
                mutableNotices.tryEmit(offlineMutationNotice("Αυτή η αλλαγή χρειάζεται επαληθευμένη σύνδεση."))
            }
            return
        }

        mutationLaunchInFlight = true
        mutationJob = viewModelScope.launch {
            try {
                saveMutation(
                    session = session,
                    mutation = mutation,
                    baseDocument = ready.projection.document,
                    previousProjection = ready.projection,
                )
            } finally {
                mutationLaunchInFlight = false
            }
        }
    }

    private suspend fun queueOfflineTransaction(
        session: AuthSession,
        mutation: AppendCanonicalEvent,
        previousProjection: CanonicalProductProjection,
    ) {
        val serverDocument = lastServerDocument ?: localStore.load(session.userId)?.serverDocument
        if (serverDocument == null) {
            mutableNotices.emit(
                UserNotice(
                    message = "Δεν υπάρχει ασφαλές τοπικό αντίγραφο για νέα κίνηση χωρίς σύνδεση.",
                    details = "Ενέργεια: Αποθήκευση offline κίνησης\nΚατηγορία: OFFLINE_CACHE_MISSING",
                    diagnosticCode = "MFH-OFFLINE-CACHE-MISSING",
                ),
            )
            return
        }
        lastServerDocument = serverDocument
        val id = mutation.event.string("id").orEmpty()
        if (id.isBlank()) return
        if (pendingTransactions.none { it.eventId == id }) {
            pendingTransactions = pendingTransactions + PendingTransactionIntent(
                event = mutation.event,
                nowIso = mutation.nowIso,
                syncState = PendingTransactionSyncState.NEVER_SENT,
            )
        }
        persistLocalSnapshot()
        val projection = readyForDocument(
            document = applyAllPending(serverDocument),
            previous = previousProjection,
            saving = false,
            offline = true,
        ).projection.copy(
            quickEntryState = previousProjection.quickEntryState.copy(
                persisted = false,
                dirty = false,
                validationMessage = null,
            ),
        )
        mutableState.value = FinanceProductState.Ready(
            projection = projection,
            offline = true,
            pendingTransactionCount = pendingTransactions.size,
            pendingReviewCount = pendingTransactions.count { it.syncState == PendingTransactionSyncState.NEEDS_REVIEW },
        )
        reloadWhenOnline = true
        mutableNotices.emit(
            UserNotice(
                message = "Η κίνηση αποθηκεύτηκε στη συσκευή και περιμένει συγχρονισμό.",
                details = "Ενέργεια: Αποθήκευση offline κίνησης\nΚατηγορία: PENDING_SYNC\nΗ κίνηση θα σταλεί μόνο αφού φορτωθεί πρώτα η νεότερη κατάσταση από τον server.",
                diagnosticCode = "MFH-OFFLINE-PENDING-SYNC",
            ),
        )
    }

    private suspend fun saveMutation(
        session: AuthSession,
        mutation: CanonicalFinanceMutation,
        baseDocument: CanonicalFinanceDocument,
        previousProjection: CanonicalProductProjection?,
    ) {
        val localDocument = runCatching { mutation.apply(baseDocument) }.getOrElse {
            previousProjection?.let { projection -> mutableState.value = readyForProjection(projection) }
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προετοιμασία αλλαγής",
                    throwable = it,
                    message = "Η αλλαγή δεν μπόρεσε να εφαρμοστεί με ασφάλεια.",
                ),
            )
            return
        }
        pendingMutation = mutation
        var localProjection = runCatching {
            projectCanonicalProduct(localDocument, LocalDate.now(), previousProjection)
        }.getOrElse {
            pendingMutation = null
            previousProjection?.let { projection -> mutableState.value = readyForProjection(projection) }
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προεπισκόπηση αλλαγής",
                    throwable = it,
                    message = "Η αλλαγή ακυρώθηκε επειδή δεν μπόρεσε να εμφανιστεί με ασφάλεια.",
                ),
            )
            return
        }

        if (!canUseServer()) {
            if (mutation is AppendCanonicalEvent && previousProjection != null) {
                pendingMutation = null
                queueOfflineTransaction(session, mutation, previousProjection)
            } else {
                pendingMutation = null
                previousProjection?.let { mutableState.value = readyForProjection(it, offline = true) }
                mutableNotices.emit(offlineMutationNotice("Η αλλαγή δεν στάλθηκε και χρειάζεται επαληθευμένη σύνδεση."))
            }
            return
        }

        mutableState.value = readyForProjection(localProjection, saving = true)
        repository.save(session, localDocument)
        when (val saved = repository.state.value) {
            is FinanceSyncState.Ready -> {
                var projection = runCatching {
                    projectCanonicalProduct(saved.envelope.document, LocalDate.now(), localProjection)
                }.getOrElse {
                    mutableState.value = readyForProjection(localProjection)
                    mutableNotices.emit(
                        unexpectedUserNotice(
                            operation = "Ανανέωση μετά την αποθήκευση",
                            throwable = it,
                            message = "Η αλλαγή αποθηκεύτηκε, αλλά η οθόνη δεν ανανεώθηκε πλήρως.",
                        ),
                    )
                    pendingMutation = null
                    return
                }
                projection = when (mutation) {
                    is AppendCanonicalEvent -> projection.copy(
                        quickEntryState = projection.quickEntryState.copy(persisted = true, dirty = false),
                    )
                    is UpsertOverallBudget -> projection.copy(
                        planState = projection.planState.copy(message = "Το budget αποθηκεύτηκε στο canonical state."),
                    )
                    else -> projection
                }
                pendingMutation = null
                lastServerDocument = saved.envelope.document
                recordSuccessfulSync(saved.envelope.lastSavedAt)
                reconcileCommittedPending(saved.envelope.document)
                persistLocalSnapshot()
                mutableState.value = readyForProjection(projection)
                if (mutation is DeactivateCanonicalCard) {
                    mutableCommittedCardDeletions.emit(mutation.cardId)
                }
            }
            is FinanceSyncState.Conflict -> {
                if (mutation is AppendCanonicalEvent) {
                    pendingMutation = null
                    queueAttemptedTransaction(mutation)
                    localProjection = markPendingTransactions(localProjection)
                }
                val message = "Τα δεδομένα άλλαξαν σε άλλη συνεδρία. Η αλλαγή διατηρείται μέχρι να φορτωθούν τα νεότερα δεδομένα και να επιλέξεις επανάληψη."
                mutableState.value = FinanceProductState.Ready(
                    projection = localProjection,
                    issue = FinanceSyncIssue(FinanceSyncIssueKind.REVISION_CONFLICT, message),
                    pendingTransactionCount = pendingTransactions.size,
                    pendingReviewCount = pendingTransactions.count { it.syncState == PendingTransactionSyncState.NEEDS_REVIEW },
                )
                mutableNotices.emit(
                    UserNotice(
                        message = "Υπάρχει νεότερη έκδοση των δεδομένων.",
                        details = "Ενέργεια: Αποθήκευση οικονομικών δεδομένων\nΚατηγορία: REVISION_CONFLICT\nΗ αλλαγή διατηρείται μέχρι ρητή επανάληψη μετά από νέα φόρτωση.",
                        diagnosticCode = "MFH-API-REVISION_CONFLICT-409",
                    ),
                )
            }
            is FinanceSyncState.Error -> {
                if (mutation is AppendCanonicalEvent) {
                    pendingMutation = null
                    queueAttemptedTransaction(mutation)
                    localProjection = markPendingTransactions(localProjection)
                }
                if (saved.failure.kind.isAuthRejection()) {
                    mutableNotices.emit(saved.failure.toUserNotice("Αποθήκευση οικονομικών δεδομένων"))
                    repository.clear()
                    mutableState.value = FinanceProductState.AuthRejected
                } else {
                    val message = apiFailureMessage(saved.failure.kind)
                    mutableState.value = FinanceProductState.Ready(
                        projection = localProjection,
                        issue = FinanceSyncIssue(FinanceSyncIssueKind.SAVE_FAILED, message),
                        offline = saved.failure.kind == ApiFailureKind.NETWORK,
                        pendingTransactionCount = pendingTransactions.size,
                        pendingReviewCount = pendingTransactions.count { it.syncState == PendingTransactionSyncState.NEEDS_REVIEW },
                    )
                    mutableNotices.emit(saved.failure.toUserNotice("Αποθήκευση οικονομικών δεδομένων"))
                }
            }
            else -> {
                if (mutation is AppendCanonicalEvent) {
                    pendingMutation = null
                    queueAttemptedTransaction(mutation)
                    localProjection = markPendingTransactions(localProjection)
                }
                val message = "Η αποθήκευση δεν ολοκληρώθηκε."
                mutableState.value = FinanceProductState.Ready(
                    localProjection,
                    issue = FinanceSyncIssue(FinanceSyncIssueKind.SAVE_FAILED, message),
                    pendingTransactionCount = pendingTransactions.size,
                    pendingReviewCount = pendingTransactions.count { it.syncState == PendingTransactionSyncState.NEEDS_REVIEW },
                )
                mutableNotices.emit(
                    UserNotice(
                        message = message,
                        details = "Ενέργεια: Αποθήκευση οικονομικών δεδομένων\nΚατηγορία: UNEXPECTED_SYNC_STATE",
                        diagnosticCode = "MFH-APP-UNEXPECTED_SYNC_STATE",
                    ),
                )
            }
        }
    }

    private suspend fun queueAttemptedTransaction(mutation: AppendCanonicalEvent) {
        val id = mutation.event.string("id").orEmpty()
        if (id.isBlank()) return
        val existing = pendingTransactions.indexOfFirst { it.eventId == id }
        val pending = PendingTransactionIntent(
            event = mutation.event,
            nowIso = mutation.nowIso,
            syncState = PendingTransactionSyncState.NEEDS_REVIEW,
        )
        pendingTransactions = if (existing >= 0) {
            pendingTransactions.toMutableList().apply { this[existing] = pending }
        } else {
            pendingTransactions + pending
        }
        persistLocalSnapshot()
    }

    private suspend fun reconcileCommittedPending(serverDocument: CanonicalFinanceDocument) {
        val serverIds = serverDocument.canonicalEvents().map { it.id }.toSet()
        if (serverIds.isEmpty() || pendingTransactions.isEmpty()) return
        pendingTransactions = pendingTransactions.filterNot { it.eventId in serverIds }
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
            pendingTransactionCount = pendingTransactions.size,
            pendingReviewCount = pendingTransactions.count { it.syncState == PendingTransactionSyncState.NEEDS_REVIEW },
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
        pendingTransactionCount = pendingTransactions.size,
        pendingReviewCount = pendingTransactions.count { it.syncState == PendingTransactionSyncState.NEEDS_REVIEW },
    )

    private fun applyAllPending(serverDocument: CanonicalFinanceDocument): CanonicalFinanceDocument =
        pendingTransactions.fold(serverDocument) { document, pending -> pending.asMutation().apply(document) }

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
        pendingTransactions.map(PendingTransactionIntent::eventId).filter(String::isNotBlank).toSet()

    private fun reviewIssueOrNull(): FinanceSyncIssue? =
        if (pendingTransactions.any { it.syncState == PendingTransactionSyncState.NEEDS_REVIEW }) {
            FinanceSyncIssue(
                FinanceSyncIssueKind.SAVE_FAILED,
                "Υπάρχουν κινήσεις που μπορεί να έχουν φτάσει στον server. Απαιτείται νέα φόρτωση πριν από ρητή επανάληψη.",
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
                pendingTransactions = pendingTransactions,
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
        loadJob = null
        mutationJob = null
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
        details = "Ενέργεια: Offline αλλαγή\nΚατηγορία: CONNECTION_REQUIRED\nΟι ήδη φορτωμένες οικονομικές πληροφορίες παραμένουν διαθέσιμες.",
        diagnosticCode = "MFH-OFFLINE-CONNECTION-REQUIRED",
    )

    private fun ApiFailureKind.isAuthRejection(): Boolean =
        this == ApiFailureKind.AUTH_REQUIRED || this == ApiFailureKind.MFA_REQUIRED
}
