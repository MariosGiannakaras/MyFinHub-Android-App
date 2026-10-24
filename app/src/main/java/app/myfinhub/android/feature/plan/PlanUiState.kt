package app.myfinhub.android.feature.plan

import androidx.lifecycle.ViewModel
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlannedItem(
    val id: String,
    val title: String,
    val dueLabel: String,
    val amount: Double,
    val kind: PlannedKind,
    val flow: PlannedFlow = PlannedFlow.OBLIGATION,
    val category: String = "",
    val accountLabel: String = "",
    val note: String = "",
    val paused: Boolean = false,
    val dueDateIso: String = "",
    val urgency: PlannedUrgency = PlannedUrgency.LATER,
)

enum class PlannedKind { RECURRING, SCHEDULED }

enum class PlannedFlow { OBLIGATION, INCOME, TRANSFER }

enum class PlannedUrgency { OVERDUE, THIS_WEEK, LATER }

data class RecurringManageItem(
    val id: String,
    val title: String,
    val amount: Double,
    val status: String,
    val nextDueDateIso: String = "",
    val category: String = "",
    val accountId: String = "",
    val cadenceLabel: String = "",
    // Preserve Web service branding metadata without inventing/embedding logos.
    val logoAssetKey: String? = null,
    val source: String? = null,
    val endDateIso: String? = null,
    val paymentCount: Int = 0,
    val lastPaymentDateIso: String? = null,
    val typicalPaymentDay: Int? = null,
    val amountMode: String = "fixed",
    val estimateMin: Double? = null,
    val estimateMax: Double? = null,
    /** Real linked expenses through the as-of date; not planned/estimated money. */
    val lastActualPaymentDateIso: String? = null,
    val lastActualPaymentAmount: Double? = null,
    val actualPaidThisMonth: Double? = null,
    val actualPaymentsThisMonth: Int = 0,
    val actualPaymentHistory: List<RecurringActualPaymentRow> = emptyList(),
    // Web editable cadence fields, distinct from historical typical payment day.
    val recurrenceUnit: String = "month",
    val recurrenceInterval: Int = 1,
    val day: Int? = null,
    val firstExpectedDateIso: String? = null,
    val subcategory: String? = null,
)

/** Web actual payments: no additional persisted finance data. */
data class RecurringActualPaymentRow(
    val eventId: String,
    val dateIso: String,
    val amount: Double,
)

data class ScheduledManageItem(
    val id: String,
    val title: String,
    val kind: String,
    val dueDateIso: String,
    val amount: Double,
    val status: String,
    val accountId: String? = null,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
)

data class ScheduledAccountOption(val id: String, val label: String)
/** A plan only: no money is booked until a separate explicit expense action. */
data class RecurringCreateRequest(
    val name: String,
    val amount: Double,
    val accountId: String,
    val category: String,
    val amountMode: String = "fixed",
    val estimateMin: Double? = null,
    val estimateMax: Double? = null,
    val recurrenceUnit: String = "month",
    val recurrenceInterval: Int = 1,
    val day: Int? = null,
    val firstExpectedDate: String? = null,
    val endDate: String? = null,
    /** Append to preserve positional constructors emitted by earlier Android UI code. */
    val subcategory: String? = null,
)

/** A confirmed scheduled payment selects the actual money route, not a stale scheduled source. */
data class ScheduledCompletionRequest(
    val id: String,
    val date: String,
    val amount: Double,
    val accountId: String? = null,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
)

internal fun scheduledCompletionAccountIsValid(
    kind: String,
    availableIds: Set<String>,
    accountId: String?,
    fromAccountId: String?,
    toAccountId: String?,
): Boolean = if (kind == "transfer") {
    fromAccountId in availableIds && toAccountId in availableIds && fromAccountId != toAccountId
} else {
    accountId in availableIds
}

data class ScheduledCreateRequest(
    val kind: String,
    val dueDate: String,
    val amount: Double,
    val note: String,
    val accountId: String?,
    val fromAccountId: String?,
    val toAccountId: String?,
)

data class BudgetDraft(
    val monthlyLimitText: String = "",
    val alertThresholdText: String = "80",
)

data class CategoryBudget(
    val id: String,
    val name: String,
    val monthlyLimitText: String,
    val alertThresholdText: String = "80",
    val spent: Double,
    val enabled: Boolean = true,
    /** Web budgetProgress: refunds may produce negative raw net, but used is floored at zero. */
    val rawSpent: Double = spent,
    val remaining: Double = 0.0,
    val status: String = "ok",
    val effectiveLimit: Double? = null,
    val carried: Double = 0.0,
    val carryover: Boolean = false,
    val projectedDue: Double = 0.0,
    val projectedRemaining: Double = remaining,
    val projectedStatus: String = status,
    val category: String? = null,
    val subcategory: String? = null,
)

data class PlanningRule(
    val id: String,
    val title: String,
    val description: String,
    val enabled: Boolean,
    val warning: String? = null,
    val historicalMatchCount: Int = 0,
    val priority: Double = 0.0,
    val matchDescription: String = "",
    val matchMerchant: String = "",
    val matchAccountId: String = "",
    val matchMode: String = "contains",
    val actionCategory: String = "",
    val actionSubcategory: String = "",
    val actionNote: String = "",
    val scopes: List<String> = listOf("manual"),
)

/** Web ForecastPoint includes each day's account balances as well as portfolio. */
data class ForecastPortfolioPoint(
    val dateIso: String,
    val portfolio: Double,
    val balancesByAccount: Map<String, Double> = emptyMap(),
)
data class ForecastAccountBreakdown(
    val accountId: String, val accountLabel: String,
    val current: Double, val projected: Double, val minimum: Double,
    val firstLowDateIso: String?, val firstNegativeDateIso: String?,
)
data class ForecastAccountImpact(
    val accountId: String, val accountLabel: String, val delta: Double,
)
data class ForecastKnownMovement(
    val id: String, val dateIso: String, val label: String,
    val source: String, val portfolioDelta: Double,
    /** Preserve each Web ForecastMovement leg, including zero-net transfers. */
    val accountImpacts: List<ForecastAccountImpact> = emptyList(),
)

/** Exact read-only Web cashFlowForecast trace, shared across 30/60/90 horizons. */
data class ForecastWindow(
    val days: Int,
    val label: String,
    val expectedIncome: Double,
    val expectedOutflow: Double,
    val balanceDeltaFromThirtyDays: Double,
    val projectedPortfolio: Double = 0.0,
    val minimumPortfolio: Double = 0.0,
    val accountsWithLowBalance: Int = 0,
    val accountsWithNegativeBalance: Int = 0,
    val omittedSourceCount: Int = 0,
    val omittedWarnings: List<String> = emptyList(),
    val sourceCounts: Map<String, Int> = emptyMap(),
    val currentPortfolio: Double = 0.0,
    val minimumPortfolioDateIso: String = "",
    val insufficientData: Boolean = false,
    val portfolioTimeline: List<ForecastPortfolioPoint> = emptyList(),
    val accountBreakdown: List<ForecastAccountBreakdown> = emptyList(),
    val knownMovements: List<ForecastKnownMovement> = emptyList(),
)

/** Production-facing plan state. Synthetic planning data must be supplied explicitly by previews. */
data class PlanUiState(
    val items: List<PlannedItem> = emptyList(),
    val recurringManageItems: List<RecurringManageItem> = emptyList(),
    val scheduledManageItems: List<ScheduledManageItem> = emptyList(),
    val scheduledAccounts: List<ScheduledAccountOption> = emptyList(),
    val budget: BudgetDraft = BudgetDraft(),
    val overallBudgetId: String? = null,
    val categoryBudgets: List<CategoryBudget> = emptyList(),
    val subcategoryBudgets: List<CategoryBudget> = emptyList(),
    val budgetCategories: List<String> = emptyList(),
    val ruleSubcategories: Map<String, List<String>> = emptyMap(),
    val expenseTaxonomy: List<app.myfinhub.android.core.data.CanonicalCategoryTaxonomyRow> = emptyList(),
    val incomeTaxonomy: List<app.myfinhub.android.core.data.CanonicalCategoryTaxonomyRow> = emptyList(),
    val categoryIconColors: Map<String, String> = emptyMap(),
    val categoryIconPack: String = "lucide",
    /** Selected per-pack semantic keys for the currently active pack and valid taxonomy. */
    val categoryGlyphOverrides: Map<String, String> = emptyMap(),
    val subcategoryIconColors: Map<String, String> = emptyMap(),
    val rules: List<PlanningRule> = emptyList(),
    /** Source events only; never seeded legacy transactions or synthetic preview fixtures. */
    val rulePreviewEvents: List<JsonObject> = emptyList(),
    val counterpartyDirectory: List<app.myfinhub.android.core.data.CanonicalCounterpartyRow> = emptyList(),
    val managedAccounts: List<app.myfinhub.android.core.data.CanonicalAccount> = emptyList(),
    val deletableCustomCashAccountIds: Set<String> = emptySet(),
    val defaultAccountIds: Map<String, String> = emptyMap(),
    val forecastWindows: List<ForecastWindow> = emptyList(),
    val forecastHorizonDays: Int = 30,
    val forecastStartDateIso: String = "",
    val forecastEndDateIso: String = "",
    val forecastStartBalance: Double = 0.0,
    val forecastExpectedIncome: Double = 0.0,
    val forecastObligations: Double = 0.0,
    val forecastTransferImpact: Double = 0.0,
    val forecastEndBalance: Double = 0.0,
    val forecastEndDateLabel: String = "",
    val budgetSpent: Double = 0.0,
    val budgetProjectedDue: Double = 0.0,
    val budgetProjectedRemaining: Double = 0.0,
    val budgetProjectedStatus: String = "ok",
    /** Known future scheduled/recurring expenses, not finance ledger entries. */
    val budgetKnownCommitments: List<app.myfinhub.android.core.data.CanonicalBudgetCommitment> = emptyList(),
    val budgetMonthLabel: String = "",
    /** Independent reporting month: YYYY-MM, preserved across finance reprojections. */
    val budgetSelectedMonthIso: String = "",
    val message: String? = null,
    val itemMessage: String? = null,
    val categoryBudgetMessage: String? = null,
)

sealed interface PlanAction {
    data class MonthlyLimitChanged(val value: String) : PlanAction
    data class AlertThresholdChanged(val value: String) : PlanAction
    data object SaveBudget : PlanAction
    data class ShiftBudgetMonth(val delta: Int) : PlanAction
    data class SelectBudgetMonth(val monthIso: String) : PlanAction

    data class UpdatePlannedItem(
        val id: String,
        val title: String,
        val dueLabel: String,
        val amount: Double,
        val kind: PlannedKind,
        val category: String,
        val accountLabel: String,
        val note: String,
    ) : PlanAction

    data class TogglePlannedItemPause(val id: String) : PlanAction
    data class CategoryBudgetLimitChanged(val id: String, val value: String) : PlanAction
    data class CategoryBudgetThresholdChanged(val id: String, val value: String) : PlanAction
    data class ToggleCategoryBudget(val id: String) : PlanAction
    data object SaveCategoryBudgets : PlanAction
    data class ToggleRule(val id: String) : PlanAction
    data class ForecastHorizonChanged(val days: Int) : PlanAction
}

fun reducePlan(state: PlanUiState, action: PlanAction): PlanUiState = when (action) {
    is PlanAction.SelectBudgetMonth -> {
        val next = runCatching { java.time.YearMonth.parse(action.monthIso) }.getOrNull()
        if (next == null || next.toString() != action.monthIso) state
        else state.copy(budgetSelectedMonthIso = next.toString())
    }
    is PlanAction.ShiftBudgetMonth -> {
        val month = runCatching { java.time.YearMonth.parse(state.budgetSelectedMonthIso) }
            .getOrNull() ?: java.time.YearMonth.now()
        if (action.delta !in setOf(-1, 1)) state
        else state.copy(budgetSelectedMonthIso = month.plusMonths(action.delta.toLong()).toString())
    }
    is PlanAction.MonthlyLimitChanged -> state.copy(
        budget = state.budget.copy(monthlyLimitText = action.value),
        message = null,
    )

    is PlanAction.AlertThresholdChanged -> state.copy(
        budget = state.budget.copy(alertThresholdText = action.value),
        message = null,
    )

    PlanAction.SaveBudget -> {
        val limit = state.budget.monthlyLimitText.replace(',', '.').toDoubleOrNull()
        val threshold = state.budget.alertThresholdText.toIntOrNull()
        when {
            limit == null || limit <= 0 -> state.copy(message = "Το μηνιαίο όριο πρέπει να είναι μεγαλύτερο από μηδέν.")
            threshold == null || threshold !in 50..99 -> state.copy(message = "Το όριο ειδοποίησης πρέπει να είναι από 50 έως 99%.")
            else -> state.copy(message = "Ο μηνιαίος προϋπολογισμός ενημερώθηκε.")
        }
    }

    is PlanAction.UpdatePlannedItem -> state.copy(
        items = state.items.map { item ->
            if (item.id != action.id) {
                item
            } else {
                item.copy(
                    title = action.title.trim(),
                    dueLabel = action.dueLabel.trim(),
                    amount = action.amount,
                    kind = action.kind,
                    category = action.category.trim(),
                    accountLabel = action.accountLabel.trim(),
                    note = action.note.trim(),
                )
            }
        },
        itemMessage = "Το τοπικό προσχέδιο της προγραμματισμένης κίνησης ενημερώθηκε. Δεν έχει συγχρονιστεί.",
    )

    is PlanAction.TogglePlannedItemPause -> state.copy(
        items = state.items.map { item ->
            if (item.id == action.id) item.copy(paused = !item.paused) else item
        },
        itemMessage = "Η τοπική κατάσταση της προγραμματισμένης κίνησης ενημερώθηκε. Δεν έχει συγχρονιστεί.",
    )

    is PlanAction.CategoryBudgetLimitChanged -> state.copy(
        categoryBudgets = state.categoryBudgets.map { budget ->
            if (budget.id == action.id) budget.copy(monthlyLimitText = action.value) else budget
        },
        categoryBudgetMessage = null,
    )

    is PlanAction.CategoryBudgetThresholdChanged -> state.copy(
        categoryBudgets = state.categoryBudgets.map { budget ->
            if (budget.id == action.id) budget.copy(alertThresholdText = action.value) else budget
        },
        categoryBudgetMessage = null,
    )

    is PlanAction.ToggleCategoryBudget -> state.copy(
        categoryBudgets = state.categoryBudgets.map { budget ->
            if (budget.id == action.id) budget.copy(enabled = !budget.enabled) else budget
        },
        categoryBudgetMessage = null,
    )

    PlanAction.SaveCategoryBudgets -> {
        val invalid = state.categoryBudgets.firstOrNull { budget ->
            if (!budget.enabled) return@firstOrNull false
            val limit = budget.monthlyLimitText.replace(',', '.').toDoubleOrNull()
            val threshold = budget.alertThresholdText.toIntOrNull()
            limit == null || limit <= 0.0 || threshold == null || threshold !in 50..99
        }
        if (invalid != null) {
            state.copy(categoryBudgetMessage = "Έλεγξε το όριο και το ποσοστό ειδοποίησης για ${invalid.name}.")
        } else {
            state.copy(categoryBudgetMessage = "Τα τοπικά προσχέδια budgets ενημερώθηκαν. Δεν έχουν συγχρονιστεί.")
        }
    }

    is PlanAction.ToggleRule -> state.copy(
        rules = state.rules.map { rule ->
            if (rule.id == action.id) rule.copy(enabled = !rule.enabled) else rule
        },
    )

    is PlanAction.ForecastHorizonChanged -> state.copy(
        forecastHorizonDays = action.days.takeIf { days -> state.forecastWindows.any { it.days == days } }
            ?: state.forecastHorizonDays,
    )
}

class PlanViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(syntheticPlanUiState())
    val state: StateFlow<PlanUiState> = mutableState.asStateFlow()

    fun onAction(action: PlanAction) {
        mutableState.update { reducePlan(it, action) }
    }
}

/** Explicit preview/test fixture. Never use as a production-state default. */
fun syntheticPlanUiState(): PlanUiState = PlanUiState(
    items = syntheticPlannedItems(),
    budget = BudgetDraft(monthlyLimitText = "900", alertThresholdText = "80"),
    categoryBudgets = syntheticCategoryBudgets(),
    rules = syntheticPlanningRules(),
    forecastWindows = syntheticForecastWindows(),
    forecastHorizonDays = 30,
    forecastEndBalance = 1_620.0,
)

fun syntheticPlannedItems() = listOf(
    PlannedItem(
        id = "plan-1",
        title = "Ενοίκιο",
        dueLabel = "1 Σεπ",
        amount = 680.0,
        kind = PlannedKind.RECURRING,
        category = "Στέγαση",
        accountLabel = "Κύριος λογαριασμός",
        note = "Μηνιαία πληρωμή κατοικίας",
    ),
    PlannedItem(
        id = "plan-2",
        title = "Internet",
        dueLabel = "3 Σεπ",
        amount = 34.90,
        kind = PlannedKind.RECURRING,
        category = "Λογαριασμοί",
        accountLabel = "Κύριος λογαριασμός",
    ),
    PlannedItem(
        id = "plan-3",
        title = "Ασφάλεια αυτοκινήτου",
        dueLabel = "8 Σεπ",
        amount = 126.0,
        kind = PlannedKind.SCHEDULED,
        category = "Μετακίνηση",
        accountLabel = "Κύριος λογαριασμός",
    ),
    PlannedItem(
        id = "plan-4",
        title = "Δόση δανείου",
        dueLabel = "12 Σεπ",
        amount = 185.0,
        kind = PlannedKind.RECURRING,
        category = "Δάνειο",
        accountLabel = "Κύριος λογαριασμός",
    ),
)

fun syntheticCategoryBudgets() = listOf(
    CategoryBudget("budget-food", "Τρόφιμα", "320", spent = 214.40),
    CategoryBudget("budget-home", "Στέγαση", "760", alertThresholdText = "90", spent = 680.0),
    CategoryBudget("budget-transport", "Μετακίνηση", "180", spent = 92.30),
    CategoryBudget("budget-fun", "Ψυχαγωγία", "140", spent = 71.80),
)

fun syntheticPlanningRules() = listOf(
    PlanningRule(
        id = "rule-rollover",
        title = "Μεταφορά αχρησιμοποίητου ορίου",
        description = "Εμφάνιση του υπολοίπου του προηγούμενου μήνα ως διαθέσιμου περιθωρίου στον επόμενο.",
        enabled = false,
    ),
    PlanningRule(
        id = "rule-early-warning",
        title = "Έγκαιρη προειδοποίηση",
        description = "Τόνισε την κατηγορία πριν φτάσει το κανονικό όριο ειδοποίησης όταν ο ρυθμός δαπανών είναι υψηλός.",
        enabled = true,
    ),
    PlanningRule(
        id = "rule-upcoming",
        title = "Κράτηση για επόμενες υποχρεώσεις",
        description = "Στην πρόβλεψη αφαιρείται οπτικά το ποσό των κοντινών επαναλαμβανόμενων και προγραμματισμένων υποχρεώσεων.",
        enabled = true,
    ),
)

fun syntheticForecastWindows() = listOf(
    ForecastWindow(30, "30 ημέρες", expectedIncome = 2_250.0, expectedOutflow = 1_740.0, balanceDeltaFromThirtyDays = 0.0),
    ForecastWindow(60, "60 ημέρες", expectedIncome = 4_500.0, expectedOutflow = 3_620.0, balanceDeltaFromThirtyDays = 370.0),
    ForecastWindow(90, "90 ημέρες", expectedIncome = 6_750.0, expectedOutflow = 5_540.0, balanceDeltaFromThirtyDays = 610.0),
)
