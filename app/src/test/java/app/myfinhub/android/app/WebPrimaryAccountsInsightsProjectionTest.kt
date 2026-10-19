package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.canonicalReportPrimaryAccountSeries
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

class WebPrimaryAccountsInsightsProjectionTest {
    @Test fun usesCanonicalCashExcludedPayrollAndSavingsRolesAndNoHistoricalWrites() {
        val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[
            {"id":"daily","kind":"cash","cashRole":"daily","name":"Μετρητά"},
            {"id":"pay","kind":"bank","bankAccountCategory":"payroll",
             "name":"Ο Μισθός"},
            {"id":"save","kind":"savings","name":"Στόχος"},
            {"id":"other","kind":"bank","name":"Αλλος"}
          ],"snapshots":[{"date":"2026-10-01",
            "balances":{"pay":800,"save":500,"daily":150,"other":900}}]},
           "state":{"events":[],"settings":{}}}
        """.trimIndent()).jsonObject)
        val before=doc.raw
        val today=LocalDate.parse("2026-10-19")
        val screen=projectCanonicalInsightsState(doc,today)
        val month=screen.periodScope(INSIGHTS_PERIOD_MONTH).primaryAccountSeries
        assertNotNull(month)
        assertEquals("Ο Μισθός",month!!.payrollLabel)
        assertEquals("Στόχος",month.savingsLabel)
        assertEquals(6,month.months.size)
        assertEquals("2026-10-19",month.months.last().cutoffDateIso)
        val direct=doc.canonicalReportPrimaryAccountSeries(
            "2026-10",listOf("pay","save"),asOf="2026-10-19")
        assertEquals(direct.map { it.balances["pay"] },
            month.months.map { it.payrollBalance })
        assertEquals(direct.map { it.balances["save"] },
            month.months.map { it.savingsBalance })
        assertNull(screen.periodScope(INSIGHTS_PERIOD_30_DAYS).primaryAccountSeries)
        assertNull(screen.periodScope(INSIGHTS_PERIOD_90_DAYS).primaryAccountSeries)
        assertEquals(before,doc.raw)
    }
}
