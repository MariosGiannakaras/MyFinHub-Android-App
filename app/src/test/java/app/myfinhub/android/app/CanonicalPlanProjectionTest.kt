package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.feature.plan.PlannedKind
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalPlanProjectionTest {
    @Test
    fun forecast_reconcilesCurrentPositionObligationsIncomeAndTransferImpact() {
        val state = projectCanonicalPlanState(
            document = planFixture(),
            today = LocalDate.of(2026, 9, 10),
            previous = null,
        )

        assertEquals(1_000.0, state.forecastStartBalance, 0.001)
        assertEquals(100.0, state.forecastObligations, 0.001)
        assertEquals(300.0, state.forecastExpectedIncome, 0.001)
        assertEquals(-50.0, state.forecastTransferImpact, 0.001)
        assertEquals(1_150.0, state.forecastEndBalance, 0.001)
        assertEquals("800", state.budget.monthlyLimitText)
        assertEquals(0.0, state.budgetSpent, 0.001)
        assertTrue(state.forecastEndDateLabel.contains("2026"))
    }

    @Test
    fun equivalentScheduledRent_collapsesRecurringProjectionWithoutRawIsoDate() {
        val state = projectCanonicalPlanState(
            document = planFixture(),
            today = LocalDate.of(2026, 9, 10),
            previous = null,
        )

        val rents = state.items.filter { it.title == "Ενοίκιο" }

        assertEquals(1, rents.size)
        assertEquals(PlannedKind.SCHEDULED, rents.single().kind)
        assertFalse(rents.single().dueLabel.contains("2026-09-12"))
        assertEquals("2026-09-12", rents.single().dueDateIso)
    }

    private fun planFixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(
        Json.parseToJsonElement(
            """
            {
              "schemaVersion":3,
              "seed":{
                "accounts":[
                  {"id":"bank","name":"Κύριος","kind":"bank"},
                  {"id":"savings","name":"Αποταμίευση","kind":"savings","excludeFromAvailable":true}
                ],
                "snapshots":[
                  {"date":"2026-09-01","balances":{"bank":1000.0,"savings":500.0}}
                ],
                "transactions":[],
                "recurring":[
                  {"id":"rec-rent","name":"Ενοίκιο","amount":100.0,"firstExpectedDate":"2026-09-12","active":true,"category":"Στέγαση","accountId":"bank"}
                ],
                "loans":[],
                "lending":[]
              },
              "state":{
                "settings":{"excludedFromAvailable":[],"accountNames":{}},
                "events":[],
                "scheduled":[
                  {"id":"scheduled-rent","dueDate":"2026-09-12","kind":"expense","amount":100.0,"note":"Ενοίκιο","category":"Στέγαση","accountId":"bank","status":"pending"},
                  {"id":"scheduled-income","dueDate":"2026-09-15","kind":"income","amount":300.0,"note":"Μισθός","category":"Μισθός","accountId":"bank","status":"pending"},
                  {"id":"scheduled-transfer","dueDate":"2026-09-18","kind":"transfer","amount":50.0,"note":"Αποταμίευση","fromAccountId":"bank","toAccountId":"savings","status":"pending"}
                ],
                "cards":[],
                "budgets":[
                  {"id":"budget-sep","month":"2026-09","scope":"overall","amount":800.0,"alertThreshold":80}
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject,
    )
}
