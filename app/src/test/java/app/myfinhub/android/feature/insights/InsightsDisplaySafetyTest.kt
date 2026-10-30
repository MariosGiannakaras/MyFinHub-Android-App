package app.myfinhub.android.feature.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsDisplaySafetyTest {
    @Test fun nonfiniteMoneyAndRatiosNeverLookLikeValidZeroOrOverflow() {
        assertEquals(INVALID_INSIGHT_MONEY, insightsCurrency(Double.NaN))
        assertEquals(INVALID_INSIGHT_MONEY, insightsCurrency(Double.POSITIVE_INFINITY))
        assertEquals(INVALID_INSIGHT_MONEY, insightsCurrency(Double.NEGATIVE_INFINITY))
        assertTrue(insightsCurrency(20.0).contains("20"))

        assertEquals(INVALID_INSIGHT_RATIO, insightsRatioLabel(Double.NaN))
        assertEquals(INVALID_INSIGHT_RATIO, insightsRatioLabel(Double.POSITIVE_INFINITY))
        assertEquals(INVALID_INSIGHT_RATIO, insightsRatioLabel(1e308))
        assertEquals("Χωρίς προηγούμενη βάση", insightsRatioLabel(null))
        assertEquals("50%", insightsRatioLabel(0.5))
        assertEquals("+20%", insightsRatioLabel(0.2, signed = true))
        assertEquals("-10%", insightsRatioLabel(-0.1, signed = true))
        assertEquals("0%", insightsRatioLabel(0.0))
        assertEquals(INVALID_INSIGHT_RATIO, insightsCategoryShareLabel(Float.NaN))
        assertEquals(INVALID_INSIGHT_RATIO, insightsCategoryShareLabel(Float.POSITIVE_INFINITY))
        assertEquals(INVALID_INSIGHT_RATIO, insightsCategoryShareLabel(-0.2f))
        assertEquals("40%", insightsCategoryShareLabel(0.4f))
    }

    @Test fun expenseComparisonDoesNotTurnNonfiniteOrOverflowIntoZeroPercent() {
        assertEquals(20.0,
            InsightsComparison("Τώρα", "Πριν", 100.0, 120.0, 90.0, 100.0)
                .expenseChangePercent ?: -1.0, 0.0001)
        assertNull(InsightsComparison("T", "P", 0.0, Double.NaN, 0.0, 100.0)
            .expenseChangePercent)
        assertNull(InsightsComparison("T", "P", 0.0, 10.0, 0.0,
            Double.POSITIVE_INFINITY).expenseChangePercent)
        assertNull(InsightsComparison("T", "P", 0.0, 1e308, 0.0, 0.01)
            .expenseChangePercent)
    }

    @Test fun chartInvalidSourcesNeverProduceFakeBarValues() {
        assertTrue(insightsTrendCanDraw(listOf(TrendPoint("Οκτ", 100.0, 20.0))))
        assertFalse(insightsTrendCanDraw(listOf(TrendPoint("Οκτ", Double.NaN, 20.0))))
        assertFalse(insightsTrendCanDraw(listOf(TrendPoint("Οκτ", 20.0, Double.POSITIVE_INFINITY))))
        assertFalse(insightsTrendCanDraw(listOf(TrendPoint("Οκτ", -20.0, 30.0))))
    }
}
