package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web primaryAccountSeries month-boundary, account-ID and as-of regression. */
class WebReportPrimaryAccountSeriesParityTest {
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{
        "accounts":[
          {"id":"pay","kind":"bank","bankAccountCategory":"payroll"},
          {"id":"save","kind":"bank","bankAccountCategory":"savings"},
          {"id":"other","kind":"bank"}
        ],"snapshots":[
          {"date":"2026-08-31","balances":{"pay":800,"save":500,"other":200}},
          {"date":"2026-09-30","balances":{"pay":900,"save":550,"other":250}},
          {"date":"2026-10-01","balances":{"pay":1000,"save":600,"other":300}}
        ]
      },"state":{"settings":{},"events":[
        {"id":"increase","date":"2026-10-10","kind":"income","amount":100,
         "legs":[{"accountId":"pay","amount":100}]},
        {"id":"future","date":"2026-10-29","kind":"income","amount":500,
         "legs":[{"accountId":"pay","amount":500}]}
      ]}}
    """.trimIndent()).jsonObject)

    @Test fun sixCalendarMonthsUseFullPastMonthEndAndCurrentAsOf() {
        val before=doc.raw
        val rows=doc.canonicalReportPrimaryAccountSeries(
            month="2026-10",accountIds=listOf("pay","save"),asOf="2026-10-19",
        )
        assertEquals(listOf("2026-05","2026-06","2026-07","2026-08",
            "2026-09","2026-10"),rows.map { it.monthIso })
        assertEquals("2026-08-31",rows[3].cutoffDateIso)
        assertEquals("2026-09-30",rows[4].cutoffDateIso)
        assertEquals("2026-10-19",rows[5].cutoffDateIso)
        assertEquals(800.0,rows[3].balances["pay"]!!,0.001)
        assertEquals(900.0,rows[4].balances["pay"]!!,0.001)
        assertEquals(1100.0,rows[5].balances["pay"]!!,0.001)
        assertEquals(600.0,rows[5].balances["save"]!!,0.001)
        assertTrue(rows.all { it.balances.keys == setOf("pay","save") })
        assertEquals(before,doc.raw)
    }

    @Test fun decToJanBoundaryAndUnknownAccountRemainStable() {
        val points=doc.canonicalReportPrimaryAccountSeries(
            "2026-02",listOf("pay","removed"),count=3,asOf="2026-02-12")
        assertEquals(listOf("2025-12","2026-01","2026-02"),points.map { it.monthIso })
        assertEquals("2026-01-31",points[1].cutoffDateIso)
        assertEquals(0.0,points[0].balances["removed"]!!,0.0)
        assertEquals(emptyList<CanonicalReportPrimaryAccountPoint>(),
            doc.canonicalReportPrimaryAccountSeries("2026-10",listOf("pay"),count=0))
    }
}
