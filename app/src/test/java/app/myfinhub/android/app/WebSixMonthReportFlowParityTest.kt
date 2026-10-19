package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.canonicalOperationalMonthlyFlow
import app.myfinhub.android.core.data.canonicalOperationalFlowBetween
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web ReportsPage reportFlowSeries(6) and cumulativeSeries goldens. */
class WebSixMonthReportFlowParityTest {
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[],"transactions":[
        {"id":"nov-income","date":"2025-11-08","type":"income","amount":400},
        {"id":"dec-expense","date":"2025-12-18","type":"expense","amount":75},
        {"id":"jan-income","date":"2026-01-05","type":"income","amount":300},
        {"id":"mar-expense","date":"2026-03-11","type":"expense","amount":55},
        {"id":"apr-future","date":"2026-04-27","type":"expense","amount":900}
      ]},"state":{"settings":{},"events":[
        {"id":"feb-refund","kind":"refund","date":"2026-02-15","amount":10},
        {"id":"apr-saving","kind":"saving_cash_offset","date":"2026-04-06",
         "amount":20,"savingAmount":20},
        {"id":"apr-expense","kind":"expense","date":"2026-04-14","amount":50}
      ]}}
    """.trimIndent()).jsonObject)

    @Test fun sixMonthSequenceCrossesNewYearWithAsOfCappedCumulativeFlow() {
        val before = doc.raw
        val asOf = LocalDate.parse("2026-04-19")
        val trend = projectCanonicalInsightsState(doc, asOf).monthlyTrend
        assertEquals(listOf("2025-11","2025-12","2026-01",
            "2026-02","2026-03","2026-04"), trend.map { it.monthIso })
        var running = 0.0
        for (point in trend) {
            val month = YearMonth.parse(point.monthIso)
            val flow = if (month == YearMonth.from(asOf)) {
                doc.canonicalOperationalFlowBetween(month.atDay(1).toString(), asOf.toString())
            } else doc.canonicalOperationalMonthlyFlow(month.toString())
            assertEquals(flow.income, point.income, 0.001)
            assertEquals(flow.expense, point.expense, 0.001)
            assertEquals(flow.saving, point.saving, 0.001)
            running += point.income - point.expense
            assertEquals(running, point.cumulativeNet, 0.001)
        }
        assertTrue(trend.last().isPartial)
        assertFalse(trend[4].isPartial)
        assertEquals(50.0, trend.last().expense, 0.001)
        assertEquals(20.0, trend.last().saving, 0.001)
        assertEquals(before, doc.raw)
    }

    @Test fun fullMonthIncludesLateMovementWithOnlySixSlots() {
        val trend = projectCanonicalInsightsState(doc, LocalDate.parse("2026-04-30")).monthlyTrend
        assertEquals(6, trend.size)
        assertFalse(trend.last().isPartial)
        assertEquals(950.0, trend.last().expense, 0.001)
        assertEquals(20.0, trend.last().saving, 0.001)
    }
}
