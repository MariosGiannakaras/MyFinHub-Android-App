package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web forecastScenarios.ts per-account stress stays source-canonical. */
class WebForecastScenarioAccountRiskParityTest {
    private val today = LocalDate.parse("2026-08-17")
    private fun data() = CanonicalFinanceDocument(Json.parseToJsonElement("""
        {"seed":{"accounts":[
            {"id":"bank","name":"Μισθοδοσία","kind":"bank"},
            {"id":"cash","name":"Μετρητά","kind":"cash"}],
          "snapshots":[{"date":"2026-08-17",
            "balances":{"bank":1000,"cash":150}}],
          "transactions":[]},
         "state":{"events":[],"scheduled":[
          {"id":"bill-1","kind":"expense","status":"pending",
           "dueDate":"2026-08-18","accountId":"bank","amount":60,
           "note":"Πληρωμή","createdAt":"2026-08-01T00:00:00Z"}]}}
    """.trimIndent()).jsonObject)

    @Test fun perAccountRiskUsesRealBaselineAndCentExactHypotheticalDelta() {
        val finance = data()
        val original = finance.raw
        val result = finance.webForecastScenario(today,30,listOf(
            CanonicalForecastAssumption(
                "hypothetical-1","bank","2026-08-19","extra_expense",990.0),
            CanonicalForecastAssumption(
                "hypothetical-2","bank","2026-08-20","extra_income",500.0),
        ))
        val rows = result.canonicalScenarioAccountRiskRows()
        assertEquals(listOf("bank","cash"),rows.map { it.accountId })
        val bank = rows.first()
        assertEquals(940.0,bank.baselineProjected,0.00001)
        assertEquals(450.0,bank.scenarioProjected,0.00001)
        assertEquals(-490.0,bank.scenarioDifference,0.00001)
        assertEquals(-50.0,bank.minimum,0.00001)
        assertEquals(LocalDate.parse("2026-08-19"),bank.firstNegativeDate)
        assertEquals(LocalDate.parse("2026-08-19"),bank.firstLowDate)
        val cash = rows.last()
        assertEquals(150.0,cash.baselineProjected,0.00001)
        assertEquals(150.0,cash.scenarioProjected,0.00001)
        assertEquals(0.0,cash.scenarioDifference,0.00001)
        assertNull(cash.firstNegativeDate)
        assertNull(cash.firstLowDate)
        assertEquals(original,finance.raw)
        assertTrue(finance.canonicalEvents().isEmpty())
    }

    @Test fun lowBalanceThresholdIsTrackedWithoutInventingPortfolioShortfall() {
        val finance = data()
        val rows = finance.webForecastScenario(today,60,listOf(
            CanonicalForecastAssumption(
                "spend","cash","2026-08-19","extra_expense",75.01),
        )).canonicalScenarioAccountRiskRows()
        val cash = rows.last()
        assertEquals(74.99,cash.scenarioProjected,0.00001)
        assertEquals(-75.01,cash.scenarioDifference,0.00001)
        assertEquals(LocalDate.parse("2026-08-19"),cash.firstLowDate)
        assertNull(cash.firstNegativeDate)
        assertEquals(74.99,cash.minimum,0.00001)
    }

    @Test fun emptyScenariosRetainZeroAccountDeltasAtEveryHorizon() {
        val data = data()
        val original = data.raw
        listOf(30,60,90).forEach { days ->
            val rows = data.cashFlowForecast(today,days)
                .webForecastScenario(emptyList()).canonicalScenarioAccountRiskRows()
            assertEquals(2,rows.size)
            assertTrue(rows.all { it.scenarioDifference == 0.0 })
            assertTrue(rows.all { it.scenarioProjected == it.baselineProjected })
        }
        assertEquals(original,data.raw)
    }

    @Test fun multipleAccountsReceiveIndependentExactCentScenarioImpacts() {
        val data = data()
        val rows = data.webForecastScenario(today,90,listOf(
            CanonicalForecastAssumption(
                "income","bank","2026-08-20","extra_income",2.22),
            CanonicalForecastAssumption(
                "expense","cash","2026-08-20","extra_expense",1.11),
        )).canonicalScenarioAccountRiskRows()
        assertEquals(2.22,rows.first().scenarioDifference,0.00001)
        assertEquals(-1.11,rows.last().scenarioDifference,0.00001)
        assertEquals(1091.11, rows.sumOf { it.scenarioProjected }, 0.00001)
    }
}
