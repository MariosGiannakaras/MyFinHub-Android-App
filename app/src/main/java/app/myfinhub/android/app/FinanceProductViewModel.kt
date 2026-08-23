package app.myfinhub.android.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.data.AppendCanonicalEvent
import app.myfinhub.android.core.data.CanonicalEventDraft
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
import app.myfinhub.android.core.network.ApiFailureKind
import app.myfinhub.android.core.network.OkHttpMyFinHubApi
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

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

enum class FinanceSyncIssueKind { REVISION_CONFLICT, SAVE_FAILED }

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
    private val repository = FinanceRepository(
        OkHttpMyFinHubApi(
            configuration = AppConfiguration.fromBuildConfig(),
            client = OkHttpClient.Builder().build(),
        ),
    )

    private val mutableState = MutableStateFlow<FinanceProductState>(FinanceProductState.Idle)
    val state: StateFlow<FinanceProductState> = mutableState.asStateFlow()

    /** Emits only after a card deactivation is accepted by the canonical server revision. */
    private val mutableCommittedCardDeletions = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val committedCardDeletions: SharedFlow<String> = mutableCommittedCardDeletions.asSharedFlow()

    private var currentSession: AuthSession? = null
    private var pendingMutation: CanonicalFinanceMutation? = null

    fun attachSession(session: AuthSession) {
        val previous = currentSession
        currentSession = session
        if (previous?.userId == session.userId && mutableState.value is FinanceProductState.Ready) return
        loadFresh(preserveUi = false)
    }

    fun clear() {
        currentSession = null
        pendingMutation = null
        repository.clear()
        mutableState.value = FinanceProductState.Idle
    }

    fun retryLoad() {
        if (currentSession == null) return
        loadFresh(preserveUi = false)
    }

    fun retryPendingMutation() {
        val session = currentSession ?: return
        val mutation = pendingMutation ?: return
        val previousProjection = (mutableState.value as? FinanceProductState.Ready)?.projection
        viewModelScope.launch {
            mutableState.value = FinanceProductState.Loading
            repository.load(session)
            when (val loaded = repository.state.value) {
                is FinanceSyncState.Ready -> saveMutation(
                    session = session,
                    mutation = mutation,
                    baseDocument = loaded.envelope.document,
                    previousProjection = previousProjection,
                )
                is FinanceSyncState.Error -> handleLoadFailure(loaded.failure.kind, loaded.failure.retryable)
                else -> mutableState.value = FinanceProductState.Failure("Δεν ήταν δυνατή η επαναφόρτωση των δεδομένων.", true)
            }
        }
    }

    fun discardPendingAndReload() {
        pendingMutation = null
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
            if (ready.saving || ready.issue != null) return
            val mutation = EditCanonicalActivity(
                transactionId = action.id,
                note = action.note,
                category = action.category,
                nowIso = Instant.now().toString(),
            )
            applyMutation(mutation)
            return
        }
        updateReady { ready ->
            ready.copy(projection = ready.projection.copy(activityState = reduceActivity(ready.projection.activityState, action)))
        }
    }

    fun deleteCard(cardId: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null) return
        if (ready.projection.document.canonicalCards().none { it.id == cardId && it.active }) return
        applyMutation(
            DeactivateCanonicalCard(
                cardId = cardId,
                nowIso = Instant.now().toString(),
            ),
        )
    }

    fun onQuickEntryAction(action: QuickEntryAction) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null) return
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
        if (preview.validationMessage != null || preview.amount == null) return

        val document = ready.projection.document
        val accounts = document.canonicalAccounts().filter { it.kind != "credit" }
        val defaultId = document.settingsObject().string("defaultExpenseAccount")
        val fromId = accounts.firstOrNull { it.id == defaultId }?.id
            ?: accounts.firstOrNull { it.name.equals(preview.fromAccount, ignoreCase = true) }?.id
            ?: accounts.firstOrNull()?.id
        if (fromId == null) {
            setQuickEntryError("Δεν υπάρχει διαθέσιμος λογαριασμός για την κίνηση.")
            return
        }
        val eventId = "evt-android-${UUID.randomUUID()}"
        val now = Instant.now().toString()
        val amount = preview.amount!!

        val draft = when (preview.kind) {
            QuickEntryKind.EXPENSE -> CanonicalEventDraft(
                kind = "expense",
                date = LocalDate.now().toString(),
                amount = amount,
                note = preview.note,
                category = preview.category,
                accountId = fromId,
            )
            QuickEntryKind.TRANSFER -> {
                val destination = accounts.firstOrNull {
                    it.id.equals(preview.destination.trim(), ignoreCase = true) ||
                        it.name.equals(preview.destination.trim(), ignoreCase = true)
                }
                if (destination == null) {
                    setQuickEntryError("Ο λογαριασμός προορισμού δεν βρέθηκε.")
                    return
                }
                CanonicalEventDraft(
                    kind = "transfer",
                    date = LocalDate.now().toString(),
                    amount = amount,
                    note = preview.note,
                    fromAccountId = fromId,
                    toAccountId = destination.id,
                )
            }
            QuickEntryKind.CARD_PAYMENT -> {
                val card = document.canonicalCards().firstOrNull { it.active && it.kind == "credit" }
                if (card == null) {
                    setQuickEntryError("Δεν υπάρχει ενεργή πιστωτική κάρτα για πληρωμή.")
                    return
                }
                CanonicalEventDraft(
                    kind = "card_payment",
                    date = LocalDate.now().toString(),
                    amount = amount,
                    note = preview.note,
                    category = preview.category,
                    fromAccountId = fromId,
                    cardId = card.id,
                )
            }
            QuickEntryKind.SPLIT -> CanonicalEventDraft(
                kind = "split",
                date = LocalDate.now().toString(),
                amount = amount,
                note = preview.note,
                accountId = fromId,
                parts = equalExpenseSplit(
                    total = amount,
                    parts = preview.splitPeople,
                    category = preview.category,
                    idPrefix = "$eventId-part",
                ),
            )
        }

        val mutation = runCatching {
            createCanonicalEventMutation(document, draft, eventId, now)
        }.getOrElse { error ->
            setQuickEntryError(error.message ?: "Η κίνηση δεν είναι έγκυρη.")
            return
        }
        applyMutation(mutation)
    }

    fun onPlanAction(action: PlanAction) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null) return
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
        val previous = (mutableState.value as? FinanceProductState.Ready)?.projection.takeIf { preserveUi }
        viewModelScope.launch {
            mutableState.value = FinanceProductState.Loading
            repository.load(session)
            when (val loaded = repository.state.value) {
                is FinanceSyncState.Ready -> {
                    pendingMutation = null
                    mutableState.value = FinanceProductState.Ready(
                        projectCanonicalProduct(loaded.envelope.document, LocalDate.now(), previous),
                    )
                }
                is FinanceSyncState.Error -> handleLoadFailure(loaded.failure.kind, loaded.failure.retryable)
                else -> mutableState.value = FinanceProductState.Failure("Δεν ήταν δυνατή η φόρτωση των οικονομικών δεδομένων.", true)
            }
        }
    }

    private fun applyMutation(mutation: CanonicalFinanceMutation) {
        val session = currentSession ?: return
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null) return
        viewModelScope.launch {
            saveMutation(
                session = session,
                mutation = mutation,
                baseDocument = ready.projection.document,
                previousProjection = ready.projection,
            )
        }
    }

    private suspend fun saveMutation(
        session: AuthSession,
        mutation: CanonicalFinanceMutation,
        baseDocument: app.myfinhub.android.core.data.CanonicalFinanceDocument,
        previousProjection: CanonicalProductProjection?,
    ) {
        val localDocument = runCatching { mutation.apply(baseDocument) }.getOrElse { error ->
            mutableState.value = FinanceProductState.Failure(error.message ?: "Η αλλαγή δεν είναι έγκυρη.", false)
            return
        }
        pendingMutation = mutation
        var localProjection = projectCanonicalProduct(localDocument, LocalDate.now(), previousProjection)
        mutableState.value = FinanceProductState.Ready(localProjection, saving = true)

        repository.save(session, localDocument)
        when (val saved = repository.state.value) {
            is FinanceSyncState.Ready -> {
                var projection = projectCanonicalProduct(saved.envelope.document, LocalDate.now(), localProjection)
                projection = when (mutation) {
                    is AppendCanonicalEvent -> projection.copy(
                        quickEntryState = projection.quickEntryState.copy(persisted = true),
                    )
                    is UpsertOverallBudget -> projection.copy(
                        planState = projection.planState.copy(message = "Το budget αποθηκεύτηκε στο canonical state."),
                    )
                    else -> projection
                }
                pendingMutation = null
                mutableState.value = FinanceProductState.Ready(projection)
                if (mutation is DeactivateCanonicalCard) {
                    mutableCommittedCardDeletions.emit(mutation.cardId)
                }
            }
            is FinanceSyncState.Conflict -> {
                localProjection = projectCanonicalProduct(saved.localDocument, LocalDate.now(), localProjection)
                mutableState.value = FinanceProductState.Ready(
                    projection = localProjection,
                    issue = FinanceSyncIssue(
                        FinanceSyncIssueKind.REVISION_CONFLICT,
                        "Τα δεδομένα άλλαξαν σε άλλη συνεδρία. Η δική σου αλλαγή διατηρείται τοπικά μέχρι να επιλέξεις επανάληψη ή επαναφόρτωση.",
                    ),
                )
            }
            is FinanceSyncState.Error -> {
                if (saved.failure.kind.isAuthRejection()) {
                    repository.clear()
                    mutableState.value = FinanceProductState.AuthRejected
                } else {
                    mutableState.value = FinanceProductState.Ready(
                        projection = localProjection,
                        issue = FinanceSyncIssue(
                            FinanceSyncIssueKind.SAVE_FAILED,
                            failureMessage(saved.failure.kind),
                        ),
                    )
                }
            }
            else -> mutableState.value = FinanceProductState.Ready(
                localProjection,
                issue = FinanceSyncIssue(FinanceSyncIssueKind.SAVE_FAILED, "Η αποθήκευση δεν ολοκληρώθηκε."),
            )
        }
    }

    private fun handleLoadFailure(kind: ApiFailureKind, retryable: Boolean) {
        if (kind.isAuthRejection()) {
            repository.clear()
            mutableState.value = FinanceProductState.AuthRejected
        } else {
            mutableState.value = FinanceProductState.Failure(failureMessage(kind), retryable)
        }
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

    private fun updateReady(transform: (FinanceProductState.Ready) -> FinanceProductState.Ready) {
        val current = mutableState.value as? FinanceProductState.Ready ?: return
        if (current.saving || current.issue != null) return
        mutableState.value = transform(current)
    }

    private fun ApiFailureKind.isAuthRejection(): Boolean =
        this == ApiFailureKind.AUTH_REQUIRED || this == ApiFailureKind.MFA_REQUIRED

    private fun failureMessage(kind: ApiFailureKind): String = when (kind) {
        ApiFailureKind.BUILD_NOT_CONFIGURED -> "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration."
        ApiFailureKind.AUTH_REQUIRED -> "Η συνεδρία δεν είναι πλέον έγκυρη."
        ApiFailureKind.MFA_REQUIRED -> "Η συνεδρία δεν έχει πλέον AAL2 πρόσβαση."
        ApiFailureKind.REVISION_CONFLICT -> "Τα δεδομένα άλλαξαν σε άλλη συνεδρία."
        ApiFailureKind.PRECONDITION_REQUIRED -> "Λείπει έγκυρη revision προϋπόθεση για την αποθήκευση."
        ApiFailureKind.INVALID_DATA -> "Τα δεδομένα της αλλαγής δεν έγιναν δεκτά."
        ApiFailureKind.RATE_LIMITED -> "Πολλά αιτήματα. Δοκίμασε ξανά αργότερα."
        ApiFailureKind.NETWORK -> "Δεν υπάρχει σύνδεση με το MyFinHub. Η τοπική αλλαγή διατηρείται."
        ApiFailureKind.SERVER -> "Το MyFinHub δεν είναι προσωρινά διαθέσιμο. Η τοπική αλλαγή διατηρείται."
        ApiFailureKind.MALFORMED_RESPONSE -> "Το MyFinHub επέστρεψε μη αναμενόμενη απάντηση."
        ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE -> "Η λειτουργία δεν είναι διαθέσιμη στο test host."
    }
}
