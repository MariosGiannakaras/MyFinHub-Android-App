package app.myfinhub.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalInsightsRangeTest {
    private val document = canonicalFixture()

    @Test
    fun flowBetween_usesInclusiveRangeWithExistingCanonicalSemantics() {
        val firstTenDays = document.flowBetween("2026-08-01", "2026-08-10")
        val throughFifteenth = document.flowBetween("2026-08-01", "2026-08-15")

        assertEquals(0.0, firstTenDays.income, 0.001)
        assertEquals(120.0, firstTenDays.expense, 0.001)
        assertEquals(200.0, throughFifteenth.income, 0.001)
        assertEquals(150.0, throughFifteenth.expense, 0.001)
    }

    @Test
    fun categoryTotalsBetween_respectsRangeAndRefundSemantics() {
        val firstTenDays = document.categoryTotalsBetween("2026-08-01", "2026-08-10")
        val throughEighteenth = document.categoryTotalsBetween("2026-08-01", "2026-08-18")

        assertEquals(120.0, firstTenDays.getValue("Τρόφιμα"), 0.001)
        assertEquals(145.0, throughEighteenth.getValue("Τρόφιμα"), 0.001)
        assertEquals(10.0, throughEighteenth.getValue("Έξοδος"), 0.001)
    }

    @Test
    fun reversedRange_isEmptyInsteadOfLeakingAnotherPeriod() {
        val flow = document.flowBetween("2026-08-10", "2026-08-01")
        val categories = document.categoryTotalsBetween("2026-08-10", "2026-08-01")

        assertEquals(0.0, flow.income, 0.001)
        assertEquals(0.0, flow.expense, 0.001)
        assertTrue(categories.isEmpty())
    }
}
