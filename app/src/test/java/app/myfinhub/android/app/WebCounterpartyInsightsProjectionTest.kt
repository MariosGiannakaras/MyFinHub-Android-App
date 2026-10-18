package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.canonicalReportExpenseCounterparties
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_MONTH
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_30_DAYS
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_90_DAYS
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real Insights UI only shows Web monthly counterparties within the month period. */
class WebCounterpartyInsightsProjectionTest {
    @Test fun monthlyReportCounterpartiesBindToCanonicalWebSourceWithoutLedgerChanges() {
        val date=LocalDate.parse("2026-10-15")
        val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[{"id":"bank","kind":"bank"}],
            "transactions":[]},
           "state":{"events":[
             {"id":"a","date":"2026-10-04","kind":"expense",
              "amount":22,"category":"Έξοδος","note":"Cafe"},
             {"id":"b","date":"2026-10-05","kind":"card_purchase",
              "amount":18,"category":"Έξοδος","note":"cafe"},
             {"id":"c","date":"2026-10-24","kind":"expense",
              "amount":120,"category":"Άλλο","note":"Future"}
           ],"settings":{}}}
        """.trimIndent()).jsonObject)
        val original=doc.raw
        val expected=doc.canonicalReportExpenseCounterparties("2026-10",date.toString())
        val screen=projectCanonicalInsightsState(doc,date)
        val monthly=screen.periodScope(INSIGHTS_PERIOD_MONTH)
        assertEquals(expected.map { it.title },monthly.counterparties.map { it.title })
        assertEquals(expected.map { it.amount },monthly.counterparties.map { it.amount })
        assertEquals(expected.map { it.count },monthly.counterparties.map { it.count })
        assertEquals(expected.map { it.category },monthly.counterparties.map { it.category })
        assertEquals(1,monthly.counterparties.size)
        assertEquals(2,monthly.counterparties.single().count)
        assertEquals(40.0,monthly.counterparties.single().amount,0.001)
        assertTrue(screen.periodScope(INSIGHTS_PERIOD_30_DAYS).counterparties.isEmpty())
        assertTrue(screen.periodScope(INSIGHTS_PERIOD_90_DAYS).counterparties.isEmpty())
        assertEquals(original,doc.raw)
    }
}
