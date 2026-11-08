package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalAccount
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
    fun scheduledManageRowsExposeHistoryWhileForecastCountsOnlyPending() {
        val records = """{"id":"completed","dueDate":"2026-09-04","kind":"expense",
            "amount":25,"accountId":"bank","status":"completed","completedEventId":"evt-1"},
            {"id":"cancelled","dueDate":"2026-09-02","kind":"expense",
            "amount":35,"accountId":"bank","status":"cancelled"},
            {"id":"due","dueDate":"2026-09-08","kind":"expense",
            "amount":40,"accountId":"bank","status":"pending"}"""
        val state = projectCanonicalPlanState(withPlanRows(records,""),
            LocalDate.of(2026,9,10),null)
        assertEquals(setOf("completed","cancelled","due"),
            state.scheduledManageItems.map { it.id }.toSet())
        assertEquals("completed",state.scheduledManageItems.first { it.id=="completed" }.status)
        assertEquals("cancelled",state.scheduledManageItems.first { it.id=="cancelled" }.status)
        assertEquals("due",state.scheduledManageItems.first { it.id=="due" }.status)
        assertEquals(listOf("due"),state.items.map { it.id })
        assertEquals(40.0,state.forecastObligations,0.001)
    }

    @Test
    fun sameTitleAmountAndDateAcrossAccounts_doNotSuppressObligations() {
        val scheduled = """{"id":"scheduled","dueDate":"2026-09-12","kind":"expense","amount":100,"note":"Ενοίκιο","accountId":"bank","status":"pending"}"""
        val recurring = """{"id":"rec-one","name":"Ενοίκιο","amount":100,"firstExpectedDate":"2026-09-12","accountId":"savings","active":true},
            {"id":"rec-two","name":"Ενοίκιο","amount":100,"firstExpectedDate":"2026-09-12","accountId":"bank","active":true}"""
        val state = projectCanonicalPlanState(withPlanRows(scheduled, recurring), LocalDate.of(2026, 9, 10), null)

        assertEquals(setOf("scheduled", "rec-one", "rec-two"), state.items.map { it.id }.toSet())
        // The Web portfolio has all three physical obligations, but spendable
        // cash excludes the savings-funded one: do not count it twice in cash.
        assertEquals(200.0, state.forecastObligations, 0.001)
        assertEquals(800.0, state.forecastEndBalance, 0.001)
        assertEquals(3, state.forecastWindows.first().sourceCounts["scheduled"]!!.plus(
            state.forecastWindows.first().sourceCounts["recurring"] ?: 0))
        assertEquals(1_200.0, state.forecastWindows.first().projectedPortfolio, 0.001)
    }


    @Test
    fun spendableHeadlineExcludesUnresolvedAccountsButPreservesVisiblePendingSources() {
        val scheduled = """
          {"id":"orphan-expense","dueDate":"2026-09-12","kind":"expense",
           "amount":400,"note":"Μη διαθέσιμο έξοδο","accountId":"deleted","status":"pending"},
          {"id":"valid-expense","dueDate":"2026-09-13","kind":"expense",
           "amount":50,"note":"Έγκυρο έξοδο","accountId":"bank","status":"pending"}
        """.trimIndent()
        val recurring = """
          {"id":"orphan-subscription","name":"Μη διαθέσιμο πάγιο",
           "amount":20,"firstExpectedDate":"2026-09-14","accountId":"deleted","active":true}
        """.trimIndent()
        val document = withPlanRows(scheduled, recurring)
        val before = document.raw
        val projected = projectCanonicalPlanState(document, LocalDate.of(2026, 9, 10), null)
        assertEquals(3, projected.items.size)
        assertEquals(1_000.0, projected.forecastStartBalance, 0.001)
        assertEquals(0.0, projected.forecastExpectedIncome, 0.001)
        assertEquals(50.0, projected.forecastObligations, 0.001)
        assertEquals(950.0, projected.forecastEndBalance, 0.001)
        val window = projected.forecastWindows.first { it.days == 30 }
        assertEquals(1, window.sourceCounts["scheduled"])
        assertEquals(0, window.sourceCounts["recurring"])
        assertEquals(2, window.omittedSourceCount)
        assertTrue(window.omittedWarnings.any { it.contains("Μη διαθέσιμο έξοδο") })
        assertTrue(window.omittedWarnings.any { it.contains("Μη διαθέσιμο πάγιο") })
        assertEquals(1_450.0, window.projectedPortfolio, 0.001)
        assertEquals(before, document.raw)
    }

    @Test
    fun spendableHeadlineIncludesCanonicalLoansAndFutureEventsWithoutTreatingSavingsAsCash() {
        val scheduled = """
          {"id":"income","dueDate":"2026-09-15","kind":"income",
           "amount":70,"note":"Μισθός","accountId":"bank","status":"pending"},
          {"id":"savings-to-bank","dueDate":"2026-09-17","kind":"transfer",
           "amount":50,"note":"Ανάληψη στόχου","fromAccountId":"savings",
           "toAccountId":"bank","status":"pending"}
        """.trimIndent()
        val recurring = """
          {"id":"subscription","name":"Συνδρομή","amount":20,
           "firstExpectedDate":"2026-09-11","accountId":"bank","active":true}
        """.trimIndent()
        val base = withPlanRows(scheduled, recurring)
        val loans = Json.parseToJsonElement("""
          [{"id":"loan","name":"Δόση","installment":40,"total":80,
            "installments":2,"day":"13","firstExpectedDate":"2026-09-13",
            "defaultAccountId":"bank"}]
        """.trimIndent())
        val events = Json.parseToJsonElement("""
          [{"id":"future","kind":"expense","date":"2026-09-18","amount":35,
            "note":"Μελλοντική δαπάνη","legs":[{"accountId":"bank","amount":-35}]}]
        """.trimIndent())
        val document = CanonicalFinanceDocument(JsonObject(base.raw + mapOf(
            "seed" to JsonObject(base.seed + ("loans" to loans)),
            "state" to JsonObject(base.state + ("events" to events)),
        )))
        val before = document.raw
        val projected = projectCanonicalPlanState(document, LocalDate.of(2026, 9, 10), null)
        assertEquals(1_000.0, projected.forecastStartBalance, 0.001)
        assertEquals(70.0, projected.forecastExpectedIncome, 0.001)
        assertEquals(95.0, projected.forecastObligations, 0.001)
        assertEquals(50.0, projected.forecastTransferImpact, 0.001)
        assertEquals(1_025.0, projected.forecastEndBalance, 0.001)
        val window = projected.forecastWindows.first { it.days == 30 }
        assertEquals(mapOf(
            "scheduled" to 2, "recurring" to 1,
            "loan" to 1, "dated_event" to 1,
        ), window.sourceCounts)
        assertEquals(5, window.knownMovements.size)
        assertEquals(1_475.0, window.projectedPortfolio, 0.001)
        assertEquals(0, window.omittedSourceCount)
        assertEquals(before, document.raw)
    }

    @Test
    fun planPaymentPickerExcludesGloballyDuplicatedCreditAndNonCreditAccountIds() {
        fun account(id: String, kind: String) = CanonicalAccount(
            id = id, name = id, shortName = null, kind = kind, excludeFromAvailable = false,
        )
        val options = canonicalPlanPaymentAccountOptions(listOf(
            account("payroll", "bank"), account("payroll", "bank"),
            account("collision", "cash"), account("collision", "credit"),
            account("savings", "savings"),
        ))
        assertEquals(listOf("savings"), options.map { it.id })
        assertTrue(options.none { it.id in listOf("payroll", "collision") })
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
