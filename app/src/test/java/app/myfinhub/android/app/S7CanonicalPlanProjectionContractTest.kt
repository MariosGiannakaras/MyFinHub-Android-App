package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.feature.plan.BudgetDraft
import app.myfinhub.android.feature.plan.PlanUiState
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class S7CanonicalPlanProjectionContractTest {
    @Test
    fun productionForecast_ignoresLegacyPreviewHorizonAndStaysThirtyDays() {
        val state = projectCanonicalPlanState(
            document = fixture(),
            today = LocalDate.of(2026, 9, 10),
            previous = PlanUiState(forecastHorizonDays = 90),
        )

        assertEquals(30, state.forecastHorizonDays)
        assertEquals("2026-09-10", state.forecastStartDateIso)
        assertEquals("2026-10-10", state.forecastEndDateIso)
        assertEquals(100.0, state.forecastObligations, 0.001)
    }

    @Test
    fun scheduledAndRecurringWithSameRawId_areBothRetained() {
        val state = projectCanonicalPlanState(
            document = fixture(),
            today = LocalDate.of(2026, 9, 10),
            previous = null,
        )
        assertEquals(2, state.items.count { it.id == "same" })
    }

    @Test
    fun canonicalBudget_replacesStalePreviousDraft() {
        val document = CanonicalFinanceDocument(
            Json.parseToJsonElement(
                """
                {
                  "schemaVersion":3,
                  "seed":{"accounts":[],"snapshots":[],"transactions":[],"recurring":[],"loans":[],"lending":[]},
                  "state":{
                    "settings":{"excludedFromAvailable":[],"accountNames":{}},
                    "events":[],"scheduled":[],"cards":[],
                    "budgets":[{"id":"budget-sep","month":"2026-09","scope":"overall","amount":950.0,"alertThreshold":85}]
                  }
                }
                """.trimIndent(),
            ).jsonObject,
        )
        val state = projectCanonicalPlanState(
            document = document,
            today = LocalDate.of(2026, 9, 14),
            previous = PlanUiState(budget = BudgetDraft("700", "75")),
        )
        assertEquals("950", state.budget.monthlyLimitText)
        assertEquals("85", state.budget.alertThresholdText)
    }

    private fun fixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(
        Json.parseToJsonElement(
            """
            {
              "schemaVersion":3,
              "seed":{
                "accounts":[{"id":"bank","name":"Κύριος","kind":"bank"}],
                "snapshots":[{"date":"2026-09-01","balances":{"bank":1000.0}}],
                "transactions":[],
                "recurring":[{"id":"same","name":"Internet","amount":40.0,"firstExpectedDate":"2026-09-12","active":true,"accountId":"bank"}],
                "loans":[],
                "lending":[]
              },
              "state":{
                "settings":{"excludedFromAvailable":[],"accountNames":{}},
                "events":[],
                "scheduled":[
                  {"id":"same","dueDate":"2026-09-12","kind":"expense","amount":60.0,"note":"Internet","accountId":"bank","status":"pending"},
                  {"id":"outside","dueDate":"2026-11-01","kind":"expense","amount":900.0,"accountId":"bank","status":"pending"}
                ],
                "cards":[],
                "budgets":[]
              }
            }
            """.trimIndent(),
        ).jsonObject,
    )
}
