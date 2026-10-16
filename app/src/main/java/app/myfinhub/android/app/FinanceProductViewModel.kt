package app.myfinhub.android.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.data.AppendCanonicalEvent
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.CanonicalFinanceMutation
import app.myfinhub.android.core.data.CreateCanonicalCard
import app.myfinhub.android.core.data.DeactivateCanonicalCard
import app.myfinhub.android.core.data.confirmedPendingCardSecretCleanupIds
import app.myfinhub.android.core.data.CompleteCanonicalCardSecretCleanup
import app.myfinhub.android.core.data.RestoreCanonicalCard
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
import app.myfinhub.android.core.data.undoLatestNeverSentPendingMutation
import app.myfinhub.android.core.data.UpsertOverallBudget
import app.myfinhub.android.core.data.SetCanonicalRecurringStatus
import app.myfinhub.android.core.data.UpsertCanonicalSavingsGoal
import app.myfinhub.android.core.data.DeleteCanonicalSavingsGoal
import app.myfinhub.android.core.data.allCanonicalRecurringItems
import app.myfinhub.android.core.data.canonicalCards
import app.myfinhub.android.core.network.AndroidConnectivityObserver
import app.myfinhub.android.core.network.ApiFailureKind
import app.myfinhub.android.core.network.ApiResult
import app.myfinhub.android.core.network.FinancialProviderRecord
import app.myfinhub.android.core.network.DeviceSessionRecord
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
import app.myfinhub.android.feature.money.CardCreateRequest
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

data class DeviceSessionsUiSnapshot(
    val loaded: Boolean = false,
    val devices: List<DeviceSessionRecord> = emptyList(),
)

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

    private val providerAwareApi = OkHttpMyFinHubApi(
        configuration = AppConfiguration.fromBuildConfig(),
        client = NetworkClientFactory.create(),
    )
    private val repository = FinanceRepository(providerAwareApi)
    private val mutableDeviceSessions = MutableStateFlow(DeviceSessionsUiSnapshot())
    /** Sensitive per-session metadata is not cached to disk or included in finance revisions. */
    val deviceSessions: StateFlow<DeviceSessionsUiSnapshot> = mutableDeviceSessions.asStateFlow()
    private val mutableFinancialProviderCatalog = MutableStateFlow<List<FinancialProviderRecord>>(emptyList())
    /** In-memory, per-auth-session read-only provider metadata. Never persisted as finance data. */
    val financialProviderCatalog: StateFlow<List<FinancialProviderRecord>> =
        mutableFinancialProviderCatalog.asStateFlow()
    private val localStore = EncryptedFinanceLocalStore(application)

    private val mutableLastSuccessfulSync = MutableStateFlow<String?>(null)
    val lastSuccessfulSync: StateFlow<String?> = mutableLastSuccessfulSync.asStateFlow()

    private val mutableState = MutableStateFlow<FinanceProductState>(FinanceProductState.Idle)
    val state: StateFlow<FinanceProductState> = mutableState.asStateFlow()

    private val mutableNotices = MutableSharedFlow<UserNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<UserNotice> = mutableNotices.asSharedFlow()

    /** Only confirmed *permanent* deletions may remove card vault secrets.
     * Ordinary reversible archives/restores must retain encrypted card details.
     */
    private var currentSession: AuthSession? = null
    private var automaticSyncAllowed = true
    private var lastServerDocument: CanonicalFinanceDocument? = null
    private var pendingMutations: List<PendingCanonicalMutationIntent> = emptyList()
    private var reloadWhenOnline = false
    private var mutationLaunchInFlight = false
    private var loadJob: Job? = null
    private var mutationJob: Job? = null
    private var providerCatalogJob: Job? = null
    private var deviceSessionsJob: Job? = null

    init {
        viewModelScope.launch {
            networkStatus.drop(1).collect { status ->
                if (status != NetworkStatus.ONLINE || !automaticSyncAllowed || currentSession == null) return@collect
                val ready = mutableState.value as? FinanceProductState.Ready
                if (mutableFinancialProviderCatalog.value.isEmpty()) {
                    currentSession?.let(::refreshFinancialProviderCatalog)
                }
                if (!mutableDeviceSessions.value.loaded) currentSession?.let(::refreshDeviceSessions)
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
        if (previous?.userId != session.userId || previous.accessToken != session.accessToken ||
            mutableFinancialProviderCatalog.value.isEmpty()) {
            refreshFinancialProviderCatalog(session)
        }
        if (previous?.userId != session.userId || previous.accessToken != session.accessToken ||
            !mutableDeviceSessions.value.loaded) refreshDeviceSessions(session)

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
        providerCatalogJob?.cancel()
        providerCatalogJob = null
        mutableFinancialProviderCatalog.value = emptyList()
        deviceSessionsJob?.cancel()
        deviceSessionsJob = null
        mutableDeviceSessions.value = DeviceSessionsUiSnapshot()
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
        if (canUseServer()) {
            mutableNotices.tryEmit(
                UserNotice(
                    message = "Η αναίρεση εκκρεμούς αλλαγής είναι διαθέσιμη μόνο για αλλαγές που αποθηκεύτηκαν offline.",
                    details = "Ενέργεια: Αναίρεση αλλαγής\nΚατηγορία: OFFLINE_ONLY_UNDO\nΟι online αλλαγές αποστέλλονται άμεσα στον server.",
                    diagnosticCode = "MFH-OFFLINE-UNDO-ONLY",
                ),
            )
            return
        }
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
                        date = action.date,
                        subcategory = action.subcategory,
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

    fun createCard(request: CardCreateRequest) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        if (ready.projection.document.canonicalCards().any { it.id == request.cardId }) {
            mutableNotices.tryEmit(
                UserNotice(
                    message = "Η κάρτα δεν μπόρεσε να προστεθεί με ασφάλεια.",
                    details = "Ενέργεια: Προσθήκη κάρτας\nΚατηγορία: DUPLICATE_CARD_ID",
                    diagnosticCode = "MFH-APP-DUPLICATE-CARD-ID",
                ),
            )
            return
        }
        applyMutation(
            CreateCanonicalCard(
                cardId = request.cardId,
                bankId = request.bankId,
                nickname = request.nickname,
                kind = request.kind,
                network = request.network,
                formFactor = request.formFactor,
                last4 = request.last4,
                creditLimit = request.creditLimit,
                nowIso = Instant.now().toString(),
            ),
        )
    }

    /**
     * Only server-accepted profiles with a durable pending secret-deletion marker may trigger
     * vault cleanup. Never infer cleanup from an optimistic offline finance projection.
     */
    fun serverConfirmedPendingSecretCleanupIds(): List<String> {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return emptyList()
        if (!canUseServer() || currentSession?.assuranceLevel != AssuranceLevel.AAL2 ||
            ready.saving || ready.offline || ready.issue != null ||
            mutationLaunchInFlight || pendingMutations.isNotEmpty()
        ) return emptyList()
        val server = lastServerDocument ?: return emptyList()
        return confirmedPendingCardSecretCleanupIds(server)
    }

    /** Called ONLY once both owner+AAL2 server and legacy local vault deletion succeeded. */
    fun acknowledgeCompletedCardSecretCleanup(cardId: String) {
        val normalized = cardId.trim()
        if (normalized !in serverConfirmedPendingSecretCleanupIds()) return
        applyMutation(CompleteCanonicalCardSecretCleanup(normalized, Instant.now().toString()))
    }

    fun deleteCard(cardId: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val normalizedCardId = cardId.trim()
        if (normalizedCardId.isBlank() || ready.projection.document.canonicalCards().none { it.id == normalizedCardId && it.active }) {
            mutableNotices.tryEmit(
                UserNotice(
                    message = "Η κάρτα δεν είναι πλέον διαθέσιμη για αρχειοθέτηση.",
                    details = "Ενέργεια: Αρχειοθέτηση κάρτας\nΚατηγορία: INVALID_CARD_STATE",
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

    fun restoreCard(cardId: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val normalized = cardId.trim()
        if (normalized.isBlank() ||
            ready.projection.document.canonicalCards().none { it.id == normalized && !it.active }
        ) {
            mutableNotices.tryEmit(UserNotice(
                message = "Η αρχειοθετημένη κάρτα δεν είναι πλέον διαθέσιμη.",
                details = "Ενέργεια: Επαναφορά κάρτας\\nΚατηγορία: INVALID_CARD_STATE",
                diagnosticCode = "MFH-APP-INVALID_CARD_STATE",
            ))
            return
        }
        applyMutation(RestoreCanonicalCard(cardId = normalized, nowIso = Instant.now().toString()))
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

    /** Mutates the canonical recurring record through the guarded/offline replay pipeline. */
    /** Savings goals are server-authoritative metadata, never a transfer of funds. */
    fun saveSavingsGoal(goalId: String?, name: String, amountText: String, deadline: String?) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val amount = amountText.replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 } ?: return
        if (name.trim().isBlank() || name.length > 500) return
        val previous = ready.projection.moneyState.savingsGoalItems.firstOrNull { it.id == goalId }
        if (goalId != null && previous == null) return
        val asOf = LocalDate.now()
        val date = deadline?.trim()?.takeIf(String::isNotEmpty)
        if (date != null) {
            val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
            if (parsed.isBefore(asOf) && previous?.targetDate != date) return
        }
        applyMutation(UpsertCanonicalSavingsGoal(
            goalId = goalId ?: "goal-android-${UUID.randomUUID()}",
            name = name.trim(),
            targetAmount = amount,
            targetDate = date,
            asOf = asOf.toString(),
            nowIso = Instant.now().toString(),
        ))
    }

    fun deleteSavingsGoal(goalId: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        if (ready.projection.moneyState.savingsGoalItems.none { it.id == goalId }) return
        applyMutation(DeleteCanonicalSavingsGoal(goalId, Instant.now().toString()))
    }

    fun setRecurringStatus(recurringId: String, status: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        if (status !in setOf("active", "paused", "stopped")) return
        val recurring = ready.projection.document.allCanonicalRecurringItems()
            .firstOrNull { it.id == recurringId } ?: return
        if (recurring.effectiveStatus == status) return
        applyMutation(SetCanonicalRecurringStatus(recurringId, status, Instant.now().toString()))
    }

    fun saveOverallBudget(monthlyLimitText: String, alertThresholdText: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val amount = monthlyLimitText.replace(',', '.').toDoubleOrNull()
        val threshold = alertThresholdText.toIntOrNull()
        if (amount == null || amount <= 0.0 || threshold == null || threshold !in 50..99) return

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
        if (amount == null || amount <= 0.0 || threshold == null || threshold !in 50..99) return

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
                pendingMutations = pendingMutations.filterNot { it.intentId in eligibleIds }
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
                when (
                    mutationDeliveryMode(
                        serverAvailable = canUseServer(),
                        repositoryReady = repository.state.value is FinanceSyncState.Ready,
                        hasPendingMutations = pendingMutations.isNotEmpty(),
                    )
                ) {
                    MutationDeliveryMode.IMMEDIATE_SERVER -> commitOnlineMutation(
                        session = session,
                        mutation = mutation,
                        previousProjection = ready.projection,
                    )
                    MutationDeliveryMode.DURABLE_OFFLINE_QUEUE -> queueLocalMutation(
                        session = session,
                        mutation = mutation,
                        previousProjection = ready.projection,
                    )
                    MutationDeliveryMode.RECONCILE_PENDING_FIRST -> {
                        val queued = queueLocalMutation(
                            session = session,
                            mutation = mutation,
                            previousProjection = ready.projection,
                        )
                        if (queued && canUseServer()) {
                            synchronizePendingFromServer(
                                session = session,
                                previousProjection = (mutableState.value as? FinanceProductState.Ready)?.projection
                                    ?: ready.projection,
                                includeNeedsReview = false,
                            )
                        }
                    }
                }
            } finally {
                mutationLaunchInFlight = false
            }
        }
    }

    /**
     * Fast connected path: one canonical save using the current server revision. The temporary
     * NEEDS_REVIEW intent is crash-safety only; it is hidden during a healthy online request and
     * removed immediately on acknowledgement. No Undo grace timer or pre-save reload is involved.
     */
    private suspend fun commitOnlineMutation(
        session: AuthSession,
        mutation: CanonicalFinanceMutation,
        previousProjection: CanonicalProductProjection,
    ) {
        val serverDocument = lastServerDocument ?: previousProjection.document
        lastServerDocument = serverDocument
        val candidateDocument = runCatching { mutation.apply(serverDocument) }.getOrElse { error ->
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προετοιμασία online αλλαγής",
                    throwable = error,
                    message = "Η αλλαγή δεν μπόρεσε να εφαρμοστεί με ασφάλεια.",
                ),
            )
            return
        }

        val inFlightIntent = PendingCanonicalMutationIntent.fromMutation(
            mutation = mutation,
            intentId = "mutation-online-${UUID.randomUUID()}",
            syncState = PendingMutationSyncState.NEEDS_REVIEW,
        )
        pendingMutations = pendingMutations + inFlightIntent
        persistLocalSnapshot()

        val optimisticProjection = runCatching {
            projectCanonicalProduct(candidateDocument, LocalDate.now(), previousProjection)
        }.getOrElse { error ->
            pendingMutations = pendingMutations.filterNot { it.intentId == inFlightIntent.intentId }
            persistLocalSnapshot()
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προεπισκόπηση online αλλαγής",
                    throwable = error,
                    message = "Η αλλαγή δεν μπόρεσε να εμφανιστεί με ασφάλεια.",
                ),
            )
            return
        }
        mutableState.value = readyForProjection(
            projection = optimisticProjection,
            saving = true,
            offline = false,
        )

        repository.save(session, candidateDocument)
        when (val saved = repository.state.value) {
            is FinanceSyncState.Ready -> {
                pendingMutations = pendingMutations.filterNot { it.intentId == inFlightIntent.intentId }
                lastServerDocument = saved.envelope.document
                recordSuccessfulSync(saved.envelope.lastSavedAt)
                persistLocalSnapshot()

                var committedProjection = projectCanonicalProduct(
                    saved.envelope.document,
                    LocalDate.now(),
                    previousProjection,
                )
                if (mutation is AppendCanonicalEvent) {
                    committedProjection = committedProjection.copy(
                        quickEntryState = committedProjection.quickEntryState.copy(
                            persisted = true,
                            pendingSync = false,
                            dirty = false,
                            validationMessage = null,
                        ),
                    )
                }
                mutableState.value = readyForProjection(
                    projection = committedProjection,
                    saving = false,
                    offline = false,
                )
            }
            is FinanceSyncState.Conflict -> {
                persistLocalSnapshot()
                renderLocalState(
                    previous = previousProjection,
                    offline = false,
                    issue = FinanceSyncIssue(
                        FinanceSyncIssueKind.REVISION_CONFLICT,
                        "Η αλλαγή διατηρήθηκε με ασφάλεια. Φόρτωσε τα νεότερα δεδομένα πριν από ρητή επανάληψη.",
                    ),
                )
            }
            is FinanceSyncState.Error -> {
                persistLocalSnapshot()
                if (saved.failure.kind.isAuthRejection()) {
                    mutableNotices.emit(saved.failure.toUserNotice("Αποθήκευση αλλαγής"))
                    repository.clear()
                    mutableState.value = FinanceProductState.AuthRejected
                } else {
                    if (saved.failure.kind == ApiFailureKind.NETWORK) reloadWhenOnline = true
                    renderLocalState(
                        previous = previousProjection,
                        offline = saved.failure.kind == ApiFailureKind.NETWORK,
                        issue = FinanceSyncIssue(
                            if (saved.failure.kind == ApiFailureKind.NETWORK) {
                                FinanceSyncIssueKind.WAITING_FOR_NETWORK
                            } else {
                                FinanceSyncIssueKind.SAVE_FAILED
                            },
                            "Η αλλαγή διατηρήθηκε με ασφάλεια και θα συμφωνηθεί με την τρέχουσα κατάσταση του server πριν από οποιαδήποτε επανάληψη.",
                        ),
                    )
                    mutableNotices.emit(saved.failure.toUserNotice("Αποθήκευση αλλαγής"))
                }
            }
            else -> {
                persistLocalSnapshot()
                renderLocalState(
                    previous = previousProjection,
                    offline = false,
                    issue = FinanceSyncIssue(
                        FinanceSyncIssueKind.SAVE_FAILED,
                        "Η αλλαγή διατηρήθηκε με ασφάλεια και χρειάζεται συμφωνία με τον server.",
                    ),
                )
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
        pendingMutations = remaining
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
        val exposePending = offline || issue != null
        return FinanceProductState.Ready(
            projection = if (exposePending) markPendingTransactions(projection) else projection,
            saving = saving,
            issue = issue,
            offline = offline,
            pendingTransactionCount = if (exposePending) pendingTransactionIds().size else 0,
            pendingChangeCount = if (exposePending) pendingMutations.size else 0,
            latestPendingChange = if (exposePending) latestPendingChangeUi() else null,
            pendingReviewCount = if (exposePending) {
                pendingMutations.count { it.syncState == PendingMutationSyncState.NEEDS_REVIEW }
            } else {
                0
            },
        )
    }

    private fun readyForProjection(
        projection: CanonicalProductProjection,
        saving: Boolean = false,
        offline: Boolean = false,
        issue: FinanceSyncIssue? = null,
    ): FinanceProductState.Ready {
        val exposePending = offline || issue != null
        return FinanceProductState.Ready(
            projection = if (exposePending) markPendingTransactions(projection) else projection,
            saving = saving,
            issue = issue,
            offline = offline,
            pendingTransactionCount = if (exposePending) pendingTransactionIds().size else 0,
            pendingChangeCount = if (exposePending) pendingMutations.size else 0,
            latestPendingChange = if (exposePending) latestPendingChangeUi() else null,
            pendingReviewCount = if (exposePending) {
                pendingMutations.count { it.syncState == PendingMutationSyncState.NEEDS_REVIEW }
            } else {
                0
            },
        )
    }

    private fun applyAllPending(serverDocument: CanonicalFinanceDocument): CanonicalFinanceDocument =
        pendingMutations.fold(serverDocument) { document, pending -> pending.asMutation().apply(document) }

    private fun markPendingTransactions(projection: CanonicalProductProjection): CanonicalProductProjection =
        projectPendingUi(
            projection = projection,
            serverDocument = lastServerDocument,
            pending = pendingMutations,
            today = LocalDate.now(),
        )

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
                PendingMutationKind.SET_RECURRING_STATUS -> "Αλλαγή κατάστασης παγίου"
                PendingMutationKind.UPSERT_SAVINGS_GOAL -> "Αποθήκευση στόχου αποταμίευσης"
                PendingMutationKind.DELETE_SAVINGS_GOAL -> "Διαγραφή στόχου αποταμίευσης"
                PendingMutationKind.CREATE_CARD -> "Προσθήκη κάρτας"
                PendingMutationKind.DEACTIVATE_CARD -> "Αρχειοθέτηση κάρτας"
                PendingMutationKind.RESTORE_CARD -> "Επαναφορά κάρτας"
                PendingMutationKind.PERMANENTLY_DELETE_CARD -> "Οριστική διαγραφή κάρτας"
                PendingMutationKind.COMPLETE_CARD_SECRET_CLEANUP -> "Καθαρισμός ασφαλών στοιχείων κάρτας"
            },
            statusLabel = if (pending.syncState == PendingMutationSyncState.NEVER_SENT) {
                "Προς συγχρονισμό"
            } else {
                "Αναμονή επιβεβαίωσης από τον server"
            },
            canUndo = !canUseServer() && pending.syncState == PendingMutationSyncState.NEVER_SENT,
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
        providerCatalogJob?.cancel()
        providerCatalogJob = null
        mutableFinancialProviderCatalog.value = emptyList()
        deviceSessionsJob?.cancel()
        deviceSessionsJob = null
        mutableDeviceSessions.value = DeviceSessionsUiSnapshot()
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

    /**
     * Optional metadata transport is strictly bound to a single authenticated owner session.
     * A slow response for a prior user/token can never repopulate the next account's picker.
     */
    private fun refreshFinancialProviderCatalog(session: AuthSession) {
        providerCatalogJob?.cancel()
        mutableFinancialProviderCatalog.value = emptyList()
        if (!canUseServer() || session.assuranceLevel != AssuranceLevel.AAL2) return
        providerCatalogJob = viewModelScope.launch {
            val result = providerAwareApi.loadFinancialProviders(session)
            if (currentSession?.userId != session.userId ||
                currentSession?.accessToken != session.accessToken ||
                !automaticSyncAllowed) return@launch
            mutableFinancialProviderCatalog.value =
                (result as? ApiResult.Success<List<FinancialProviderRecord>>)?.value.orEmpty()
        }
    }

    private fun refreshDeviceSessions(session: AuthSession) {
        deviceSessionsJob?.cancel()
        mutableDeviceSessions.value = DeviceSessionsUiSnapshot()
        if (!canUseServer() || session.assuranceLevel != AssuranceLevel.AAL2) return
        deviceSessionsJob = viewModelScope.launch {
            val fetched = providerAwareApi.loadDeviceSessions(session)
            if (currentSession?.userId != session.userId ||
                currentSession?.accessToken != session.accessToken ||
                !automaticSyncAllowed) return@launch
            val records = (fetched as? ApiResult.Success<List<DeviceSessionRecord>>)?.value
            mutableDeviceSessions.value = if (records != null) {
                DeviceSessionsUiSnapshot(loaded = true, devices = records)
            } else DeviceSessionsUiSnapshot()
        }
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

}
