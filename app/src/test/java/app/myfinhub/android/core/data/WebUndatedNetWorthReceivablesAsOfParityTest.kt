package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Web domain.ts eventReceivables: missing/null date is excluded from as-of net worth. */
class WebUndatedNetWorthReceivablesAsOfParityTest {
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"lending":[{"person":"Μαρία","outstanding":30,
          "entries":[]}]},
       "state":{"events":[
         {"id":"past","kind":"repayment","date":"2026-10-10",
          "person":"Μαρία","receivableDelta":-10},
         {"id":"missing","kind":"lending","person":"Μαρία",
          "receivableDelta":70},
         {"id":"null-date","kind":"lending","date":null,"person":"Μαρία",
          "receivableDelta":80},
         {"id":"future","kind":"lending","date":"2026-11-05",
          "person":"Μαρία","receivableDelta":50},
         {"id":"current","kind":"lending","date":"2026-10-17",
          "person":"Μαρία","receivableDelta":5}
       ]}}
    """.trimIndent()).jsonObject)

    @Test fun asOfNetWorthExcludesMissingNullAndFutureReceivableDeltas() {
        assertEquals(25.0,doc.receivableOutstanding("2026-10-17"),0.001)
        assertEquals(25.0,doc.lendingOutstandingForPerson("Μαρία","2026-10-17"),0.001)
        assertEquals(75.0,doc.receivableOutstanding("2026-12-31"),0.001)
    }

    @Test fun allTimeHistoryKeepsMissingDateDeltasAndLegacyBaseline() {
        assertEquals(225.0,doc.receivableOutstanding(),0.001)
        assertEquals(225.0,doc.lendingOutstandingForPerson("Μαρία"),0.001)
    }

    @Test fun timeScopedReceivablesDoNotFalselyTreatUndatedDebtAsPaid() {
        assertEquals(30.0,doc.receivableOutstanding("2026-10-01"),0.001)
        assertEquals(20.0,doc.receivableOutstanding("2026-10-16"),0.001)
    }
}
