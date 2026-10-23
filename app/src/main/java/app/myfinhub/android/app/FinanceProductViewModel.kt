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
import app.myfinhub.android.core.data.EditCanonicalCardProfile
import app.myfinhub.android.core.data.UpsertCanonicalCounterparty
import app.myfinhub.android.feature.utilities.CanonicalCounterpartyEditRequest
import app.myfinhub.android.core.data.SetCanonicalCreditBillingSettings
import app.myfinhub.android.core.data.AddCanonicalCreditRateVersion
import app.myfinhub.android.feature.money.CreditBillingSettingsRequest
import app.myfinhub.android.feature.money.CreditRateVersionRequest
import app.myfinhub.android.feature.money.CardProfileEditRequest
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
import app.myfinhub.android.core.data.UpsertCategoryBudget
import app.myfinhub.android.core.data.DeleteCategoryBudget
import app.myfinhub.android.core.data.SetCanonicalSavingsTargetRate
import app.myfinhub.android.core.data.SetCanonicalTransactionRuleEnabled
import app.myfinhub.android.core.data.UpsertCanonicalTransactionRule
import app.myfinhub.android.core.data.UpsertCanonicalLoanMetadata
import app.myfinhub.android.feature.money.LoanMetadataEditRequest
import app.myfinhub.android.core.data.DeleteCanonicalTransactionRule
import app.myfinhub.android.core.data.ReorderCanonicalTransactionRule
import app.myfinhub.android.core.data.obj
import app.myfinhub.android.feature.utilities.TransactionRuleEditRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import app.myfinhub.android.core.data.bool
import app.myfinhub.android.core.data.webCategoryBudgetStableId
import app.myfinhub.android.core.data.webOverallBudgetStableId
import app.myfinhub.android.core.data.settingsObject
import app.myfinhub.android.core.data.number
import app.myfinhub.android.core.data.array
import app.myfinhub.android.core.data.string
import app.myfinhub.android.feature.plan.CategoryBudgetEditRequest
import kotlinx.serialization.json.JsonObject
import app.myfinhub.android.core.data.SetCanonicalRecurringStatus
import app.myfinhub.android.core.data.CreateCanonicalRecurring
import app.myfinhub.android.core.data.EditCanonicalRecurring
import app.myfinhub.android.core.data.canonicalRecurringEditSource
import app.myfinhub.android.core.data.CanonicalRecurringCreateDraft
import app.myfinhub.android.feature.plan.RecurringCreateRequest
import app.myfinhub.android.core.data.CompleteCanonicalScheduledTransaction
import app.myfinhub.android.core.data.CreateCanonicalScheduledTransaction
import app.myfinhub.android.core.data.CanonicalScheduledDraft
import app.myfinhub.android.feature.plan.ScheduledCreateRequest
import app.myfinhub.android.core.data.CloseCanonicalScheduledTransaction
import app.myfinhub.android.core.data.canonicalScheduled
import app.myfinhub.android.core.data.canonicalAccounts

import app.myfinhub.android.core.data.createCanonicalLoanPaymentMutation
import app.myfinhub.android.core.data.UpsertCanonicalSavingsGoal
import app.myfinhub.android.core.data.DeleteCanonicalSavingsGoal
import app.myfinhub.android.core.data.allCanonicalRecurringItems
import app.myfinhub.android.core.data.canonicalCards
import app.myfinhub.android.core.network.AndroidConnectivityObserver
import app.myfinhub.android.core.network.ApiFailureKind
import app.myfinhub.android.core.network.ApiResult
import app.myfinhub.android.core.network.FinancialProviderRecord
import app.myfinhub.android.core.network.RecurringServiceAssetRecord
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
import app.myfinhub.android.feature.activity.selectOverviewMonth
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
    val revokingSessionId: String? = null,
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
    private val mutableRecurringServiceAssets = MutableStateFlow<List<RecurringServiceAssetRecord>>(emptyList())
    private var recurringServiceAssetsLoaded = false
    /** Owner+AAL2 session-only service artwork metadata, never saved in FinanceData. */
    val recurringServiceAssets: StateFlow<List<RecurringServiceAssetRecord>> =
        mutableRecurringServiceAssets.asStateFlow()
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
    private var recurringServiceAssetsJob: Job? = null
    private var deviceSessionsJob: Job? = null
    private var deviceRevokeJob: Job? = null

    init {
        viewModelScope.launch {
            networkStatus.drop(1).collect { status ->
                if (status != NetworkStatus.ONLINE || !automaticSyncAllowed || currentSession == null) return@collect
                val ready = mutableState.value as? FinanceProductState.Ready
                if (mutableFinancialProviderCatalog.value.isEmpty()) {
                    currentSession?.let(::refreshFinancialProviderCatalog)
                }
                if (!recurringServiceAssetsLoaded)
                    currentSession?.let(::refreshRecurringServiceAssets)
                if (!mutableDeviceSessions.value.loaded) currentSession?.let(::refreshDeviceSessions)
                if (reloadWhenOnline || ready?.offline == true || pendingMutations.any { it.syncState == PendingMutationSyncState.NEVER_SENT }) {
                    loadFresh(preserveUi = true)
                }
            }
        }
    }

    fun attachSession(session: AuthSession, allowAutomaticSync: Boolean = true) {
        val previous = currentSession
        // A refreshed bearer for the SAME owner is still a new authorization
        // boundary. An old in-flight GET/POST must never satisfy or block the
        // new token's load. Stable queued writes are already stored locally;
        // an interrupted network write remains NEEDS_REVIEW until reconciled.
        if (financeSessionAuthorizationChanged(previous, session)) {
            cancelOperations()
            repository.clear()
            reloadWhenOnline = true
        }
        if (previous != null && previous.userId != session.userId) {
            lastServerDocument = null
            pendingMutations = emptyList()
            repository.clear()
        }
        if (previous?.userId != session.userId || previous.accessToken != session.accessToken) {
            deviceRevokeJob?.cancel()
            deviceRevokeJob = null
            mutableDeviceSessions.value = DeviceSessionsUiSnapshot()
        }
        currentSession = session
        automaticSyncAllowed = allowAutomaticSync
        if (previous?.userId != session.userId || previous.accessToken != session.accessToken ||
            mutableFinancialProviderCatalog.value.isEmpty()) {
            refreshFinancialProviderCatalog(session)
        }
        if (previous?.userId != session.userId || previous.accessToken != session.accessToken ||
            !recurringServiceAssetsLoaded) refreshRecurringServiceAssets(session)
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
        recurringServiceAssetsJob?.cancel()
        recurringServiceAssetsJob = null
        mutableRecurringServiceAssets.value = emptyList()
        recurringServiceAssetsLoaded = false
        deviceSessionsJob?.cancel()
        deviceSessionsJob = null
        deviceRevokeJob?.cancel()
        deviceRevokeJob = null
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

    /**
     * Guard the durable canonical mutation boundary, not just the Compose row
     * or preview reducer. Duplicated import IDs can otherwise edit/delete more
     * than one original history entry through a deep link or stale back stack.
     */
    private fun canMutateActivity(ready: FinanceProductState.Ready, id: String): Boolean {
        if (ready.projection.activityState.isUniqueMutationTarget(id)) return true
        mutableNotices.tryEmit(
            UserNotice(
                message = "Η κίνηση δεν μπορεί να τροποποιηθεί με ασφάλεια.",
                details = "Το αναγνωριστικό δεν αντιστοιχεί σε ακριβώς μία διαθέσιμη κίνηση.",
                diagnosticCode = "MFH-APP-AMBIGUOUS-ACTIVITY-ID",
            ),
        )
        return false
    }

    fun onActivityAction(action: ActivityAction) {
        when (action) {
            is ActivityAction.SaveEdit -> {
                val ready = mutableState.value as? FinanceProductState.Ready ?: return
                if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
                if (!canMutateActivity(ready, action.id)) return
                applyMutation(
                    EditCanonicalActivity(
                        transactionId = action.id,
                        note = action.note,
                        category = action.category,
                        nowIso = Instant.now().toString(),
                        date = action.date,
                        subcategory = action.subcategory,
                        legacyType = action.legacy?.type,
                        legacyAmount = action.legacy?.amount,
                        legacyAccountId = action.legacy?.accountId,
                        legacyFromAccountId = action.legacy?.fromAccountId,
                        legacyToAccountId = action.legacy?.toAccountId,
                    ),
                )
            }
            is ActivityAction.Delete -> deleteTransaction(action.id)
            is ActivityAction.NavigateOverviewMonth -> {
                if (action.delta !in setOf(-1, 1)) return
                updateReady { ready ->
                    val state = ready.projection.activityState
                    if (state.isAnalyticsScope) return@updateReady ready
                    val target = runCatching {
                        YearMonth.parse(state.monthOverview?.month ?: "")
                            .plusMonths(action.delta.toLong())
                    }.getOrNull() ?: return@updateReady ready
                    val overview = projectCanonicalActivityMonthOverview(ready.projection.document, target)
                    ready.copy(projection = ready.projection.copy(
                        activityState = state.selectOverviewMonth(overview),
                    ))
                }
            }
            else -> updateReady { ready ->
                ready.copy(projection = ready.projection.copy(activityState = reduceActivity(ready.projection.activityState, action)))
            }
        }
    }

    private fun deleteTransaction(transactionId: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val id = transactionId.trim()
        if (!canMutateActivity(ready, id)) return
        applyMutation(DeleteCanonicalActivity(id, Instant.now().toString()))
    }

    /** Future purchase cycle configuration: already stored statement IDs are immutable. */
    fun saveCreditBillingSettings(request: CreditBillingSettingsRequest) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        if (request.closingDay !in 1..31 || request.dueDay !in 1..31 ||
            request.boundaryRule !in setOf("next-cycle", "include-closing-day")) return
        val original = ready.projection.document.state.array("cards")
            .mapNotNull { it as? JsonObject }
            .filter { it.string("id") == request.cardId }
            .singleOrNull() ?: return
        if (original.string("kind") != "credit" || original.bool("active") == false) return
        if (original.number("statementClosingDay") == request.closingDay.toDouble() &&
            original.number("statementDueDay") == request.dueDay.toDouble() &&
            original.string("statementBoundaryRule") == request.boundaryRule) return
        applyMutation(SetCanonicalCreditBillingSettings(
            cardId = request.cardId,
            closingDay = request.closingDay,
            dueDay = request.dueDay,
            boundaryRule = request.boundaryRule,
            expected = original,
            nowIso = Instant.now().toString(),
        ))
    }

    /** Record only a dated bank-disclosed nominal rate; no modeled interest becomes a transaction. */
    fun addCreditRateVersion(request: CreditRateVersionRequest) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val effective = runCatching { LocalDate.parse(request.effectiveFrom) }.getOrNull()
            ?: return
        val earliest = LocalDate.now()
        val scaled = request.annualPercent * 10_000.0
        if (effective.isBefore(earliest) || !request.annualPercent.isFinite() ||
            request.annualPercent !in 0.0..100.0 ||
            kotlin.math.abs(kotlin.math.round(scaled) - scaled) >= 0.00001) return
        val original = ready.projection.document.state.array("cards")
            .mapNotNull { it as? JsonObject }
            .filter { it.string("id") == request.cardId }
            .singleOrNull() ?: return
        if (original.string("kind") != "credit" || original.bool("active") == false) return
        if (original.array("creditRateHistory")
            .filterIsInstance<JsonObject>()
            .any { it.string("effectiveFrom") == request.effectiveFrom }) return
        applyMutation(AddCanonicalCreditRateVersion(
            cardId = request.cardId,
            annualPercent = request.annualPercent,
            effectiveFrom = request.effectiveFrom,
            earliestAllowedDate = earliest.toString(),
            expected = original,
            nowIso = Instant.now().toString(),
        ))
    }

    /** Canonical card-profile editing. Does not modify the encrypted secret vault. */
    fun saveCardProfile(request: CardProfileEditRequest) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val rows = ready.projection.document.state.array("cards")
            .mapNotNull { it as? JsonObject }
            .filter { it.string("id") == request.cardId }
        val expected = rows.singleOrNull() ?: return
        if (expected.bool("active") == false) return
        if (request.nickname.trim().isEmpty() ||
            request.nickname.trim().length > 500 || request.bankId.isBlank() ||
            request.bankId.length > 100 ||
            request.network !in setOf("visa", "mastercard", "other") ||
            request.formFactor !in setOf("physical", "virtual")) return
        val kind = expected.string("kind")
        if (kind !in setOf("debit", "prepaid", "credit")) return
        if (request.creditLimit != null &&
            (kind != "credit" || !request.creditLimit.isFinite() ||
                request.creditLimit <= 0.0)) return
        // Keep source-row and kind identity; only the permitted profile fields change.
        applyMutation(EditCanonicalCardProfile(
            cardId = request.cardId,
            bankId = request.bankId,
            nickname = request.nickname,
            network = request.network,
            formFactor = request.formFactor,
            creditLimit = request.creditLimit,
            expected = expected,
            nowIso = Instant.now().toString(),
        ))
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

    /**
     * Ordinary-loan metadata only: never creates a payment, funding event,
     * transfer or silently rewrites historical installment totals.
     */
    fun saveLoanMetadata(request: LoanMetadataEditRequest) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val document = ready.projection.document
        if (request.name.trim().isEmpty() || request.name.length > 500 ||
            request.kind !in setOf("loan", "installment") ||
            request.installments !in 1..1200 ||
            !request.total.isFinite() || request.total <= 0.0 ||
            !request.installment.isFinite() || request.installment <= 0.0 ||
            request.defaultAccountId.isBlank() ||
            document.canonicalAccounts().count {
                it.id == request.defaultAccountId && it.kind != "credit"
            } != 1) return
        if (Regex("\\bHELP\\b|ΒΟΗΘΕΙΑ", RegexOption.IGNORE_CASE)
                .containsMatchIn(request.name + " " + request.provider)) return
        fun validMoney(value: Double): Boolean {
            val scaled = value * 100.0
            return scaled.isFinite() && kotlin.math.abs(kotlin.math.round(scaled) -
                scaled) < 0.00001
        }
        if (!validMoney(request.total) || !validMoney(request.installment) ||
            (request.firstExpectedDate.isNotBlank() &&
                runCatching { LocalDate.parse(request.firstExpectedDate) }.isFailure)) return

        val taxonomy = ready.projection.planState.budgetCategories
        val seeded = document.seed.array("loans").filterIsInstance<JsonObject>()
        val custom = document.state.array("customLoans").filterIsInstance<JsonObject>()
        val id = request.id ?: "loan-android-${UUID.randomUUID()}"
        if (id.isBlank()) return
        val seedMatches = seeded.filter { it.string("id") == id }
        val customMatches = custom.filter { it.string("id") == id }
        if (seedMatches.size + customMatches.size > 1) return
        val source = if (seedMatches.isNotEmpty()) "seed" else "custom"
        val existing = when (source) {
            "seed" -> {
                (document.state.obj("loanOverrides")[id] as? JsonObject)
                    ?: seedMatches.singleOrNull()
            }
            else -> customMatches.singleOrNull()
        }
        if (request.id != null && existing == null) return
        if (request.id == null && existing != null) return
        if (existing != null) {
            if (existing.string("kind") != request.kind ||
                existing.string("source") == "self-loan") return
        }
        val expectedCategory = existing?.string("expenseCategory")
        if (request.expenseCategory.isBlank() ||
            (taxonomy.isNotEmpty() && request.expenseCategory !in taxonomy &&
                request.expenseCategory != expectedCategory)) return

        val now = Instant.now().toString()
        val metadata = mapOf(
            "id" to JsonPrimitive(id),
            "name" to JsonPrimitive(request.name.trim()),
            "provider" to JsonPrimitive(request.provider.trim()),
            "kind" to JsonPrimitive(request.kind),
            "total" to JsonPrimitive(request.total),
            "installment" to JsonPrimitive(request.installment),
            "installments" to JsonPrimitive(request.installments),
            "firstExpectedDate" to JsonPrimitive(request.firstExpectedDate),
            "defaultAccountId" to JsonPrimitive(request.defaultAccountId),
            "expenseCategory" to JsonPrimitive(request.expenseCategory),
            "updatedAt" to JsonPrimitive(now),
        )
        val row = if (existing != null) {
            JsonObject(existing + metadata)
        } else {
            JsonObject(mapOf(
                "source" to JsonPrimitive("user"),
                "paidCount" to JsonPrimitive(0),
                "forgivenAmount" to JsonPrimitive(0),
                "accountingMode" to JsonPrimitive("expense-per-installment"),
                "day" to JsonPrimitive(""),
                "schedule" to JsonArray(emptyList()),
                "longTermRecurring" to JsonPrimitive(
                    request.kind == "loan" && request.longTermRecurring
                ),
                "createdAt" to JsonPrimitive(now),
            ) + metadata)
        }
        applyMutation(UpsertCanonicalLoanMetadata(
            source = source,
            loan = row,
            expected = existing,
            nowIso = now,
        ))
    }

    /** User-confirmed loan payment uses the existing strict canonical mutation and offline replay. */
    fun recordLoanPayment(
        loanId: String, accountId: String, date: String, installments: Int,
        selfLoanAmount: Double?, savingsAccountId: String?,
    ) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val mutation = runCatching {
            createCanonicalLoanPaymentMutation(
                document = ready.projection.document,
                loanId = loanId, date = date, accountId = accountId,
                eventId = "evt-android-${UUID.randomUUID()}",
                nowIso = Instant.now().toString(),
                requestedInstallments = installments,
                selfLoanAmount = selfLoanAmount,
                savingsAccountId = savingsAccountId,
            )
        }.getOrElse {
            mutableNotices.tryEmit(unexpectedUserNotice(
                operation = "Καταχώριση πληρωμής δανείου",
                throwable = it,
                message = "Η πληρωμή δεν μπόρεσε να προετοιμαστεί.",
            ))
            return
        }
        applyMutation(mutation)
    }

    /** Saving a future schedule does not yet create a ledger event. */
    fun createScheduled(request: ScheduledCreateRequest) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        applyMutation(CreateCanonicalScheduledTransaction(
            scheduledId = "sch-android-${UUID.randomUUID()}",
            nowIso = Instant.now().toString(),
            draft = CanonicalScheduledDraft(
                kind = request.kind, dueDate = request.dueDate,
                amount = request.amount, note = request.note,
                accountId = request.accountId,
                fromAccountId = request.fromAccountId,
                toAccountId = request.toAccountId,
            ),
        ))
    }

    /** A user-confirmed schedule closes the same canonical event it creates. */
    fun completeScheduled(request: app.myfinhub.android.feature.plan.ScheduledCompletionRequest) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val scheduled = ready.projection.document.canonicalScheduled()
            .filter { it.id == request.id }.singleOrNull()
            ?.takeIf { it.status == "pending" } ?: return
        val valid = app.myfinhub.android.feature.plan.scheduledCompletionAccountIsValid(
            scheduled.kind,ready.projection.document.canonicalAccounts()
                .filter { it.kind != "credit" }.mapTo(mutableSetOf()) { it.id },
            request.accountId,request.fromAccountId,request.toAccountId,
        )
        if (!valid) return
        applyMutation(CompleteCanonicalScheduledTransaction(
            scheduledId = request.id,
            eventId = "evt-android-${UUID.randomUUID()}",
            date = request.date,
            nowIso = Instant.now().toString(),
            amount = request.amount,
            accountId = request.accountId,
            fromAccountId = request.fromAccountId,
            toAccountId = request.toAccountId,
        ))
    }

    /** Skipping/cancelling never writes money and uses durable offline replay. */
    fun closeScheduled(id: String, status: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight ||
            status !in setOf("skipped","cancelled")) return
        if (ready.projection.document.canonicalScheduled().filter { it.id == id }
                .singleOrNull()?.status != "pending") return
        applyMutation(CloseCanonicalScheduledTransaction(
            scheduledId = id, status = status, nowIso = Instant.now().toString(),
        ))
    }

    /** Only an explicit Save queues this canonical plan metadata; never book a payment. */
    fun createRecurring(request: RecurringCreateRequest) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        applyMutation(CreateCanonicalRecurring(
            recurringId = "rec-android-${UUID.randomUUID()}",
            nowIso = Instant.now().toString(),
            draft = CanonicalRecurringCreateDraft(
                name = request.name, amount = request.amount,
                accountId = request.accountId, category = request.category,
                amountMode = request.amountMode,
                estimateMin = request.estimateMin, estimateMax = request.estimateMax,
                recurrenceUnit = request.recurrenceUnit,
                recurrenceInterval = request.recurrenceInterval,
                day = request.day,
                firstExpectedDate = request.firstExpectedDate,
                endDate = request.endDate,
            ),
        ))
    }

    /** Guarded edit: compare the original canonical row at every offline/revision replay. */
    fun editRecurring(recurringId: String, request: RecurringCreateRequest) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val source = ready.projection.document.canonicalRecurringEditSource(recurringId) ?: return
        applyMutation(EditCanonicalRecurring(
            recurringId = recurringId,
            expected = source.row,
            nowIso = Instant.now().toString(),
            draft = CanonicalRecurringCreateDraft(
                name = request.name, amount = request.amount,
                accountId = request.accountId, category = request.category,
                amountMode = request.amountMode,
                estimateMin = request.estimateMin, estimateMax = request.estimateMax,
                recurrenceUnit = request.recurrenceUnit,
                recurrenceInterval = request.recurrenceInterval,
                day = request.day, firstExpectedDate = request.firstExpectedDate,
                endDate = request.endDate,
            ),
        ))
    }

    fun setRecurringStatus(recurringId: String, status: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        if (status !in setOf("active", "paused", "stopped")) return
        val recurring = ready.projection.document.allCanonicalRecurringItems()
            .filter { it.id == recurringId }.singleOrNull() ?: return
        if (recurring.effectiveStatus == status) return
        applyMutation(SetCanonicalRecurringStatus(recurringId, status, Instant.now().toString()))
    }

    /** Web counterparty identity directory: financial events and past notes stay unchanged. */
    fun saveCounterparty(request: CanonicalCounterpartyEditRequest) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val rows = ready.projection.document.state.array("counterparties")
            .mapNotNull { it as? JsonObject }
        val matches = rows.filter { it.string("id") == request.id }
        if (request.id != null && matches.size != 1) return
        if (request.id == null && rows.size >= 2000) return
        val original = matches.singleOrNull()
        val kind = original?.string("kind") ?: request.kind
        val now = Instant.now().toString()
        val id = original?.string("id") ?: "cp-${UUID.randomUUID()}"
        if (kind !in setOf("merchant", "beneficiary") ||
            (original != null && request.kind != kind)) return
        applyMutation(UpsertCanonicalCounterparty(
            counterpartyId = id,
            kind = kind,
            displayName = request.displayName,
            aliases = request.aliases,
            createdAt = original?.string("createdAt") ?: now,
            expected = original,
            nowIso = now,
        ))
    }

    /** Web-shaped rule authoring through the only guarded finance mutation pipeline. */
    fun saveTransactionRule(request: TransactionRuleEditRequest) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val document = ready.projection.document
        val rows = document.state.array("transactionRules").mapNotNull { it as? JsonObject }
        val matches = rows.filter { it.string("id") == request.id }
        if (request.id != null && matches.size != 1) return
        if (request.id == null && rows.size >= 10_000) return
        val existing = matches.singleOrNull()
        val name = request.name.trim()
        val description = request.matchDescription.trim()
        val merchant = request.matchMerchant.trim()
        val account = request.matchAccountId.trim()
        val category = request.actionCategory.trim()
        val subcategory = request.actionSubcategory.trim()
        val note = request.actionNote.trim()
        val scopeOrder = listOf("manual", "imported", "review")
        val scopes = scopeOrder.filter { it in request.scopes }
        if (name.isEmpty() || name.length > 500 ||
            listOf(description, merchant, account).all(String::isEmpty) ||
            listOf(category, subcategory, note).all(String::isEmpty) ||
            scopes.isEmpty() || request.scopes.any { it !in scopeOrder } ||
            request.matchMode !in setOf("contains", "equals")
        ) return
        // Existing missing taxonomy references remain editable as Web historical exceptions.
        val allowedCategories = ready.projection.planState.budgetCategories
        if (category.isNotEmpty() && allowedCategories.isNotEmpty() &&
            category !in allowedCategories && category != existing?.obj("action")?.string("category")) return
        if (subcategory.isNotEmpty() && category.isEmpty()) return
        if (subcategory.isNotEmpty() &&
            subcategory !in ready.projection.planState.ruleSubcategories[category].orEmpty() &&
            !(subcategory == existing?.obj("action")?.string("subcategory") &&
              category == existing?.obj("action")?.string("category"))
        ) return
        val allowedAccounts = document.canonicalAccounts().filter { it.kind != "credit" }.map { it.id }
        if (account.isNotEmpty() && account !in allowedAccounts &&
            account != existing?.obj("match")?.string("accountId")) return

        val now = Instant.now().toString()
        val id = request.id ?: "rule-android-${UUID.randomUUID()}"
        val priority = existing?.number("priority")
            ?: ((rows.maxOfOrNull { it.number("priority") ?: 0.0 } ?: 0.0) + 100.0)
                .coerceIn(0.0, 1_000_000.0)
        fun withFields(original: JsonObject, pairs: Map<String, String?>): JsonObject {
            val values = original.toMutableMap()
            pairs.forEach { (key, value) ->
                if (value.isNullOrBlank()) values.remove(key) else values[key] = JsonPrimitive(value)
            }
            return JsonObject(values)
        }
        val match = withFields(existing?.obj("match") ?: JsonObject(emptyMap()), mapOf(
            "description" to description, "merchant" to merchant,
            "accountId" to account, "mode" to request.matchMode,
        ))
        val action = withFields(existing?.obj("action") ?: JsonObject(emptyMap()), mapOf(
            "category" to category, "subcategory" to subcategory, "note" to note,
        ))
        val rule = JsonObject((existing ?: JsonObject(emptyMap())) + mapOf(
            "id" to JsonPrimitive(id),
            "name" to JsonPrimitive(name),
            "enabled" to JsonPrimitive(existing?.bool("enabled") ?: true),
            "priority" to JsonPrimitive(priority.toInt()),
            "scopes" to JsonArray(scopes.map(::JsonPrimitive)),
            "match" to match,
            "action" to action,
            "createdAt" to JsonPrimitive(existing?.string("createdAt") ?: now),
            "updatedAt" to JsonPrimitive(now),
        ))
        applyMutation(UpsertCanonicalTransactionRule(rule, existing, now))
    }

    fun deleteTransactionRule(ruleId: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight || ruleId.isBlank()) return
        val row = ready.projection.document.state.array("transactionRules")
            .mapNotNull { it as? JsonObject }.filter { it.string("id") == ruleId }
            .singleOrNull() ?: return
        applyMutation(DeleteCanonicalTransactionRule(ruleId, row, Instant.now().toString()))
    }

    fun reorderTransactionRule(ruleId: String, direction: Int) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight ||
            ruleId.isBlank() || direction !in setOf(-1, 1)) return
        val rows = ready.projection.document.state.array("transactionRules")
        val ids = rows.mapNotNull { (it as? JsonObject)?.string("id") }
        if (ids.size != rows.size || ids.distinct().size != ids.size ||
            ruleId !in ids || rows.size < 2) return
        applyMutation(ReorderCanonicalTransactionRule(
            ruleId = ruleId, direction = direction,
            expected = JsonArray(rows), nowIso = Instant.now().toString(),
        ))
    }

    /** Applies only to future transactions; source fields and past events are preserved. */
    fun setTransactionRuleEnabled(ruleId: String, enabled: Boolean) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight || ruleId.isBlank()) return
        val matches = ready.projection.document.state.array("transactionRules")
            .mapNotNull { it as? JsonObject }.filter { it.string("id") == ruleId }
        val existing = matches.singleOrNull() ?: return
        if (existing.bool("enabled") == enabled) return
        applyMutation(SetCanonicalTransactionRuleEnabled(
            ruleId = ruleId, enabled = enabled, expected = existing,
            nowIso = Instant.now().toString(),
        ))
    }

    /** Web savingsTargetRate is canonical settings metadata, not a balance or transfer. */
    fun saveMonthlySavingsRate(percentText: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val value = percentText.replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return
        val rate = value / 100.0
        val existing = ready.projection.document.settingsObject().number("savingsTargetRate")
        if (existing == rate) return
        applyMutation(SetCanonicalSavingsTargetRate(
            rate = rate,
            expectedRate = existing,
            nowIso = Instant.now().toString(),
        ))
    }

    /** Delete only the explicitly selected source row; historical finance events remain intact. */
    fun deleteCategoryBudget(budgetId: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight || budgetId.isBlank()) return
        val month = YearMonth.now().toString()
        val candidates = ready.projection.document.state.array("budgets")
            .mapNotNull { it as? JsonObject }.filter { it.string("id") == budgetId }
        val current = candidates.singleOrNull() ?: return
        if (current.string("scope") != "category" || current.string("month") != month) return
        val category = current.string("category")?.takeIf { it.isNotBlank() } ?: return
        applyMutation(DeleteCategoryBudget(
            budgetId = budgetId,
            month = month,
            category = category,
            expected = current,
            nowIso = Instant.now().toString(),
        ))
    }

    fun saveCategoryBudget(request: CategoryBudgetEditRequest) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val category = request.category.trim()
        val amount = request.monthlyLimitText.replace(',', '.').toDoubleOrNull()
        val threshold = request.alertThresholdText.toIntOrNull()
        if (category.isEmpty() || amount == null || !amount.isFinite() ||
            amount <= 0.0 || threshold == null || threshold !in 50..99) return
        val month = YearMonth.now().toString()
        val matches = ready.projection.document.state.array("budgets")
            .mapNotNull { it as? JsonObject }
            .filter {
                it.string("month") == month && it.string("scope") == "category" &&
                    it.string("category") == category
            }
        if (matches.size > 1) return
        val current = matches.singleOrNull()
        // New category budgets use the configured Web taxonomy, never a free-form
        // Android-only synthetic category. Existing historical rows retain their identity.
        if (request.budgetId == null &&
            ready.projection.planState.budgetCategories.isNotEmpty() &&
            category !in ready.projection.planState.budgetCategories
        ) return
        // A new category must not silently overwrite an existing category.
        if (request.budgetId == null && current != null) return
        if (request.budgetId != null && current?.string("id") != request.budgetId) return
        applyMutation(UpsertCategoryBudget(
            month = month, category = category, amount = amount,
            alertThreshold = threshold,
            budgetId = request.budgetId ?: webCategoryBudgetStableId(month, category),
            expected = current,
            nowIso = Instant.now().toString(),
        ))
    }

    fun saveOverallBudget(monthlyLimitText: String, alertThresholdText: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val amount = monthlyLimitText.replace(',', '.').toDoubleOrNull()
        val threshold = alertThresholdText.toIntOrNull()
        if (amount == null || amount <= 0.0 || threshold == null || threshold !in 50..99) return

        val month = YearMonth.now().toString()
        val rows = ready.projection.document.state.array("budgets")
            .mapNotNull { it as? JsonObject }
            .filter { it.string("month") == month && it.string("scope") == "overall" }
        if (rows.size > 1) return
        applyMutation(
            UpsertOverallBudget(
                month = month,
                amount = amount,
                alertThreshold = threshold,
                budgetId = webOverallBudgetStableId(month),
                nowIso = Instant.now().toString(),
                expected = rows.singleOrNull(),
                enforceExpected = true,
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
                PendingMutationKind.UPSERT_CATEGORY_BUDGET -> "Όριο κατηγορίας"
                PendingMutationKind.DELETE_CATEGORY_BUDGET -> "Διαγραφή ορίου κατηγορίας"
                PendingMutationKind.SET_SAVINGS_TARGET_RATE -> "Αλλαγή στόχου αποταμίευσης"
                PendingMutationKind.SET_TRANSACTION_RULE_ENABLED -> "Κατάσταση αυτοματισμού"
                PendingMutationKind.UPSERT_TRANSACTION_RULE -> "Αποθήκευση αυτοματισμού"
                PendingMutationKind.UPSERT_COUNTERPARTY -> "Αποθήκευση αντισυμβαλλομένου"
                PendingMutationKind.DELETE_TRANSACTION_RULE -> "Διαγραφή αυτοματισμού"
                PendingMutationKind.REORDER_TRANSACTION_RULE -> "Σειρά αυτοματισμών"
                PendingMutationKind.SET_RECURRING_STATUS -> "Αλλαγή κατάστασης παγίου"
                PendingMutationKind.CREATE_RECURRING -> "Προσθήκη παγίου"
                PendingMutationKind.EDIT_RECURRING -> "Επεξεργασία παγίου"
                PendingMutationKind.CREATE_SCHEDULED -> "Προγραμματισμός κίνησης"
                PendingMutationKind.COMPLETE_SCHEDULED -> "Ολοκλήρωση προγραμματισμένης κίνησης"
                PendingMutationKind.CLOSE_SCHEDULED -> "Παράλειψη ή ακύρωση προγραμματισμένης κίνησης"
                PendingMutationKind.UPSERT_LOAN_METADATA -> "Στοιχεία δανείου"
                PendingMutationKind.UPSERT_SAVINGS_GOAL -> "Αποθήκευση στόχου αποταμίευσης"
                PendingMutationKind.DELETE_SAVINGS_GOAL -> "Διαγραφή στόχου αποταμίευσης"
                PendingMutationKind.CREATE_CARD -> "Προσθήκη κάρτας"
                PendingMutationKind.EDIT_CARD_PROFILE -> "Αλλαγή προφίλ κάρτας"
                PendingMutationKind.SET_CREDIT_BILLING_SETTINGS -> "Ρυθμίσεις κύκλου πιστωτικής"
                PendingMutationKind.ADD_CREDIT_RATE_VERSION -> "Νέο επιτόκιο πιστωτικής"
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
        recurringServiceAssetsJob?.cancel()
        recurringServiceAssetsJob = null
        mutableRecurringServiceAssets.value = emptyList()
        recurringServiceAssetsLoaded = false
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

    /** Never accept a delayed metadata response from a previous bearer or owner session. */
    private fun refreshRecurringServiceAssets(session: AuthSession) {
        recurringServiceAssetsJob?.cancel()
        mutableRecurringServiceAssets.value = emptyList()
        recurringServiceAssetsLoaded = false
        if (!canUseServer() || session.assuranceLevel != AssuranceLevel.AAL2) return
        recurringServiceAssetsJob = viewModelScope.launch {
            val result = providerAwareApi.loadRecurringServiceAssets(session)
            if (currentSession?.userId != session.userId ||
                currentSession?.accessToken != session.accessToken ||
                !automaticSyncAllowed) return@launch
            recurringServiceAssetsLoaded = result is ApiResult.Success<*>
            mutableRecurringServiceAssets.value =
                (result as? ApiResult.Success<List<RecurringServiceAssetRecord>>)?.value.orEmpty()
        }
    }

    /** Never queues destructive auth actions offline or silently retries an uncertain POST. */
    fun revokeConfirmedOtherDeviceSession(targetSessionId: String) {
        val session = currentSession ?: return
        val snapshot = mutableDeviceSessions.value
        if (!canUseServer() || session.assuranceLevel != AssuranceLevel.AAL2 ||
            !snapshot.loaded || snapshot.revokingSessionId != null ||
            deviceRevokeJob?.isActive == true) return
        val other = snapshot.devices.singleOrNull {
            it.sessionId == targetSessionId && !it.current
        } ?: return
        deviceSessionsJob?.cancel()
        deviceSessionsJob = null
        mutableDeviceSessions.value = snapshot.copy(revokingSessionId = other.sessionId)
        deviceRevokeJob = viewModelScope.launch {
            val result = providerAwareApi.revokeOtherDeviceSession(session, other.sessionId)
            if (currentSession?.userId != session.userId ||
                currentSession?.accessToken != session.accessToken ||
                !automaticSyncAllowed) return@launch
            when (result) {
                is ApiResult.Success -> {
                    if (result.value.any { it.sessionId == other.sessionId }) {
                        mutableDeviceSessions.value = DeviceSessionsUiSnapshot(
                            loaded = true, devices = result.value,
                        )
                        mutableNotices.tryEmit(UserNotice(
                            message = "Ο server δεν επιβεβαίωσε την αποσύνδεση της άλλης συσκευής.",
                            details = "Ενέργεια: Αποσύνδεση άλλης συσκευής\nΚατάσταση: SERVER_UNCONFIRMED",
                        diagnosticCode = "MFH-DEVICE-REVOKE-UNCONFIRMED",
                        ))
                        return@launch
                    }
                    mutableDeviceSessions.value = DeviceSessionsUiSnapshot(
                        loaded = true, devices = result.value,
                    )
                    mutableNotices.tryEmit(UserNotice(
                        message = "Η άλλη συσκευή αποσυνδέθηκε.",
                        details = "Ενέργεια: Αποσύνδεση άλλης συσκευής\nΚατάσταση: SERVER_CONFIRMED",
                        diagnosticCode = "MFH-DEVICE-REVOKED",
                    ))
                }
                is ApiResult.Failure -> {
                    // Never automatically retry the POST; GET safely reconciles an ambiguous result.
                    val reconciliation = providerAwareApi.loadDeviceSessions(session)
                    if (currentSession?.userId != session.userId ||
                        currentSession?.accessToken != session.accessToken ||
                        !automaticSyncAllowed) return@launch
                    val records = (reconciliation as? ApiResult.Success<List<DeviceSessionRecord>>)?.value
                    mutableDeviceSessions.value = if (records != null) {
                        DeviceSessionsUiSnapshot(loaded = true, devices = records)
                    } else DeviceSessionsUiSnapshot()
                    mutableNotices.tryEmit(UserNotice(
                        message = "Η αποσύνδεση δεν επιβεβαιώθηκε. Έλεγξε ξανά τις συσκευές.",
                        details = "Ενέργεια: Αποσύνδεση άλλης συσκευής\nΚατάσταση: RECONCILIATION_REQUIRED",
                        diagnosticCode = "MFH-DEVICE-REVOKE-REVIEW",
                    ))
                }
            }
        }
    }

    private fun refreshDeviceSessions(session: AuthSession) {
        deviceSessionsJob?.cancel()
        mutableDeviceSessions.value = DeviceSessionsUiSnapshot()
        if (!canUseServer() || session.assuranceLevel != AssuranceLevel.AAL2 ||
            deviceRevokeJob?.isActive == true) return
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
