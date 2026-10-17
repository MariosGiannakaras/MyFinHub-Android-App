package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Web domain.ts netWorth must preserve signed legacy/event receivables. */
class WebSignedLegacyReceivableParityTest {
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
       {"seed":{
         "accounts":[{"id":"payroll","kind":"bank"}],
         "snapshots":[{"date":"2026-10-01","balances":{"payroll":100}}],
         "lending":[{"person":"Ελένη","outstanding":30,"entries":[]}],
         "transactions":[],"loans":[]
       },"state":{"events":[
         {"id":"historical-overpayment","kind":"repayment","date":"2026-10-10",
          "amount":60,"person":"Ελένη","receivableDelta":-60,
          "legs":[{"accountId":"payroll","amount":60}]},
         {"id":"future-credit","kind":"lending","date":"2026-11-05",
          "amount":40,"person":"Ελένη","receivableDelta":40,
          "legs":[{"accountId":"payroll","amount":-40}]},
         {"id":"undated-adjustment","kind":"repayment","person":"Ελένη",
          "amount":5,"receivableDelta":-5,"legs":[]}
       ],"settings":{}}}
    """.trimIndent()).jsonObject)

    @Test fun overpaidHistoricalReceivableRemainsNegativeInDateScopedNetWorth() {
        // Web: seed 30 + dated repayment -60 = -30, not a fabricated zero.
        assertEquals(-30.0,doc.receivableOutstanding("2026-10-17"),0.001)
        assertEquals(-30.0,doc.canonicalLendingPersonRows("2026-10-17")
            .single().outstanding,0.001)
        assertEquals(160.0,doc.accountBalances("2026-10-17")["payroll"]!!,0.001)
    }

    @Test fun asOfExcludesFutureAndUndatedDeltasWhileAllTimeRemainsSigned() {
        assertEquals(30.0,doc.receivableOutstanding("2026-10-01"),0.001)
        assertEquals(-30.0,doc.receivableOutstanding("2026-10-17"),0.001)
        assertEquals(10.0,doc.receivableOutstanding("2026-11-30"),0.001)
        assertEquals(5.0,doc.receivableOutstanding(),0.001)
    }
}
