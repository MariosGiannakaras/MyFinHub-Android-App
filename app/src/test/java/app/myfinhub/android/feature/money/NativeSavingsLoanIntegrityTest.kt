package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSavingsLoanIntegrityTest {
    @Test fun savingsGoalsWithDuplicateOrBlankIdentityAreNeverWritable() {
        val goal = SavingsGoalItem("g1", "Ταμείο", 200.0, null, 100.0, 50.0, false)
        assertTrue(canonicalSavingsGoalIdUnique("g1", listOf(goal)))
        assertFalse(canonicalSavingsGoalIdUnique("g1", listOf(goal, goal.copy(name = "Διπλό"))))
        assertFalse(canonicalSavingsGoalIdUnique("", listOf(goal.copy(id = ""))))
        assertFalse(canonicalSavingsGoalIdUnique("missing", listOf(goal)))
    }

    @Test fun savingsRatesPreserveValidPercentButRejectNaNAndOverflow() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 1e308)) {
            assertNull(safeSavingsGoalProgressPercent(invalid))
        }
        assertEquals(50, safeSavingsGoalProgressPercent(50.8))
        assertEquals(150, safeSavingsGoalProgressPercent(150.0))
        assertEquals(0, safeSavingsGoalProgressPercent(0.0))
        assertNull(safeSavingsMonthlyTargetPercent(Double.NaN))
        assertNull(safeSavingsMonthlyTargetPercent(Double.POSITIVE_INFINITY))
        assertNull(safeSavingsMonthlyTargetPercent(-0.1))
        assertNull(safeSavingsMonthlyTargetPercent(1.1))
        assertEquals(20, safeSavingsMonthlyTargetPercent(0.2))
        assertEquals(100, safeSavingsMonthlyTargetPercent(1.0))
    }

    @Test fun repaymentDisallowedForNonfiniteOrDuplicateLoanBalances() {
        val loan = LoanItem(id = "loan", name = "Δάνειο", lender = "Τράπεζα",
            remaining = 120.0, monthlyPayment = 40.0, nextPaymentLabel = "5 Νοε",
            remainingInstallments = 3)
        assertTrue(canonicalLoanPaymentIsUnambiguous(loan, listOf(loan)))
        assertFalse(canonicalLoanPaymentIsUnambiguous(loan, listOf(loan, loan)))
        assertFalse(canonicalLoanPaymentIsUnambiguous(loan.copy(id = ""), listOf(loan.copy(id = ""))))
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val row = loan.copy(remaining = bad)
            assertFalse(canonicalLoanPaymentIsUnambiguous(row, listOf(row)))
        }
    }
}
