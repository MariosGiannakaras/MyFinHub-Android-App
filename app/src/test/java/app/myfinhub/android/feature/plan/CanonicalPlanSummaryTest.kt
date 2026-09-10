package app.myfinhub.android.feature.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalPlanSummaryTest {
    @Test
    fun budgetProgress_reportsSpentRemainingPercentAndThreshold() {
        val state = PlanUiState(
            budget = BudgetDraft(monthlyLimitText = "800", alertThresholdText = "80"),
            budgetSpent = 680.0,
        )

        val progress = assertNotNull(canonicalBudgetProgress(state))

        assertEquals(800.0, progress.limit, 0.001)
        assertEquals(680.0, progress.spent, 0.001)
        assertEquals(120.0, progress.remaining, 0.001)
        assertEquals(85, progress.percent)
        assertEquals(0.85f, progress.progress, 0.001f)
        assertTrue(progress.thresholdReached)
    }

    @Test
    fun budgetProgress_capsBarButKeepsOverrunSemantics() {
        val state = PlanUiState(
            budget = BudgetDraft(monthlyLimitText = "800", alertThresholdText = "80"),
            budgetSpent = 900.0,
        )

        val progress = assertNotNull(canonicalBudgetProgress(state))

        assertEquals(-100.0, progress.remaining, 0.001)
        assertEquals(113, progress.percent)
        assertEquals(1.0f, progress.progress, 0.001f)
        assertTrue(progress.thresholdReached)
    }
}
