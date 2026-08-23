package app.myfinhub.android.feature.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanReducerTest {
    @Test
    fun invalidThreshold_isRejected() {
        val state = PlanUiState(budget = BudgetDraft(monthlyLimitText = "900", alertThresholdText = "120"))

        val result = reducePlan(state, PlanAction.SaveBudget)

        assertEquals("Το όριο ειδοποίησης πρέπει να είναι από 1 έως 100%.", result.message)
    }

    @Test
    fun validBudget_isReadyForCanonicalSave() {
        val state = PlanUiState(budget = BudgetDraft(monthlyLimitText = "850,50", alertThresholdText = "80"))

        val result = reducePlan(state, PlanAction.SaveBudget)

        assertTrue(result.message.orEmpty().contains("έγκυρο"))
    }
}
