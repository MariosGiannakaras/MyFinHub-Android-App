package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web Reports reportInsightModel: previous full month, three historical months. */
class WebReportInsightSignalsParityTest {
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
        {"seed":{"transactions":[],"recurring":[
          {"id":"sub","name":"Monthly","amount":90,"active":true},
          {"id":"paused","name":"Paused","amount":200,"active":false,"status":"paused"}
        ]},"state":{"settings":{},"events":[
          {"id":"old-exp","kind":"expense","date":"2026-07-10",
            "amount":200,"category":"Τρόφιμα"},
          {"id":"aug-exp","kind":"expense","date":"2026-08-10",
            "amount":300,"category":"Τρόφιμα"},
          {"id":"sep-income","kind":"income","date":"2026-09-05",
            "amount":800},
          {"id":"sep-exp","kind":"expense","date":"2026-09-09",
            "amount":100,"category":"Τρόφιμα","subcategory":"Σούπερ"},
          {"id":"sep-saving","kind":"saving_cash_offset","date":"2026-09-10",
            "amount":50},
          {"id":"income","kind":"income","date":"2026-10-02",
            "amount":1000},
          {"id":"expense","kind":"expense","date":"2026-10-08",
            "amount":200,"category":"Τρόφιμα","subcategory":"Σούπερ"},
          {"id":"saving","kind":"saving_cash_offset","date":"2026-10-09",
            "amount":150},
          {"id":"future","kind":"expense","date":"2026-10-28",
            "amount":700,"category":"Άλλο"}
        ]}}
    """.trimIndent()).jsonObject)

    @Test fun monthVsFullPriorThreeMonthAverageAndRecurringBurden() {
        val before=doc.raw
        val row=doc.canonicalReportInsightSignals("2026-10","2026-10-19")
        assertEquals(0.25,row.incomeChange!!,0.0001)
        assertEquals(1.0,row.expenseChange!!,0.0001)
        assertEquals(2.0,row.savingChange!!,0.0001)
        assertEquals(800.0,row.netFlow,0.001)
        assertEquals(0.15,row.savingsRate!!,0.0001)
        assertEquals(0.0625,row.previousSavingsRate!!,0.0001)
        assertEquals(200.0,row.trailingExpenseAverage,0.001)
        assertEquals(0.0,row.expenseVsTrailingAverage!!,0.0001)
        assertEquals(90.0,row.recurringMonthlyTotal,0.001)
        assertEquals(0.09,row.recurringBurden!!,0.0001)
        assertTrue(row.sufficientExpenseHistory)
        assertEquals("Τρόφιμα › Σούπερ",row.topCategory!!.name)
        assertEquals(1.0,row.topCategory!!.share!!,0.0001)
        assertEquals(1.0,row.topCategory!!.change!!,0.0001)
        assertEquals(before,doc.raw)
    }

    @Test fun zeroBaselinesAndNoIncomeKeepUndefinedMetricsNull() {
        val empty=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"recurring":[]},"state":{"settings":{},"events":[]}}
        """.trimIndent()).jsonObject)
        val row=empty.canonicalReportInsightSignals("2026-01","2026-01-12")
        assertEquals(0.0,row.incomeChange!!,0.0)
        assertEquals(0.0,row.expenseChange!!,0.0)
        assertEquals(0.0,row.savingChange!!,0.0)
        assertNull(row.savingsRate)
        assertNull(row.previousSavingsRate)
        assertNull(row.expenseVsTrailingAverage)
        assertNull(row.recurringBurden)
        assertNull(row.topCategory)
        assertEquals(0.0,row.trailingExpenseAverage,0.0)
        assertTrue(!row.sufficientExpenseHistory)
        assertNull(doc.canonicalReportInsightSignals("2026-10","2026-10-01")
            .topCategory?.takeIf { it.name == "Άλλο" })
    }
}
