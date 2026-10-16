package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.flowBetween
import app.myfinhub.android.core.data.categoryTotalsBetween
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One Web signed finance flow feeds Insights, Activity filtering and ledger summaries. */
class WebActivityLedgerFlowParityTest {
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
       {"seed":{"accounts":[{"id":"cash","kind":"cash","name":"Μετρητά"}],
        "transactions":[
          {"id":"reviewed","type":"expense","date":"2026-10-03",
            "amount":40,"category":"Άλλο","accountId":"cash"},
          {"id":"legacy-exp","type":"expense","date":"2026-10-04",
            "amount":20,"category":"Τρόφιμα","accountId":"cash"}
        ],"snapshots":[{"date":"2026-10-01","balances":{"cash":1000}}]},
        "state":{"settings":{},
          "reviewDecisions":{"reviewed":{"status":"confirmed","semanticKind":"split","parts":[
            {"id":"part-exp","kind":"expense","amount":30,"category":"Τρόφιμα"},
            {"id":"part-ref","kind":"refund","amount":10,"category":"Τρόφιμα"}
          ]}},
          "events":[
            {"id":"salary","kind":"income","date":"2026-10-05",
              "amount":200,"category":"Μισθοδοσία"},
            {"id":"purchase","kind":"expense","date":"2026-10-06",
              "amount":100,"category":"Τρόφιμα"},
            {"id":"credit-purchase","kind":"card_purchase","date":"2026-10-07",
              "amount":20,"category":"Τρόφιμα"},
            {"id":"refund","kind":"refund","date":"2026-10-08",
              "amount":30,"category":"Τρόφιμα"},
            {"id":"payment","kind":"card_payment","date":"2026-10-09",
              "amount":40,"category":"Τρόφιμα"},
            {"id":"transfer","kind":"transfer","date":"2026-10-10",
              "amount":80,"category":"Τρόφιμα"},
            {"id":"cash-saving","kind":"saving_cash_offset","date":"2026-10-11",
              "amount":300,"savingAmount":0,"category":"Τρόφιμα"},
            {"id":"lending","kind":"lending","date":"2026-10-12",
              "amount":15,"category":"Τρόφιμα"}
          ]}}
    """.trimIndent()).jsonObject)

    @Test fun activityTotalsAndInsightsHaveSameSignedFinanceSemantics() {
        val projection=projectCanonicalProduct(doc,LocalDate.parse("2026-10-16"))
        val ledger=projection.activityState.copy(
            ledgerDateFrom="2026-10-01",ledgerDateTo="2026-10-16")
        assertEquals(200.0,ledger.visibleIncome,0.001)
        assertEquals(130.0,ledger.visibleExpense,0.001)
        assertEquals(70.0,ledger.visibleNet,0.001)
        assertEquals(doc.flowBetween("2026-10-01","2026-10-16").expense,ledger.visibleExpense,0.001)
        val pay=ledger.visibleItems.single { it.id=="payment" }
        assertEquals(0.0,pay.financeExpenseContribution!!,0.001)
        val refund=ledger.visibleItems.single { it.id=="refund" }
        assertEquals(-30.0,refund.financeExpenseContribution!!,0.001)
        assertEquals(130.0,
            doc.categoryTotalsBetween("2026-10-01","2026-10-16")["Τρόφιμα"]!!,0.001)
    }

    @Test fun categoryDrilldownExcludesTransfersDebtPaymentsAndUnattributedCategories() {
        val projection=projectCanonicalProduct(doc,LocalDate.parse("2026-10-16"))
        val scoped=projection.activityState.forCategory(
            "Τρόφιμα","2026-10-01","2026-10-16")
        assertEquals(setOf("reviewed","legacy-exp","purchase","credit-purchase","refund"),
            scoped.visibleItems.map { it.id }.toSet())
        assertEquals(130.0,scoped.visibleExpense,0.001)
        assertEquals(0.0,scoped.visibleIncome,0.001)
        assertEquals(-130.0,scoped.visibleNet,0.001)
        assertEquals(130.0,-scoped.visibleItems.sumOf { it.amount },0.001)
        assertFalse(scoped.visibleItems.any { it.canonicalKind=="card_payment" })
        assertTrue(projection.activityState.visibleItems.any { it.id=="payment" })
    }
}
