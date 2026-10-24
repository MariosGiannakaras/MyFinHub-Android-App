package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.allCanonicalRecurringItems
import app.myfinhub.android.core.data.canonicalRecurringMonthlyTotal
import app.myfinhub.android.core.data.canonicalRecurringActualPayments
import app.myfinhub.android.core.data.cashFlowForecast
import app.myfinhub.android.core.data.array
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web recurring.ts / forecast.ts: estimated periodic debits are not actual payments. */
class CanonicalVariableRecurringEstimateProjectionTest {
    private val today = LocalDate.parse("2026-10-15")
    private val document = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[{"id":"cash","name":"Μετρητά","kind":"cash"}],
        "snapshots":[{"date":"2026-10-15","balances":{"cash":500}}],
        "recurring":[
          {"id":"variable","name":"Ρεύμα","amount":64,"amountMode":"variable",
           "estimateMin":45,"estimateMax":90,"firstExpectedDate":"2026-10-20",
           "accountId":"cash","active":true},
          {"id":"fixed","name":"Ενοίκιο","amount":100,"firstExpectedDate":"2026-10-22",
           "accountId":"cash","active":true},
          {"id":"override","name":"Παλιό","amount":700,"firstExpectedDate":"2026-10-19",
           "accountId":"cash","active":true}
        ]},"state":{
          "recurringOverrides":{"override":{
            "id":"override","name":"Νερό","amount":22,"amountMode":"variable",
            "estimateMin":14,"estimateMax":30,"firstExpectedDate":"2026-10-19",
            "accountId":"cash","active":true}},
          "recurringCustom":[{"id":"custom","name":"Θέρμανση","amount":25,
            "amountMode":"variable","estimateMax":40,"firstExpectedDate":"2026-10-25",
            "accountId":"cash","active":true}],
          "events":[],"scheduled":[],"settings":{}}}
    """.trimIndent()).jsonObject)

    @Test fun variableModeAndRangesPassThroughSeedOverridesCustomToNative() {
        val recurring = document.allCanonicalRecurringItems().associateBy { it.id }
        assertEquals(4, recurring.size)
        assertTrue(recurring.getValue("variable").isVariableEstimate)
        assertFalse(recurring.getValue("fixed").isVariableEstimate)
        assertEquals("Νερό", recurring.getValue("override").name)
        assertEquals(14.0, recurring.getValue("override").estimateMin!!, 0.001)
        assertEquals(40.0, recurring.getValue("custom").estimateMax!!, 0.001)
        assertNull(recurring.getValue("custom").estimateMin)
        val projected = projectCanonicalPlanState(document, today, null)
        val ui = projected.recurringManageItems.associateBy { it.id }
        assertEquals("variable", ui.getValue("variable").amountMode)
        assertEquals(45.0, ui.getValue("variable").estimateMin!!, 0.001)
        assertEquals(90.0, ui.getValue("variable").estimateMax!!, 0.001)
        assertEquals("fixed", ui.getValue("fixed").amountMode)
        assertEquals(14.0, ui.getValue("override").estimateMin!!, 0.001)
        assertNull(ui.getValue("custom").estimateMin)
        assertNull(ui.getValue("variable").amountValidationError)
        assertNull(ui.getValue("fixed").amountValidationError)
        assertNull(ui.getValue("override").amountValidationError)
        assertTrue(ui.getValue("custom").amountValidationError!!
            .contains("και τα δύο άκρα"))
        assertEquals(211.0, document.canonicalRecurringMonthlyTotal(), 0.001)
        assertTrue(document.state.array("events").isEmpty())
    }

    @Test fun malformedImportedModeIsFlaggedInManagementNotMislabelledAsFixed() {
        val historical = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[{"id":"cash","name":"Cash","kind":"cash"}],
            "snapshots":[{"date":"2026-10-15","balances":{"cash":300}}],
            "recurring":[{"id":"legacy","name":"Unrecognized import","amount":30,
              "amountMode":"unknown-import-mode","accountId":"cash","active":true,
              "firstExpectedDate":"2026-10-20"}]},
            "state":{"events":[],"scheduled":[],"settings":{}}}
        """.trimIndent()).jsonObject)
        val initial = historical.raw
        val item = projectCanonicalPlanState(historical, today, null)
            .recurringManageItems.single()
        assertEquals("unknown-import-mode", item.amountMode)
        assertTrue(item.amountValidationError!!.contains("έγκυρο τύπο"))
        assertEquals(0, historical.canonicalEvents().size)
        assertEquals(initial, historical.raw)
    }

    @Test fun linkedActualExpenseCentsExcludeUnrelatedAndFutureEntriesWithoutRewritingEstimates() {
        val data = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[{"id":"cash","name":"Cash","kind":"cash"}],
            "snapshots":[{"date":"2026-10-01","balances":{"cash":500}}],
            "recurring":[{"id":"power","name":"Ρεύμα","amount":80,"amountMode":"variable",
              "estimateMin":60,"estimateMax":100,"day":19,"accountId":"cash","active":true}]},
            "state":{"events":[
              {"id":"sep","kind":"expense","recurringId":"power","date":"2026-09-05","amount":62.25},
              {"id":"oct-a","kind":"expense","recurringId":"power","date":"2026-10-02","amount":10.02},
              {"id":"oct-b","kind":"expense","recurringId":"power","date":"2026-10-08","amount":95.03},
              {"id":"not-paid","kind":"income","recurringId":"power","date":"2026-10-12","amount":500},
              {"id":"unrelated","kind":"expense","date":"2026-10-14","amount":70},
              {"id":"future","kind":"expense","recurringId":"power","date":"2026-10-20","amount":77}
            ],"settings":{}}}
        """.trimIndent()).jsonObject)
        val actual = data.canonicalRecurringActualPayments("power", "2026-10-01", "2026-10-15")
        assertEquals(2, actual.count)
        assertEquals(105.05, actual.totalAmount, 0.001)
        assertEquals("2026-10-08", actual.lastPaymentDateIso)
        assertEquals(95.03, actual.lastPaymentAmount!!, 0.001)
        assertEquals(listOf("oct-b", "oct-a"), actual.records.map { it.eventId })
        assertEquals(listOf(95.03, 10.02), actual.records.map { it.amount })
        assertEquals(0, data.canonicalRecurringActualPayments(
            "power", "2026-10-16", "2026-10-19").count)
        val ui = projectCanonicalPlanState(data, LocalDate.parse("2026-10-15"), null)
            .recurringManageItems.single()
        assertEquals(105.05, ui.actualPaidThisMonth!!, 0.001)
        assertEquals(2, ui.actualPaymentsThisMonth)
        assertEquals(95.03, ui.lastActualPaymentAmount!!, 0.001)
        assertEquals("2026-10-08", ui.lastActualPaymentDateIso)
        assertEquals(listOf("oct-b", "oct-a", "sep"),
            ui.actualPaymentHistory.map { it.eventId })
        assertEquals(listOf("2026-10-08", "2026-10-02", "2026-09-05"),
            ui.actualPaymentHistory.map { it.dateIso })
        assertEquals(80.0, ui.amount, 0.001)
        assertEquals("variable", ui.amountMode)
        assertEquals(6, data.state.array("events").size)
    }

    @Test fun duplicateRecurringRowsRemainVisibleWithoutAmbiguousPaymentAttribution() {
        val data = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"recurring":[
              {"id":"repeated","name":"First","amount":10,"accountId":"cash","active":true},
              {"id":"repeated","name":"Second","amount":20,"accountId":"cash","active":true}
          ]},"state":{"events":[
              {"id":"old","kind":"expense","recurringId":"repeated","date":"2026-10-02","amount":15}
          ]}}
        """.trimIndent()).jsonObject)
        val rows = projectCanonicalPlanState(data, today, null).recurringManageItems
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.actualPaymentHistory.isEmpty() })
        assertTrue(rows.all { it.lastActualPaymentAmount == null })
        assertTrue(rows.all { it.actualPaidThisMonth == null })
        assertEquals(1, data.state.array("events").size)
    }

    @Test fun onlyVariableForecastMovementsAreMarkedEstimatesWithoutChangingMoney() {
        val forecast = document.cashFlowForecast(today, 30)
        val items = forecast.movements.filter { it.source == "recurring" }
        assertEquals(4, items.size)
        assertEquals("Εκτίμηση: Νερό", items.first { "override" in it.id }.label)
        assertEquals("Εκτίμηση: Ρεύμα", items.first { "variable" in it.id }.label)
        assertEquals("Εκτίμηση: Θέρμανση", items.first { "custom" in it.id }.label)
        assertEquals("Ενοίκιο", items.first { "fixed" in it.id }.label)
        assertEquals(289.0, forecast.projectedPortfolio, 0.001)
        assertEquals(4, forecast.sourceCounts["recurring"])
        val windows = projectCanonicalPlanState(document, today, null).forecastWindows
        assertTrue(windows.all { it.knownMovements.any { movement ->
            movement.label == "Εκτίμηση: Νερό" && movement.source == "recurring"
        } })
        assertTrue(document.state.array("events").isEmpty())
    }
}
