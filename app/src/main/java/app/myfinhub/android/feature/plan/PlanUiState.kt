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
)

enum class PlannedKind { RECURRING, SCHEDULED }

data class BudgetDraft(
    val monthlyLimitText: String = "900",
    val alertThresholdText: String = "80",
)

data class PlanUiState(
    val items: List<PlannedItem> = syntheticPlannedItems(),
    val budget: BudgetDraft = BudgetDraft(),
    val forecastEndBalance: Double = 1_620.0,
    val message: String? = null,
)

sealed interface PlanAction {
    data class MonthlyLimitChanged(val value: String) : PlanAction
    data class AlertThresholdChanged(val value: String) : PlanAction
    data object SaveBudget : PlanAction
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
            else -> state.copy(message = "Το budget draft είναι έγκυρο για αποθήκευση στο canonical state.")
        }
    }
}

class PlanViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(PlanUiState())
    val state: StateFlow<PlanUiState> = mutableState.asStateFlow()

    fun onAction(action: PlanAction) {
        mutableState.update { reducePlan(it, action) }
    }
}

fun syntheticPlannedItems() = listOf(
    PlannedItem("plan-1", "Ενοίκιο", "1 Σεπ", 680.0, PlannedKind.RECURRING),
    PlannedItem("plan-2", "Internet", "3 Σεπ", 34.90, PlannedKind.RECURRING),
    PlannedItem("plan-3", "Ασφάλεια αυτοκινήτου", "8 Σεπ", 126.0, PlannedKind.SCHEDULED),
    PlannedItem("plan-4", "Δόση δανείου", "12 Σεπ", 185.0, PlannedKind.RECURRING),
)
