package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web operationalReportSnapshot uses existing as-of ledger and report sources. */
class WebOperationalReportSnapshotParityTest {
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[{"id":"payroll","name":"Μισθοδοσία","kind":"bank"},
                            {"id":"cash","name":"Μετρητά","kind":"cash"}],
        "snapshots":[{"date":"2026-10-01","balances":{"payroll":1000,"cash":200}}],
        "transactions":[],
        "lending":[{"person":"Φίλος","outstanding":80}],
        "recurring":[{"id":"internet","name":"Internet","amount":30,"day":15,"active":true}]},
       "state":{"settings":{"monthlyBudget":500},"events":[
         {"id":"p","date":"2026-09-12","kind":"expense","amount":50,"category":"Τρόφιμα"},
         {"id":"a","date":"2026-10-09","kind":"income","amount":100,
          "legs":[{"accountId":"payroll","amount":100}]},
         {"id":"b","date":"2026-10-11","kind":"expense","amount":140,
          "legs":[{"accountId":"payroll","amount":-140}]},
         {"id":"saving","date":"2026-10-14","kind":"saving_cash_offset","amount":20,
          "savingAmount":20,"savingSource":"cash_offset"},
         {"id":"future","date":"2026-10-28","kind":"expense","amount":900,
          "legs":[{"accountId":"cash","amount":-900}]}
       ]}}
    """.trimIndent()).jsonObject)

    @Test fun exactAsOfCanonicalSubsystemsAndHistoryRemainUnmodified() {
        val original=doc.raw
        val month="2026-10"
        val cutoff="2026-10-20"
        val row=doc.canonicalOperationalReportSnapshot(month,cutoff)
        assertEquals(doc.canonicalOperationalMonthlyFlow(month,cutoff),row.flow)
        assertEquals(doc.canonicalOperationalMonthlyFlow("2026-09"),row.previous)
        assertEquals(doc.accountBalances(cutoff),row.balances)
        assertEquals(doc.canonicalReportCreditPortfolio(cutoff).debt,row.creditDebt,0.001)
        assertEquals(doc.canonicalReportCreditPortfolio(cutoff).activeCards,row.creditCards)
        assertEquals(doc.canonicalLendingPersonRows(cutoff).sumOf { it.outstanding },
            row.receivables,0.001)
        assertEquals(doc.canonicalRecurringMonthlyTotal(),row.recurring,0.001)
        assertEquals(doc.canonicalSavingsBreakdown(month,cutoff),row.savings)
        assertEquals(500.0,row.budget,0.001)
        assertEquals(360.0,row.budgetRemaining,0.001)
        assertEquals(100.0,row.flow.income,0.001)
        assertEquals(140.0,row.flow.expense,0.001)
        assertEquals(960.0,row.balances["payroll"]!!,0.001)
        assertEquals(200.0,row.balances["cash"]!!,0.001)
        assertEquals(original,doc.raw)
    }

    @Test fun unrelatedAsOfMonthCannotTruncateEarlierReportingMonth() {
        val row=doc.canonicalOperationalReportSnapshot("2026-09","2026-10-20")
        assertEquals(doc.canonicalOperationalMonthlyFlow("2026-09"),row.flow)
        assertEquals(doc.accountBalances("2026-09-30"),row.balances)
        assertEquals(50.0,row.flow.expense,0.001)
        assertTrue(row.creditCardRows.isEmpty())
    }
}
