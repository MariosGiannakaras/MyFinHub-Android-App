package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web reports.ts reportExpenseCounterparties month grouping and share goldens. */
class WebReportExpenseCounterpartiesParityTest {
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[{"id":"payroll","kind":"bank"}],
        "transactions":[
          {"id":"legacy-a","type":"expense","date":"2026-10-04",
           "amount":30,"note":"MARKET","category":"Τρόφιμα"},
          {"id":"legacy-refund","type":"expense","date":"2026-10-06",
           "amount":40,"note":"MARKET","category":"Τρόφιμα"},
          {"id":"legacy-old","type":"expense","date":"2026-09-02",
           "amount":999,"note":"Old","category":"Άλλο"}
        ]},
       "state":{"reviewDecisions":{
          "legacy-refund":{"status":"confirmed","semanticKind":"refund"}
        },"events":[
          {"id":"event-a","kind":"expense","date":"2026-10-07",
           "amount":20,"note":"market","category":"Τρόφιμα"},
          {"id":"credit","kind":"card_purchase","date":"2026-10-08",
           "amount":15,"note":"Cafe","category":"Έξοδος"},
          {"id":"future","kind":"expense","date":"2026-10-28",
           "amount":100,"note":"Future","category":"Άλλο"},
          {"id":"transfer","kind":"transfer","date":"2026-10-08",
           "amount":200,"note":"Internal","category":"Άλλο"}
        ],"settings":{}}}
    """.trimIndent()).jsonObject)

    @Test fun groupsCaseFoldedMerchantAndExcludesRefundTransfersAndFuture() {
        val before=doc.raw
        val rows=doc.canonicalReportExpenseCounterparties("2026-10","2026-10-15")
        assertEquals(2,rows.size)
        assertEquals(listOf("MARKET","Cafe"),rows.map { it.title })
        assertEquals(listOf(50.0,15.0),rows.map { it.amount })
        assertEquals(2,rows[0].count)
        assertEquals("2026-10-07",rows[0].lastDate)
        assertEquals(50.0/65.0,rows[0].share!!,0.001)
        assertEquals(15.0/65.0,rows[1].share!!,0.001)
        assertEquals(before,doc.raw)
    }

    @Test fun webSplitRowsPreserveExpensePartDisplayCategoryAndAllPartExpenseFlow() {
        val splitDoc=CanonicalFinanceDocument(Json.parseToJsonElement("""
        {"seed":{},"state":{"settings":{},"events":[
          {"id":"s1","kind":"split","date":"2026-10-10",
           "note":"Mixed receipt","amount":100,
           "parts":[{"id":"a","kind":"expense","category":"Τρόφιμα",
                     "subcategory":"Φρούτα","amount":70},
                    {"id":"b","kind":"refund","category":"Άλλο","amount":30}]},
          {"id":"s2","kind":"split","date":"2026-10-11",
           "note":"Two expense categories","amount":80,
           "parts":[{"id":"a","kind":"expense","category":"Τρόφιμα","amount":50},
                    {"id":"b","kind":"expense","category":"Έξοδος","amount":30}]}
        ]}}
        """.trimIndent()).jsonObject)
        val rows=splitDoc.canonicalReportExpenseCounterparties("2026-10")
        assertEquals("Mixed receipt",rows[0].title)
        assertEquals("Τρόφιμα › Φρούτα",rows[0].category)
        assertEquals("Διαχωρισμένη συναλλαγή",rows[1].category)
        assertEquals(80.0,rows[1].amount,0.001)
        // Web eventFlowImpact(split) sums the parts for counterparty ranking.
        assertEquals(100.0,rows[0].amount,0.001)
    }

    @Test fun sourceSharesAreCalculatedBeforeTopNClamping() {
        val rows=doc.canonicalReportExpenseCounterparties("2026-10","2026-10-15",1)
        assertEquals(1,rows.size)
        assertTrue(rows[0].share!! < 1.0)
        assertEquals(0,doc.canonicalReportExpenseCounterparties("2026-10","2026-10-15",0).size)
    }
}
