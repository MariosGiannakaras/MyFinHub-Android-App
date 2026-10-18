package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.webBudgetProgress
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web budgetProgress: visible category budgets must preserve raw negative refunds. */
class WebCategoryBudgetProjectionParityTest {
    private val date=LocalDate.parse("2026-10-15")
    private val document=CanonicalFinanceDocument(Json.parseToJsonElement("""
     {"seed":{"accounts":[{"id":"bank","kind":"bank"}],
       "transactions":[]},
      "state":{"events":[
       {"id":"refund","kind":"refund","date":"2026-10-07","category":"Εστιατόρια","amount":30},
       {"id":"expense","kind":"expense","date":"2026-10-10","category":"Τρόφιμα","amount":175}
      ],"budgets":[
       {"id":"refund-budget","month":"2026-10","scope":"category",
        "category":"Εστιατόρια","amount":50,"alertThreshold":0.75},
       {"id":"food-budget","month":"2026-10","scope":"category",
        "category":"Τρόφιμα","amount":150,"alertThreshold":0.80},
       {"id":"overall-budget","month":"2026-10","scope":"overall",
        "amount":500,"alertThreshold":0.80}
      ],"settings":{}}}
    """.trimIndent()).jsonObject)

    @Test fun UIProjectionKeepsWebRawNetClampedUseRemainingStatusAndIdentity() {
        val before=document.raw
        val expected=document.webBudgetProgress("2026-10",date.toString())
            .filter { it.scope=="category" }
        val state=projectCanonicalPlanState(document,date,null)
        assertEquals(expected.map { it.id },state.categoryBudgets.map { it.id })
        expected.zip(state.categoryBudgets).forEach { (raw,ui) ->
            assertEquals(raw.category,ui.name)
            assertEquals(raw.rawUsed,ui.rawSpent,0.001)
            assertEquals(raw.used,ui.spent,0.001)
            assertEquals(raw.remaining,ui.remaining,0.001)
            assertEquals(raw.status,ui.status)
        }
        val refund=state.categoryBudgets.first { it.id=="refund-budget" }
        assertEquals(-30.0,refund.rawSpent,0.001)
        assertEquals(0.0,refund.spent,0.001)
        assertEquals(50.0,refund.remaining,0.001)
        assertEquals("ok",refund.status)
        assertEquals("exceeded",state.categoryBudgets.first { it.id=="food-budget" }.status)
        assertEquals(before,document.raw)
    }

    @Test fun historicalOrFutureBudgetsNeverAppearInSelectedMonth() {
        val rows=projectCanonicalPlanState(document,date,null).categoryBudgets
        assertEquals(2,rows.size)
        assertTrue(rows.none { it.id=="overall-budget" })
    }
}
