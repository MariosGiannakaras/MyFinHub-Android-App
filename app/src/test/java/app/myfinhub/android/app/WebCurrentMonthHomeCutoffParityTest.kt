package app.myfinhub.android.app

import java.time.LocalDate
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.monthlyFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Web Dashboard monthlyFlow(data, month, asOf) never counts future-dated events. */
class WebCurrentMonthHomeCutoffParityTest {
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{
        "accounts":[{"id":"payroll","name":"Salary","kind":"bank",
          "bankAccountCategory":"payroll","providerId":"piraeus"}],
        "snapshots":[{"date":"2026-10-01","balances":{"payroll":1000}}],
        "transactions":[],"recurring":[],"loans":[],"lending":[]
      },"state":{"events":[
        {"id":"expense-now","date":"2026-10-10","kind":"expense",
         "amount":30,"category":"Τρόφιμα","accountId":"payroll",
         "legs":[{"accountId":"payroll","amount":-30}]},
        {"id":"expense-future","date":"2026-10-25","kind":"expense",
         "amount":300,"category":"Σπίτι","accountId":"payroll",
         "legs":[{"accountId":"payroll","amount":-300}]},
        {"id":"income-future","date":"2026-10-27","kind":"income",
         "amount":200,"category":"Μισθός","accountId":"payroll",
         "legs":[{"accountId":"payroll","amount":200}]}
      ],"settings":{}}}
    """.trimIndent()).jsonObject)

    @Test fun currentHomeMonthExcludesFutureIncomeAndExpensesWithoutHidingLedgerHistory() {
        val today = LocalDate.parse("2026-10-17")
        val p = projectCanonicalProduct(doc, today)
        assertEquals(0.0, p.homeState.monthFlow.income, 0.001)
        assertEquals(30.0, p.homeState.monthFlow.expense, 0.001)
        assertEquals(970.0, p.homeState.accounts.single().balance, 0.001)
        assertEquals(30.0,p.planState.budgetSpent,0.001)
        assertEquals(3, p.activityState.items.size)
        val fullMonth = doc.monthlyFlow("2026-10")
        assertEquals(200.0,fullMonth.income,0.001)
        assertEquals(330.0,fullMonth.expense,0.001)
    }
}
