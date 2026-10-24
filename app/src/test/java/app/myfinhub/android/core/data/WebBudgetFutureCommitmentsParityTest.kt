package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web budgets.ts explicit commitments, including months beyond the next due. */
class WebBudgetFutureCommitmentsParityTest {
    private val document = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[]},"state":{
        "recurringCustom":[
          {"id":"fixed","name":"ΔΕΗ","amount":40,"day":15,
           "accountId":"cash","category":"Σπίτι","active":true,
           "recurrenceUnit":"month","recurrenceInterval":1},
          {"id":"variable","name":"Νερό","amount":25,"day":18,
           "accountId":"cash","category":"Σπίτι","active":true,
           "amountMode":"variable"}
        ],
        "scheduled":[
          {"id":"scheduled","kind":"expense","status":"pending",
           "dueDate":"2026-12-15","note":"ΔΕΗ",
           "accountId":"cash","category":"Σπίτι","amount":40}
        ],
        "budgets":[
          {"id":"home-dec","month":"2026-12","scope":"category",
           "category":"Σπίτι","amount":200}
        ]
      }}
    """.trimIndent()).jsonObject)

    @Test fun forecastFutureBudgetMonthEvenWhenAnotherRecurringCycleIsCloser() {
        val commitments = document.canonicalBudgetProjectedCommitments("2026-12", "2026-10-24")
        assertEquals(listOf("scheduled", "fixed"), commitments.map { it.id })
        assertTrue(commitments.all { it.date == "2026-12-15" })
        assertEquals(listOf("scheduled", "recurring"), commitments.map { it.source })
        assertEquals(80.0, commitments.sumOf { it.amount }, 0.001)
    }

    @Test fun variableEstimatesAreNotBookedAndIndependentScheduledRowsAreNotDeduplicated() {
        val current = document.canonicalBudgetProjectedCommitments("2026-12", "2026-12-01")
        assertEquals(2, current.size)
        assertTrue(current.none { it.id == "variable" })
        val budget = document.webBudgetProgress("2026-12", "2026-10-24")
            .single { it.id == "home-dec" }
        assertEquals(80.0, budget.projectedDue, 0.001)
        assertEquals(80.0, budget.projectedUsed, 0.001)
        assertEquals(120.0, budget.projectedRemaining, 0.001)
    }
}
