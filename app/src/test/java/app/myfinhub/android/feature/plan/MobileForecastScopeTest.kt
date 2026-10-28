package app.myfinhub.android.feature.plan

import app.myfinhub.android.core.data.CanonicalCashFlowForecast
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MobileForecastScopeTest {
    private fun cashFlow(days: Int): CanonicalCashFlowForecast {
        val today = LocalDate.parse("2026-10-28")
        return CanonicalCashFlowForecast(
            asOf = today,
            endDate = today.plusDays(days.toLong()),
            horizonDays = days,
            currentPortfolio = 900.0,
            projectedPortfolio = 750.0,
            minimumPortfolio = 500.0,
            minimumPortfolioDate = today,
            points = emptyList(),
            movements = emptyList(),
            accounts = emptyList(),
            omitted = emptyList(),
            sourceCounts = emptyMap(),
        )
    }

    @Test fun mixedWebWindowsProduceOnlyOneAndroidThirtyDayCard() {
        val w60 = ForecastWindow(60, "60 ημέρες", 0.0, 0.0, 0.0)
        val w30 = ForecastWindow(30, "30 ημέρες", 100.0, 90.0, 10.0)
        val w90 = ForecastWindow(90, "90 ημέρες", 0.0, 0.0, 0.0)
        assertSame(w30, mobilePlanForecastWindow(listOf(w60, w30, w90, w30)))
        assertNull(mobilePlanForecastWindow(listOf(w60, w90)))
        assertNull(mobilePlanForecastWindow(emptyList()))
    }

    @Test fun scenarioRequiresARealThirtyDayBaselineEvenIfWebHasOtherHorizons() {
        val b30 = cashFlow(30)
        val b60 = cashFlow(60)
        val b90 = cashFlow(90)
        assertSame(b30, mobilePlanScenarioBaseline(mapOf(60 to b60, 30 to b30, 90 to b90)))
        assertNull(mobilePlanScenarioBaseline(mapOf(60 to b60, 90 to b90)))
        assertNull(mobilePlanScenarioBaseline(mapOf(30 to b60)))
        assertNull(mobilePlanScenarioBaseline(emptyMap()))
        assertEquals(30, mobilePlanScenarioBaseline(mapOf(30 to b30))?.horizonDays)
    }
}
