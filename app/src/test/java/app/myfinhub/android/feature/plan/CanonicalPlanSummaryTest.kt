package app.myfinhub.android.feature.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalPlanSummaryTest {
    @Test
    fun monthlyLimitParserUsesExactCentsAndRejectsInvalidOrUnboundedValues() {
        assertEquals(850.5, mobileBudgetLimitOrNull("850,50")!!, 0.0)
        assertEquals(900.0, mobileBudgetLimitOrNull(" 900.00 ")!!, 0.0)
        assertEquals(0.01, mobileBudgetLimitOrNull("0,01")!!, 0.0)
        listOf("", "0", "-20", "NaN", "Infinity", "1e309", "123.456",
            "10,0,0", "1 000,00", "9223372036854775808", "0,001",
        ).forEach { assertNull("Should not accept $it", mobileBudgetLimitOrNull(it)) }
    }

    @Test
    fun invalidCanonicalBudgetNumbersNeverCreateMisleadingProgress() {
        val input = PlanUiState(budget = BudgetDraft("900", "80"))
        assertNull(canonicalBudgetProgress(input.copy(budgetSpent = Double.NaN)))
        assertNull(canonicalBudgetProgress(input.copy(budgetSpent = Double.POSITIVE_INFINITY)))
        assertNull(canonicalBudgetProgress(input.copy(
            budget = BudgetDraft("Infinity", "80"), budgetSpent = 100.0)))
        assertNull(canonicalBudgetProgress(input.copy(
            budget = BudgetDraft("0,01", "80"), budgetSpent = Double.MAX_VALUE)))
        assertFalse(budgetDraftEquivalent(BudgetDraft("900", "80"), BudgetDraft("Infinity", "80")))
        assertTrue(budgetDraftEquivalent(BudgetDraft("900", "80"), BudgetDraft("900,00", "80")))
    }

    @Test
    fun categoryAndSubcategoryImportedBudgetProgressFailsClosedWithoutInventingZero() {
        val normal = CategoryBudget(
            id = "food", name = "Τρόφιμα", monthlyLimitText = "120,00",
            spent = 135.0, remaining = -15.0,
        )
        val category = requireNotNull(mobileBudgetRowMetrics(normal))
        assertEquals(120.0, category.limit, 0.0)
        assertEquals(135.0, category.used, 0.0)
        assertEquals(-15.0, category.remaining, 0.0)
        assertEquals(1.0f, category.progress, 0.0f)
        assertEquals(-15.0, requireNotNull(
            mobileBudgetRowMetrics(normal, sourceRemaining = true)).remaining, 0.0)
        assertNull(mobileBudgetRowMetrics(normal.copy(monthlyLimitText = "Infinity")))
        assertNull(mobileBudgetRowMetrics(normal.copy(monthlyLimitText = "1,001")))
        assertNull(mobileBudgetRowMetrics(normal.copy(effectiveLimit = Double.NaN)))
        assertNull(mobileBudgetRowMetrics(normal.copy(effectiveLimit = 0.0)))
        assertNull(mobileBudgetRowMetrics(normal.copy(spent = Double.POSITIVE_INFINITY)))
        assertNull(mobileBudgetRowMetrics(normal.copy(spent = Double.NaN)))
        assertNull(mobileBudgetRowMetrics(normal.copy(spent = Double.MAX_VALUE,
            monthlyLimitText = "0,01")))
        assertNull(mobileBudgetRowMetrics(normal.copy(remaining = Double.NEGATIVE_INFINITY),
            sourceRemaining = true))
        assertTrue(mobileBudgetProjectionValid(normal.copy(projectedDue = 1.0,
            projectedRemaining = 10.0)))
        assertFalse(mobileBudgetProjectionValid(normal.copy(
            projectedDue = Double.POSITIVE_INFINITY)))
        assertFalse(mobileBudgetProjectionValid(normal.copy(
            projectedDue = 100.0, projectedRemaining = Double.NaN)))
        assertFalse(mobileBudgetCommitmentValid(Double.NaN))
        assertFalse(mobileBudgetCommitmentValid(Double.POSITIVE_INFINITY))
        assertFalse(mobileBudgetCommitmentValid(-3.0))
        assertTrue(mobileBudgetCommitmentValid(23.25))
    }

    @Test
    fun budgetProgress_reportsSpentRemainingPercentAndThreshold() {
        val state = PlanUiState(
            budget = BudgetDraft(monthlyLimitText = "800", alertThresholdText = "80"),
            budgetSpent = 680.0,
        )

        val progress = requireNotNull(canonicalBudgetProgress(state))

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

        val progress = requireNotNull(canonicalBudgetProgress(state))

        assertEquals(-100.0, progress.remaining, 0.001)
        assertEquals(113, progress.percent)
        assertEquals(1.0f, progress.progress, 0.001f)
        assertTrue(progress.thresholdReached)
    }
}
