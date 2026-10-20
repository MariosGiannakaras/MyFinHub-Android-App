package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web reports.ts operationalReportSnapshot, excluding any UI-specific Home total. */
class WebOperationalReportSnapshotParityTest {
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
        {"seed":{
          "accounts":[{"id":"cash","kind":"cash"},{"id":"bank","kind":"bank"}],
          "snapshots":[{"date":"2026-10-01","balances":{"cash":50,"bank":400}}],
          "transactions":[],
          "recurring":[{"id":"internet","name":"Internet","amount":35,
             "day":10,"active":true,"accountId":"bank"}],
          "lending":[{"person":"Ανδρέας","outstanding":100}]},
        "state":{"settings":{"monthlyBudget":500},"events":[
          {"id":"food","kind":"expense","date":"2026-10-08",
             "amount":120,"category":"Τρόφιμα",
             "legs":[{"accountId":"cash","amount":-120}]},
          {"id":"saving","kind":"saving_cash_offset","date":"2026-10-12",
             "amount":20,"savingAmount":20,"savingSource":"cash_offset"},
          {"id":"lend","kind":"lend","date":"2026-10-13","amount":50,
             "person":"Ανδρέας","receivableDelta":50},
          {"id":"future","kind":"expense","date":"2026-10-30",
             "amount":300,"legs":[{"accountId":"bank","amount":-300}]}
        ]}}
    """.trimIndent()).jsonObject)

    @Test fun cutoffKeepsFutureOperationsOutOfBudgetAndAccountReport() {
        val before=doc.raw
        val report=doc.canonicalOperationalReportSnapshot("2026-10","2026-10-20")
        assertEquals("2026-10-20",report.cutoffDateIso)
        assertEquals(120.0,report.flow.expense,0.001)
        assertEquals(380.0,report.budgetRemaining,0.001)
        assertEquals(500.0,report.budget,0.001)
        assertEquals(150.0,report.receivables,0.001)
        assertEquals(35.0,report.recurringMonthlyTotal,0.001)
        assertEquals(20.0,report.savings.total,0.001)
        assertEquals(400.0,report.balances["bank"]!!,0.001)
        assertEquals(before,doc.raw)
    }

    @Test fun previousMonthIsFullAndDefaultCutoffIsMonthEnd() {
        val report=doc.canonicalOperationalReportSnapshot("2026-10")
        assertEquals("2026-10-31",report.cutoffDateIso)
        assertEquals(420.0,report.flow.expense,0.001)
        assertEquals(80.0,report.budgetRemaining,0.001)
        assertTrue(report.previous.expense>=0.0)
        assertEquals(100.0,report.balances["bank"]!!,0.001)
    }
}
