package app.myfinhub.android.feature.plan

import androidx.lifecycle.ViewModel
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
    val category: String = "Γενικά",
    val accountLabel: String = "Κύριος λογαριασμός",
    val note: String = "",
    val paused: Boolean = false,
)

enum class PlannedKind { RECURRING, SCHEDULED }

data class BudgetDraft(
    val monthlyLimitText: String = "900",
    val alertThresholdText: String = "80",
)

data class CategoryBudget(
    val id: String,
    val name: String,
    val monthlyLimitText: String,
    val alertThresholdText: String = "80",
    val spent: Double,
    val enabled: Boolean = true,
)

data class PlanningRule(
    val id: String,
    val title: String,
    val description: String,
    val enabled: Boolean,
)

data class ForecastWindow(
    val days: Int,
    val label: String,
    val expectedIncome: Double,
    val expectedOutflow: Double,
    val balanceDeltaFromThirtyDays: Double,
)

data class PlanUiState(
    val items: List<PlannedItem> = syntheticPlannedItems(),
    val budget: BudgetDraft = BudgetDraft(),
    val categoryBudgets: List<CategoryBudget> = syntheticCategoryBudgets(),
    val rules: List<PlanningRule> = syntheticPlanningRules(),
    val forecastWindows: List<ForecastWindow> = syntheticForecastWindows(),
    val forecastHorizonDays: Int = 30,
    val forecastEndBalance: Double = 1_620.0,
    val message: String? = null,
    val itemMessage: String? = null,
    val categoryBudgetMessage: String? = null,
)

sealed interface PlanAction {
    data class MonthlyLimitChanged(val value: String) : PlanAction
    data class AlertThresholdChanged(val value: String) : PlanAction
    data object SaveBudget : PlanAction

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
            threshold == null || threshold !in 1..100 -> state.copy(message = "Το όριο ειδοποίησης πρέπει να είναι από 1 έως 100%.")
            else -> state.copy(message = "Το μηνιαίο budget ενημερώθηκε.")
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
                    category = action.category.trim().ifBlank { "Γενικά" },
                    accountLabel = action.accountLabel.trim().ifBlank { "Κύριος λογαριασμός" },
                    note = action.note.trim(),
                )
            }
        },
        itemMessage = "Η υποχρέωση ενημερώθηκε.",
    )

    is PlanAction.TogglePlannedItemPause -> state.copy(
        items = state.items.map { item ->
            if (item.id == action.id) item.copy(paused = !item.paused) else item
        },
        itemMessage = "Η κατάσταση της υποχρέωσης ενημερώθηκε.",
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
            limit == null || limit <= 0.0 || threshold == null || threshold !in 1..100
        }
        if (invalid != null) {
            state.copy(categoryBudgetMessage = "Έλεγξε το όριο και το ποσοστό ειδοποίησης για ${invalid.name}.")
        } else {
            state.copy(categoryBudgetMessage = "Τα budgets ανά κατηγορία ενημερώθηκαν.")
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
    private val mutableState = MutableStateFlow(PlanUiState())
    val state: StateFlow<PlanUiState> = mutableState.asStateFlow()

    fun onAction(action: PlanAction) {
        mutableState.update { reducePlan(it, action) }
    }
}

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
