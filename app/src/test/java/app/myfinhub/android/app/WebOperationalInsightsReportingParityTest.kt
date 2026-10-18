package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.canonicalOperationalFlowBetween
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Web ReportsPage reportFlowSeries and operationalReportSnapshot semantics. */
class WebOperationalInsightsReportingParityTest {
    @Test fun historicalPayAndSaveDoesNotInflateReportsTrendOrRollingComparison() {
        val data = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[{"id":"cash","kind":"cash"}],
            "snapshots":[{"date":"2026-08-01","balances":{"cash":300}}],
            "transactions":[
              {"id":"august","type":"expense","date":"2026-08-20",
                "amount":5,"note":"Pay & Save","accountId":"cash"},
              {"id":"september","type":"expense","date":"2026-09-21",
                "amount":12,"note":"Pay & Save","accountId":"cash"},
              {"id":"current-saving","type":"expense","date":"2026-10-10",
                "amount":15,"note":"Pay & Save","accountId":"cash"},
              {"id":"current-food","type":"expense","date":"2026-10-11",
                "amount":25,"note":"Τρόφιμα","accountId":"cash"},
              {"id":"current-income","type":"income","date":"2026-10-12",
                "amount":200,"note":"Μισθός","accountId":"cash"}
            ]},"state":{"settings":{},"events":[]}}
        """.trimIndent()).jsonObject)

        val insights = projectCanonicalInsightsState(data, LocalDate.parse("2026-10-18"))
        val trend = insights.monthlyTrend
        assertEquals(0.0, trend[1].expense, 0.001)
        assertEquals(0.0, trend[2].expense, 0.001)
        assertEquals(25.0, trend.last().expense, 0.001)
        assertEquals(200.0, trend.last().income, 0.001)
        assertEquals(25.0, insights.periods.first().comparison.currentExpense, 0.001)
        // September Pay & Save is outside the Oct 1-18 month-to-date window,
        // but in a rolling interval it remains saving rather than spending.
        val rolling = data.canonicalOperationalFlowBetween("2026-09-15", "2026-10-18")
        assertEquals(25.0, rolling.expense, 0.001)
        assertEquals(27.0, rolling.saving, 0.001)
        assertEquals(200.0, rolling.income, 0.001)
        assertEquals(175.0, rolling.net, 0.001)
    }
}
