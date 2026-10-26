package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden Web tests/forecast-scenarios.test.ts: what-if overlays never post real money. */
class WebForecastScenarioParityTest {
    private val today = LocalDate.parse("2026-08-17")
    private fun finance() = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[
        {"id":"bank","name":"Bank","kind":"bank"},
        {"id":"cash","name":"Cash","kind":"cash"}
      ],"snapshots":[{"date":"2026-08-17",
        "balances":{"bank":1000,"cash":100}}],"transactions":[]},
       "state":{"events":[],"settings":{},
         "scheduled":[
           {"id":"known-1","kind":"expense","status":"pending",
            "dueDate":"2026-08-18","accountId":"bank","amount":60,
            "note":"Planned bill","createdAt":"2026-08-01T00:00:00Z"}
         ]
       }}
    """.trimIndent()).jsonObject)
    private fun assumption(id: String, date: String = "2026-08-19",
        account: String = "bank", kind: String = "extra_expense", amount: Double = 20.0) =
        CanonicalForecastAssumption(id, account, date, kind, amount)

    @Test fun realBillsAndHypotheticalExpensesRemainAdditiveWithoutPosting() {
        val data = finance()
        val original = data.raw
        val result = data.webForecastScenario(today, 30, listOf(
            assumption("extra-1", amount = 990.0),
            assumption("extra-2", date = "2026-08-20", kind = "extra_income", amount = 1000.0),
        ))
        assertEquals(1040.0, result.baseline.projectedPortfolio, 0.00001)
        assertEquals(1050.0, result.projectedPortfolio, 0.00001)
        assertEquals(50.0, result.minimumPortfolio, 0.00001)
        assertEquals(LocalDate.parse("2026-08-19"), result.minimumPortfolioDate)
        assertNull(result.firstPortfolioShortfallDate)
        assertEquals(950.0, result.accounts.first { it.accountId == "bank" }.projected, 0.00001)
        assertEquals(-50.0, result.accounts.first { it.accountId == "bank" }.minimum, 0.00001)
        assertEquals(LocalDate.parse("2026-08-19"),
            result.accounts.first { it.accountId == "bank" }.firstNegativeDate)
        assertEquals(1, result.evidence.declared)
        assertEquals(0, result.evidence.estimated)
        assertEquals(2, result.evidence.hypothetical)
        assertEquals(today, result.points.first().date)
        assertEquals(today.plusDays(30), result.points.last().date)
        assertTrue(data.canonicalEvents().isEmpty())
        assertEquals(original, data.raw)
    }

    @Test fun detectsPortfolioShortfallAndSameDayScheduledBillWithoutDedup() {
        val document = finance()
        val low = document.webForecastScenario(today, 60, listOf(
            assumption("cash", account = "cash", amount = 1200.0)))
        assertEquals(-160.0, low.minimumPortfolio, 0.00001)
        assertEquals(LocalDate.parse("2026-08-19"), low.firstPortfolioShortfallDate)
        val both = document.webForecastScenario(today, 30, listOf(
            assumption("same", date = "2026-08-18", amount = 60.0)))
        assertEquals(980.0, both.points.first { it.date == today.plusDays(1) }.portfolio, 0.00001)
        assertEquals(1, both.evidence.declared)
        assertEquals(1, both.evidence.hypothetical)
    }

    @Test fun emptyOverlayMatchesImmutableForecastForThirtySixtyNinetyDays() {
        val data = finance()
        val original = data.raw
        listOf(30, 60, 90).forEach { days ->
            val baseline = data.cashFlowForecast(today, days)
            val result = baseline.webForecastScenario(emptyList())
            assertEquals(baseline.projectedPortfolio, result.projectedPortfolio, 0.00001)
            assertEquals(baseline.minimumPortfolio, result.minimumPortfolio, 0.00001)
            assertEquals(baseline.endDate, result.points.last().date)
            assertEquals(0, result.evidence.hypothetical)
        }
        assertEquals(original, data.raw)
    }

    @Test fun rejectsMissingAccountsDatesDuplicatesExcessRowsAndFractions() {
        val baseline = finance().cashFlowForecast(today, 30)
        val valid = assumption("valid")
        val invalid = listOf(
            valid.copy(amount = 20.001), valid.copy(amount = 0.0),
            valid.copy(date = "2026-08-17"),
            valid.copy(date = "2026-11-01"),
            valid.copy(date = "2026-02-30"),
            valid.copy(accountId = "ghost"),
            valid.copy(kind = "transfer"),
        )
        invalid.forEach { input ->
            assertThrows(IllegalArgumentException::class.java) {
                baseline.webForecastScenario(listOf(input))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            baseline.webForecastScenario(listOf(valid, valid))
        }
        assertThrows(IllegalArgumentException::class.java) {
            baseline.webForecastScenario(List(17) { index -> valid.copy(id = "id-$index") })
        }
    }

    @Test fun assumptionsApplyOnIntermediateDatesAsIndependentCumulativeCents() {
        val data = finance()
        val result = data.webForecastScenario(today, 30, listOf(
            assumption("expenses", date = "2026-08-19", amount = 10.12),
            assumption("income", date = "2026-08-19", kind = "extra_income", amount = 1.11),
            assumption("cash", date = "2026-08-21", account = "cash", amount = 2.22),
        ))
        assertEquals(1040.0 - 10.12 + 1.11 - 2.22,
            result.projectedPortfolio, 0.00001)
        assertEquals(3, result.evidence.hypothetical)
        assertEquals(result.points.last().portfolio, result.projectedPortfolio, 0.00001)
    }
}
