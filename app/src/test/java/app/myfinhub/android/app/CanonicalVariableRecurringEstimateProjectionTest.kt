package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.allCanonicalRecurringItems
import app.myfinhub.android.core.data.canonicalRecurringMonthlyTotal
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
        assertEquals(211.0, document.canonicalRecurringMonthlyTotal(), 0.001)
        assertTrue(document.state.array("events").isEmpty())
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
