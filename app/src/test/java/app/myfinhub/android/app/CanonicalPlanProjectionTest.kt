package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.feature.plan.PlannedKind
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
        assertEquals(200.0, state.forecastObligations, 0.001)
        assertEquals(300.0, state.forecastExpectedIncome, 0.001)
        assertEquals(-50.0, state.forecastTransferImpact, 0.001)
        assertEquals(1_050.0, state.forecastEndBalance, 0.001)
        assertEquals("800", state.budget.monthlyLimitText)
        assertEquals(0.0, state.budgetSpent, 0.001)
        assertTrue(state.forecastEndDateLabel.contains("2026"))
    }

    @Test
    fun similarScheduledAndRecurringRent_remainDistinctWithoutCanonicalLink() {
        val state = projectCanonicalPlanState(
            document = planFixture(),
            today = LocalDate.of(2026, 9, 10),
            previous = null,
        )

        val rents = state.items.filter { it.title == "Ενοίκιο" }

        assertEquals(2, rents.size)
        assertEquals(setOf(PlannedKind.SCHEDULED, PlannedKind.RECURRING), rents.map { it.kind }.toSet())
        assertTrue(rents.all { !it.dueLabel.contains("2026-09-12") })
        assertTrue(rents.all { it.dueDateIso == "2026-09-12" })
    }

    @Test
    fun forecastIncludesAllPendingItemsBeforeApplyingHorizon() {
        val scheduled = (1..25).joinToString(",") { index ->
            """{"id":"expense-$index","dueDate":"2026-09-12","kind":"expense","amount":10,"note":"Bill $index","accountId":"bank","status":"pending"}"""
        } + """,{"id":"future","dueDate":"2026-12-01","kind":"income","amount":900,"status":"pending"},
            {"id":"paid","dueDate":"2026-09-12","kind":"expense","amount":800,"status":"paid"}"""
        val state = projectCanonicalPlanState(withPlanRows(scheduled, ""), LocalDate.of(2026, 9, 10), null)

        assertEquals(26, state.items.size)
        assertEquals(250.0, state.forecastObligations, 0.001)
        assertEquals(0.0, state.forecastExpectedIncome, 0.001)
        assertEquals(750.0, state.forecastEndBalance, 0.001)
    }

    @Test
    fun sameTitleAmountAndDateAcrossAccounts_doNotSuppressObligations() {
        val scheduled = """{"id":"scheduled","dueDate":"2026-09-12","kind":"expense","amount":100,"note":"Ενοίκιο","accountId":"bank","status":"pending"}"""
        val recurring = """{"id":"rec-one","name":"Ενοίκιο","amount":100,"firstExpectedDate":"2026-09-12","accountId":"savings","active":true},
            {"id":"rec-two","name":"Ενοίκιο","amount":100,"firstExpectedDate":"2026-09-12","accountId":"bank","active":true}"""
        val state = projectCanonicalPlanState(withPlanRows(scheduled, recurring), LocalDate.of(2026, 9, 10), null)

        assertEquals(setOf("scheduled", "rec-one", "rec-two"), state.items.map { it.id }.toSet())
        assertEquals(300.0, state.forecastObligations, 0.001)
    }

    private fun withPlanRows(scheduled: String, recurring: String): CanonicalFinanceDocument {
        val base = planFixture()
        return CanonicalFinanceDocument(JsonObject(base.raw + mapOf(
            "seed" to JsonObject(base.seed + ("recurring" to Json.parseToJsonElement("[$recurring]"))),
            "state" to JsonObject(base.state + ("scheduled" to Json.parseToJsonElement("[$scheduled]"))),
        )))
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
