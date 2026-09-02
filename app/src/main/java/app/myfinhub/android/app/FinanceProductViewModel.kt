package app.myfinhub.android.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.data.AppendCanonicalEvent
import app.myfinhub.android.core.data.CanonicalEventDraft
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.CanonicalFinanceMutation
import app.myfinhub.android.core.data.DeactivateCanonicalCard
import app.myfinhub.android.core.data.EditCanonicalActivity
import app.myfinhub.android.core.data.FinanceRepository
import app.myfinhub.android.core.data.FinanceSyncState
import app.myfinhub.android.core.data.UpsertOverallBudget
import app.myfinhub.android.core.data.canonicalAccounts
import app.myfinhub.android.core.data.canonicalCards
import app.myfinhub.android.core.data.createCanonicalEventMutation
import app.myfinhub.android.core.data.equalExpenseSplit
import app.myfinhub.android.core.data.settingsObject
import app.myfinhub.android.core.data.string
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
import app.myfinhub.android.feature.quickentry.QuickEntryKind
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
 * Production-only product controller.
 *
 * The canonical server document remains the source of truth. UI drafts live only in memory; writes
 * are lossless JSON mutations saved through If-Match. A failed/conflicting write retains both the
 * local mutated projection and the replayable mutation intent until the user retries or discards it.
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
    private var pendingMutation: CanonicalFinanceMutation? = null
    private var pendingMutationNeverSent = false
    private var reloadWhenOnline = false
    private var mutationLaunchInFlight = false
    private var loadJob: Job? = null
    private var mutationJob: Job? = null

    init {
        viewModelScope.launch {
            networkStatus.drop(1).collect { status ->
                if (status != NetworkStatus.ONLINE) return@collect
                when {
                    pendingMutationNeverSent && pendingMutation != null && currentSession != null -> retryPendingMutation()
                    reloadWhenOnline && currentSession != null && mutableState.value is FinanceProductState.Failure -> {
                        reloadWhenOnline = false
                        retryLoad()
                    }
                }
            }
        }
    }

    fun attachSession(session: AuthSession) {
        val previous = currentSession
        if (previous != null && previous.userId != session.userId) {
            cancelOperations()
            pendingMutation = null
            pendingMutationNeverSent = false
            repository.clear()
        }
        currentSession = session
        if (previous?.userId == session.userId && mutableState.value is FinanceProductState.Ready) return
        loadFresh(preserveUi = false)
    }

    fun clear() {
        cancelOperations()
        currentSession = null
        pendingMutation = null
        pendingMutationNeverSent = false
        reloadWhenOnline = false
        mutableLastSuccessfulSync.value = null
        repository.clear()
        mutableState.value = FinanceProductState.Idle
    }

    fun retryLoad() {
        if (currentSession == null || loadJob?.isActive == true) return
        loadFresh(preserveUi = false)
    }

    fun retryPendingMutation() {
        val session = currentSession ?: return
        val mutation = pendingMutation ?: return
        if (mutationLaunchInFlight || mutationJob?.isActive == true) return
        if (connectivityObserver.current() == NetworkStatus.OFFLINE) {
            mutableNotices.tryEmit(offlineUserNotice("Επαναφόρτωση οικονομικών δεδομένων"))
            return
        }
        val previousProjection = (mutableState.value as? FinanceProductState.Ready)?.projection
        mutationLaunchInFlight = true
        mutationJob = viewModelScope.launch {
            try {
                mutableState.value = FinanceProductState.Loading
                repository.load(session)
                when (val loaded = repository.state.value) {
                    is FinanceSyncState.Ready -> {
                        recordSuccessfulSync(loaded.envelope.lastSavedAt)
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
        pendingMutationNeverSent = false
        reloadWhenOnline = false
        loadFresh(preserveUi = false)
    }

    fun onHomeAction(action: HomeAction) {
        updateReady { ready ->
            ready.copy(projection = ready.projection.copy(homeState = reduceHomeState(ready.projection.homeState, action)))
        }
    }

    fun onActivityAction(action: ActivityAction) {
        if (action is ActivityAction.SaveEdit) {
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
            return
        }
        updateReady { ready ->
            ready.copy(projection = ready.projection.copy(activityState = reduceActivity(ready.projection.activityState, action)))
        }
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
            mutableState.value = ready.copy(
                projection = ready.projection.copy(planState = reducePlan(ready.projection.planState, action)),
            )
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

    private fun loadFresh(preserveUi: Boolean) {
        val session = currentSession ?: return
        loadJob?.cancel()
        val previous = (mutableState.value as? FinanceProductState.Ready)?.projection.takeIf { preserveUi }
        if (connectivityObserver.current() == NetworkStatus.OFFLINE) {
            reloadWhenOnline = true
            mutableState.value = FinanceProductState.Failure(
                message = "Δεν υπάρχει σύνδεση. Θα γίνει νέα προσπάθεια όταν επανέλθει το δίκτυο.",
                retryable = true,
            )
            mutableNotices.tryEmit(offlineUserNotice("Φόρτωση οικονομικών δεδομένων"))
            return
        }
        reloadWhenOnline = false
        loadJob = viewModelScope.launch {
            mutableState.value = FinanceProductState.Loading
            repository.load(session)
            when (val loaded = repository.state.value) {
                is FinanceSyncState.Ready -> {
                    pendingMutation = null
                    pendingMutationNeverSent = false
                    recordSuccessfulSync(loaded.envelope.lastSavedAt)
                    val projected = runCatching {
                        projectCanonicalProduct(loaded.envelope.document, LocalDate.now(), previous)
                    }.getOrElse {
                        mutableNotices.emit(
                            unexpectedUserNotice(
                                operation = "Ανάγνωση οικονομικών δεδομένων",
                                throwable = it,
                                message = "Τα δεδομένα φορτώθηκαν αλλά δεν μπόρεσαν να εμφανιστούν σωστά.",
                            ),
                        )
                        mutableState.value = FinanceProductState.Failure(
                            "Τα οικονομικά δεδομένα έχουν μη αναμενόμενη μορφή.",
                            retryable = true,
                        )
                        return@launch
                    }
                    mutableState.value = FinanceProductState.Ready(projected)
                }
                is FinanceSyncState.Error -> handleLoadFailure(loaded.failure, "Φόρτωση οικονομικών δεδομένων")
                else -> failLoad("Δεν ήταν δυνατή η φόρτωση των οικονομικών δεδομένων.")
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

    private suspend fun saveMutation(
        session: AuthSession,
        mutation: CanonicalFinanceMutation,
        baseDocument: CanonicalFinanceDocument,
        previousProjection: CanonicalProductProjection?,
    ) {
        val localDocument = runCatching { mutation.apply(baseDocument) }.getOrElse {
            previousProjection?.let { projection ->
                mutableState.value = FinanceProductState.Ready(projection)
            }
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
            previousProjection?.let { projection -> mutableState.value = FinanceProductState.Ready(projection) }
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προεπισκόπηση αλλαγής",
                    throwable = it,
                    message = "Η αλλαγή ακυρώθηκε επειδή δεν μπόρεσε να εμφανιστεί με ασφάλεια.",
                ),
            )
            return
        }
        if (connectivityObserver.current() == NetworkStatus.OFFLINE) {
            pendingMutationNeverSent = true
            mutableState.value = FinanceProductState.Ready(
                projection = localProjection,
                issue = FinanceSyncIssue(
                    FinanceSyncIssueKind.WAITING_FOR_NETWORK,
                    "Η αλλαγή δεν στάλθηκε. Θα επαναληφθεί με ασφάλεια όταν επανέλθει το δίκτυο.",
                ),
            )
            mutableNotices.emit(offlineUserNotice("Αποθήκευση οικονομικών δεδομένων", pendingMutation = true))
            return
        }
        pendingMutationNeverSent = false
        mutableState.value = FinanceProductState.Ready(localProjection, saving = true)

        repository.save(session, localDocument)
        when (val saved = repository.state.value) {
            is FinanceSyncState.Ready -> {
                var projection = runCatching {
                    projectCanonicalProduct(saved.envelope.document, LocalDate.now(), localProjection)
                }.getOrElse {
                    mutableState.value = FinanceProductState.Ready(localProjection)
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
                pendingMutationNeverSent = false
                recordSuccessfulSync(saved.envelope.lastSavedAt)
                mutableState.value = FinanceProductState.Ready(projection)
                if (mutation is DeactivateCanonicalCard) {
                    mutableCommittedCardDeletions.emit(mutation.cardId)
                }
            }
            is FinanceSyncState.Conflict -> {
                pendingMutationNeverSent = false
                localProjection = runCatching {
                    projectCanonicalProduct(saved.localDocument, LocalDate.now(), localProjection)
                }.getOrElse { localProjection }
                val message = "Τα δεδομένα άλλαξαν σε άλλη συνεδρία. Η δική σου αλλαγή διατηρείται τοπικά μέχρι να επιλέξεις επανάληψη ή επαναφόρτωση."
                mutableState.value = FinanceProductState.Ready(
                    projection = localProjection,
                    issue = FinanceSyncIssue(FinanceSyncIssueKind.REVISION_CONFLICT, message),
                )
                mutableNotices.emit(
                    UserNotice(
                        message = "Υπάρχει νεότερη έκδοση των δεδομένων.",
                        details = "Ενέργεια: Αποθήκευση οικονομικών δεδομένων\nΚατηγορία: REVISION_CONFLICT\nΗ τοπική αλλαγή διατηρείται μέχρι να επιλέξεις επανάληψη ή επαναφόρτωση.",
                        diagnosticCode = "MFH-API-REVISION_CONFLICT-409",
                    ),
                )
            }
            is FinanceSyncState.Error -> {
                pendingMutationNeverSent = false
                if (saved.failure.kind.isAuthRejection()) {
                    mutableNotices.emit(saved.failure.toUserNotice("Αποθήκευση οικονομικών δεδομένων"))
                    repository.clear()
                    mutableState.value = FinanceProductState.AuthRejected
                } else {
                    val message = apiFailureMessage(saved.failure.kind)
                    mutableState.value = FinanceProductState.Ready(
                        projection = localProjection,
                        issue = FinanceSyncIssue(FinanceSyncIssueKind.SAVE_FAILED, message),
                    )
                    mutableNotices.emit(saved.failure.toUserNotice("Αποθήκευση οικονομικών δεδομένων"))
                }
            }
            else -> {
                val message = "Η αποθήκευση δεν ολοκληρώθηκε."
                mutableState.value = FinanceProductState.Ready(
                    localProjection,
                    issue = FinanceSyncIssue(FinanceSyncIssueKind.SAVE_FAILED, message),
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

    private suspend fun handleLoadFailure(failure: ApiResult.Failure, operation: String) {
        if (failure.kind == ApiFailureKind.NETWORK) reloadWhenOnline = true
        mutableNotices.emit(failure.toUserNotice(operation))
        if (failure.kind.isAuthRejection()) {
            repository.clear()
            mutableState.value = FinanceProductState.AuthRejected
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

    private fun ApiFailureKind.isAuthRejection(): Boolean =
        this == ApiFailureKind.AUTH_REQUIRED || this == ApiFailureKind.MFA_REQUIRED
}
