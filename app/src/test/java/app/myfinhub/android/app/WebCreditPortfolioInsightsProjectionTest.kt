package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.canonicalReportCreditPortfolio
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_MONTH
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_30_DAYS
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_90_DAYS
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WebCreditPortfolioInsightsProjectionTest {
    @Test fun nativeMonthCreditPortfolioExactlyMatchesWebCanonicalRowsAndLeavesLedgerUntouched() {
        val document=CanonicalFinanceDocument(Json.parseToJsonElement("""
         {"seed":{},"state":{"settings":{"creditLimit":500},
          "cards":[{"id":"c","kind":"credit","nickname":"Alpha",
             "createdAt":"2026-03-01T00:00:00Z","active":true}],
          "events":[{"id":"charge","kind":"card_purchase","date":"2026-10-08",
            "cardId":"c","amount":150}]}}
        """.trimIndent()).jsonObject)
        val before=document.raw
        val date=LocalDate.parse("2026-10-19")
        val expected=document.canonicalReportCreditPortfolio(date.toString())
        val ui=projectCanonicalInsightsState(document,date)
        val month=ui.periodScope(INSIGHTS_PERIOD_MONTH).creditPortfolio
        assertNotNull(month)
        assertEquals(expected.debt,month!!.debt,0.001)
        assertEquals(expected.limit,month.limit,0.001)
        assertEquals(expected.usage,month.usage,0.001)
        assertEquals(expected.cards.map{it.id},month.rows.map{it.id})
        assertEquals(expected.cards.map{it.available},month.rows.map{it.available})
        assertNull(ui.periodScope(INSIGHTS_PERIOD_30_DAYS).creditPortfolio)
        assertNull(ui.periodScope(INSIGHTS_PERIOD_90_DAYS).creditPortfolio)
        assertEquals(before,document.raw)
    }
}
