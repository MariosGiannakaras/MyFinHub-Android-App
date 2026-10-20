package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.canonicalReportCreditPortfolio
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_MONTH
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_30_DAYS
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebCreditPortfolioInsightsProjectionTest {
    @Test fun productionMonthlyReportKeepsAsOfAndOriginalFinanceUnchanged() {
        val document = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{},"state":{"settings":{"creditLimit":200},
            "cards":[{"id":"a","kind":"credit","nickname":"Card",
                      "active":true,"creditLimit":150,"createdAt":"2026-01-01"}],
            "events":[
              {"id":"old","kind":"card_purchase","date":"2026-10-09",
               "cardId":"a","amount":55,"creditDelta":-55},
              {"id":"future","kind":"card_purchase","date":"2026-10-31",
               "cardId":"a","amount":80,"creditDelta":-80}
            ]}}
        """.trimIndent()).jsonObject)
        val before=document.raw
        val date=LocalDate.parse("2026-10-20")
        val reference=document.canonicalReportCreditPortfolio(date.toString())
        val state=projectCanonicalInsightsState(document,date)
        val month=state.periodScope(INSIGHTS_PERIOD_MONTH).creditPortfolio!!
        assertEquals(reference.debt,month.debt,0.001)
        assertEquals(reference.limit,month.limit,0.001)
        assertEquals(reference.cards.map { it.id },month.cards.map { it.id })
        assertEquals(55.0,month.debt,0.001)
        assertNull(state.periodScope(INSIGHTS_PERIOD_30_DAYS).creditPortfolio)
        assertEquals(before,document.raw)
    }
}
