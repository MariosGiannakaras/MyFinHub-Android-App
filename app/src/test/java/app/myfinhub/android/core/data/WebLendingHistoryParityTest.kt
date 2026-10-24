package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web src/lib/lending.ts golden historical rows, running balances and as-of behavior. */
class WebLendingHistoryParityTest {
    private val data = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{
        "lending":[
          {"person":" Άννα ","outstanding":400,"entries":[
            {"date":"2026-09-01","lent":100,"repaid":20,"haircut":10},
            {"date":"2026-09-01","lent":15},
            {"date":"09/02/2026","lent":999}
          ]}
        ]
      },"state":{"events":[
        {"id":"repay-anna","date":"2026-10-10","kind":"repayment","person":" Άννα ",
         "amount":40,"receivableDelta":-40,"toAccountId":"payroll"},
        {"id":"overpaid","date":"2026-10-11","kind":"repayment","person":"Άννα",
         "amount":300,"receivableDelta":-300},
        {"id":"lent-vasilis","date":"2026-10-12","kind":"lending","person":"Βασίλης",
         "amount":25,"fromAccountId":"daily","note":"Για λογαριασμό του"},
        {"id":"future","date":"2026-11-01","kind":"lending","person":"Βασίλης","amount":20},
        {"id":"invalid-date","date":"2026/10/09","kind":"lending","person":"Άννα","amount":20},
        {"id":"wrong-kind","date":"2026-10-14","kind":"expense","person":"Άννα","amount":70},
        {"id":"negative","date":"2026-10-14","kind":"repayment","person":"Άννα","amount":-5}
      ]}}
    """.trimIndent()).jsonObject)

    @Test fun legacyHistoryPreservesMultipleActionsAndSeparateSameDateEntries() {
        val anna = data.canonicalLendingHistoryRows("2026-09-30")
        assertEquals(4, anna.size)
        assertEquals(listOf(
            "legacy-Άννα-2026-09-01-1-lent",
            "legacy-Άννα-2026-09-01-0-repaid",
            "legacy-Άννα-2026-09-01-0-lent",
            "legacy-Άννα-2026-09-01-0-forgiven",
        ), anna.map { it.id })
        assertEquals(listOf(0.0, 100.0, 80.0, 95.0).sorted(),
            anna.map { it.runningOutstanding }.sorted())
        assertTrue(anna.all { it.origin == "legacy" && it.accountId == null })
        assertEquals("Χάρισμα / διαγραφή οφειλής", anna.first { it.action == "forgiven" }.note)
    }

    @Test fun linkedHistoryAppliesRepaymentFloorAndKeepsSeedAggregateIndependent() {
        val historical = data.canonicalLendingHistoryRows("2026-10-11")
        assertEquals("overpaid", historical.first().id)
        assertEquals(0.0, historical.first().runningOutstanding, 0.001)
        val repayment = historical.first { it.id == "repay-anna" }
        assertEquals("payroll", repayment.accountId)
        assertEquals("Επιστροφή δανεικών", repayment.note)
        assertEquals("event", repayment.origin)
        assertEquals(55.0, repayment.runningOutstanding, 0.001)
        val aggregate = data.canonicalLendingPersonRows("2026-10-11").single()
        assertEquals(60.0, aggregate.outstanding, 0.001)
        // Web lendingHistory derives its running amount from chronological movements,
        // whereas the separate lendingRows includes seed.outstanding=400.
    }

    @Test fun asOfFiltersFutureMalformedNonLendingAndNonPositiveHistoryEvents() {
        val rows = data.canonicalLendingHistoryRows("2026-10-12")
        assertEquals("lent-vasilis", rows.first().id)
        assertEquals("daily", rows.first().accountId)
        assertEquals("Για λογαριασμό του", rows.first().note)
        assertEquals(25.0, rows.first().runningOutstanding, 0.001)
        assertFalse(rows.any { it.id in setOf("future", "invalid-date", "wrong-kind", "negative") })
        assertEquals(7, rows.size)
        assertEquals(8, data.canonicalLendingHistoryRows().size)
    }

    @Test fun noHistoryIsSynthesizedFromAggregateOnlySeedBalances() {
        val aggregateOnly = CanonicalFinanceDocument(Json.parseToJsonElement("""
           {"seed":{"lending":[{"person":"Χρήστος","outstanding":95,"entries":[]}]},"state":{}}
        """.trimIndent()).jsonObject)
        assertTrue(aggregateOnly.canonicalLendingHistoryRows().isEmpty())
        assertEquals(95.0, aggregateOnly.canonicalLendingPersonRows().single().outstanding, 0.001)
    }
    @Test fun canonicalLendingWriteoffsAppearAsForgivenWithoutCashLegOrInventedPayments() {
        val history = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"lending":[{"person":"Μαρία","outstanding":80,"entries":[]}]},
           "state":{"events":[
             {"id":"lent","date":"2026-10-01","kind":"lending","person":"Μαρία",
               "amount":75,"fromAccountId":"cash"},
             {"id":"return","date":"2026-10-03","kind":"repayment","person":"Μαρία",
               "amount":20,"toAccountId":"payroll"},
             {"id":"forgive","date":"2026-10-05","kind":"lending_writeoff","person":"Μαρία",
               "amount":15,"accountId":"cash"},
             {"id":"larger","date":"2026-10-15","kind":"lending_writeoff","person":"Μαρία",
               "amount":100},
             {"id":"other-kind","date":"2026-10-07","kind":"expense","person":"Μαρία",
               "amount":20}
           ]}}
        """.trimIndent()).jsonObject)
        val before = history.raw
        val rows = history.canonicalLendingHistoryRows("2026-10-06")
        assertEquals(listOf("forgive", "return", "lent"), rows.map { it.id })
        val forgiveness = rows.first()
        assertEquals("forgiven", forgiveness.action)
        assertEquals("Διαγραφή απαίτησης", forgiveness.note)
        assertEquals(null, forgiveness.accountId)
        assertEquals("event", forgiveness.origin)
        assertEquals(40.0, forgiveness.runningOutstanding, 0.00001)
        assertEquals(55.0, rows[1].runningOutstanding, 0.00001)
        assertEquals(75.0, rows[2].runningOutstanding, 0.00001)
        val all = history.canonicalLendingHistoryRows()
        assertEquals(4, all.size)
        assertEquals(0.0, all.first().runningOutstanding, 0.00001)
        assertEquals(before, history.raw)
    }

}
