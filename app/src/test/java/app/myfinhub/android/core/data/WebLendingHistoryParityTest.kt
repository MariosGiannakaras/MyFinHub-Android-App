package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web lending.ts lendingHistory: legacy split rows + linked dated financial events. */
class WebLendingHistoryParityTest {
    private val document = CanonicalFinanceDocument(Json.parseToJsonElement("""
        {"seed":{
          "accounts":[{"id":"cash","kind":"cash"},{"id":"salary","kind":"bank"}],
          "snapshots":[{"date":"2026-10-01","balances":{"cash":400,"salary":500}}],
          "lending":[
            {"person":" Άννα ","outstanding":65,"entries":[
              {"date":"2026-10-01","lent":100},
              {"date":"2026-10-02","repaid":20},
              {"date":"2026-10-03","haircut":10},
              {"date":"not-a-date","lent":400},
              {"date":"2026-11-01","lent":100}
            ]}
          ],
          "transactions":[]
        },"state":{"events":[
          {"id":"evt-lent","date":"2026-10-04","kind":"lending","person":" Άννα ",
           "amount":25,"fromAccountId":"cash","note":"Δανεικά","receivableDelta":25},
          {"id":"evt-repaid","date":"2026-10-05","kind":"repayment","person":"Άννα",
           "amount":150,"toAccountId":"salary","note":"","receivableDelta":-150},
          {"id":"ignored-income","date":"2026-10-05","kind":"income","person":"Άννα","amount":999},
          {"id":"undated","kind":"repayment","person":"Άννα","amount":20},
          {"id":"future","date":"2026-11-06","kind":"lending","person":"Άννα","amount":35},
          {"id":"zero","date":"2026-10-06","kind":"repayment","person":"Άννα","amount":0},
          {"id":"another-person","date":"2026-10-06","kind":"lending",
           "person":"Μαρία","amount":40}
        ],"settings":{}}}
    """.trimIndent()).jsonObject)

    @Test fun readOnlyHistoryPreservesLegacyAndNewEventActionsInWebOrder() {
        val history = document.canonicalLendingHistory("2026-10-17")
        assertEquals(6, history.size)
        assertEquals(listOf(
            "another-person", "evt-repaid", "evt-lent",
            "legacy-Άννα-2026-10-03-2-forgiven",
            "legacy-Άννα-2026-10-02-1-repaid",
            "legacy-Άννα-2026-10-01-0-lent",
        ), history.map { it.id })
        assertEquals(listOf(40.0, 0.0, 95.0, 70.0, 80.0, 100.0),
            history.map { it.runningOutstanding })
        assertEquals("salary", history.single { it.id == "evt-repaid" }.accountId)
        assertEquals("cash", history.single { it.id == "evt-lent" }.accountId)
        assertEquals("Επιστροφή δανεικών", history.single { it.id == "evt-repaid" }.note)
        assertEquals("Πλήρωσα για άλλον", history.single { it.id == "another-person" }.note)
        assertEquals("legacy", history.single { it.action == "forgiven" }.origin)
        assertEquals("event", history.single { it.id == "evt-lent" }.origin)
        assertTrue(history.none { it.id in setOf("ignored-income", "undated", "zero", "future") })
    }

    @Test fun historyHasIndependentRunningLedgerNotInflatedByImportedReceivableBaseline() {
        val history = document.canonicalLendingHistory("2026-10-03")
        assertEquals(listOf("forgiven", "repaid", "lent"), history.map { it.action })
        assertEquals(listOf(70.0, 80.0, 100.0), history.map { it.runningOutstanding })
        // Web lendingRows includes aggregate seed outstanding and dated receivableDelta,
        // while lendingHistory reconstructs only dated movement lines.
        assertEquals(-60.0,
            document.canonicalLendingPersonRows("2026-10-17")
                .first { it.person == "Άννα" }.outstanding, 0.001)
        assertFalse(history.any { it.origin == "event" })
    }

    @Test fun historicalDuplicateIdentifiersRemainDistinctRowsWithoutMutation() {
        val duplicated = CanonicalFinanceDocument(Json.parseToJsonElement("""
            {"seed":{"lending":[
              {"person":"Νίκος","outstanding":10,"entries":[
                {"date":"2026-01-02","lent":10}
              ]},
              {"person":"Νίκος","outstanding":20,"entries":[
                {"date":"2026-01-02","lent":20}
              ]}
            ]},"state":{"events":[]}}
        """.trimIndent()).jsonObject)
        val history = duplicated.canonicalLendingHistory("2026-10-17")
        assertEquals(2, history.size)
        assertEquals(1, history.map { it.id }.distinct().size)
        assertEquals(listOf(10.0, 30.0), history.map { it.runningOutstanding })
        assertEquals(30.0, duplicated.canonicalLendingPersonRows("2026-10-17").single().outstanding, 0.001)
    }
}
