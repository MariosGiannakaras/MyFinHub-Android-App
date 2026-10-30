package app.myfinhub.android.feature.money

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditRateSourceSafetyTest {
    private val today = LocalDate.of(2026, 10, 30)
    private fun rate(date: String, percent: Double) = CardAnnualRateVersion(date, percent)

    @Test fun latestValidHistoricRateRemainsSelectedAndFutureRateIgnored() {
        val older = rate("2026-01-01", 14.0)
        val newer = rate("2026-10-01", 12.5)
        val selected = selectedCreditRate(listOf(older, newer, rate("2026-12-01", 17.0)), today)
        assertEquals(CreditRateSelection.Selected(newer), selected)
        assertEquals(CreditRateSelection.Missing, selectedCreditRate(listOf(rate("2026-11-01", 5.0)), today))
    }

    @Test fun latestInvalidOrAmbiguousDateNeverFallsBackToEarlierRate() {
        val earlier = rate("2026-01-01", 10.0)
        assertEquals(CreditRateSelection.Invalid,
            selectedCreditRate(listOf(earlier, rate("2026-10-20", Double.NaN)), today))
        assertEquals(CreditRateSelection.Invalid,
            selectedCreditRate(listOf(earlier, rate("2026-10-20", 101.0)), today))
        assertEquals(CreditRateSelection.Invalid,
            selectedCreditRate(listOf(earlier, rate("2026-10-20", -0.1)), today))
        assertEquals(CreditRateSelection.Invalid,
            selectedCreditRate(listOf(earlier, rate("2026-10-20", 11.0),
                rate("2026-10-20", 11.0)), today))
    }

    @Test fun malformedDateCannotBecomeActiveRateOrLeakInvalidPercent() {
        assertEquals(CreditRateSelection.Invalid,
            selectedCreditRate(listOf(rate("2026-13-01", Double.POSITIVE_INFINITY)), today))
        assertTrue(displayCreditRateHistory(rate("2026-13-01", 6.0)).contains("Μη έγκυρη"))
        assertTrue(displayCreditRateHistory(rate("2026-10-01", Double.NaN)).contains("Μη έγκυρη"))
        assertFalse(displayCreditRateHistory(rate("2026-10-01", Double.NaN)).contains("NaN"))
        assertEquals("2026-10-01: 12.5%", displayCreditRateHistory(rate("2026-10-01", 12.5)))
        assertTrue(isValidCreditAnnualPercent(0.0))
        assertTrue(isValidCreditAnnualPercent(100.0))
        assertFalse(isValidCreditAnnualPercent(Double.POSITIVE_INFINITY))
    }
}
