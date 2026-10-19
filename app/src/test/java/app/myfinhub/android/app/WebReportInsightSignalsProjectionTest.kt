package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.canonicalReportInsightSignals
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

class WebReportInsightSignalsProjectionTest {
    @Test fun productionInsightsMonthlySignalsMatchCanonicalReportWithoutDataWrites() {
        val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
           {"seed":{"transactions":[],"recurring":[
              {"id":"r","name":"Utilities","amount":40,"active":true}
           ]},"state":{"settings":{},"events":[
             {"id":"prev","kind":"expense","date":"2026-09-12","amount":50,
              "category":"Υγεία"},
             {"id":"current-income","kind":"income","date":"2026-10-02","amount":900},
             {"id":"current-spending","kind":"expense","date":"2026-10-10",
              "amount":150,"category":"Υγεία"}
           ]}}
        """.trimIndent()).jsonObject)
        val before=doc.raw
        val date=LocalDate.parse("2026-10-19")
        val raw=doc.canonicalReportInsightSignals("2026-10",date.toString())
        val insights=projectCanonicalInsightsState(doc,date)
        val month=insights.periodScope(INSIGHTS_PERIOD_MONTH).monthlyReportSignals
        assertNotNull(month)
        assertEquals(raw.netFlow,month!!.netFlow,0.001)
        assertEquals(raw.savingsRate,month.savingsRate)
        assertEquals(raw.trailingExpenseAverage,month.trailingExpenseAverage,0.001)
        assertEquals(raw.expenseVsTrailingAverage,month.expenseVsTrailingAverage)
        assertEquals(raw.recurringBurden,month.recurringBurden)
        assertEquals(raw.topCategory!!.name,month.topCategoryName)
        assertEquals(raw.topCategory!!.amount,month.topCategoryAmount!!,0.001)
        assertNull(insights.periodScope(INSIGHTS_PERIOD_30_DAYS).monthlyReportSignals)
        assertNull(insights.periodScope(INSIGHTS_PERIOD_90_DAYS).monthlyReportSignals)
        assertEquals(before,doc.raw)
    }
}
