package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Web recurring.ts/recurringCadence.ts golden month, leap and account lifecycle dates. */
class WebRecurringCadenceLifecycleParityTest {
    private val data=CanonicalFinanceDocument(Json.parseToJsonElement("""
    {"seed":{"recurring":[
      {"id":"monthly","name":"Μηνιαίο","amount":31,"day":31,"firstExpectedDate":"2026-01-31","accountId":"bank","category":"Άλλο","active":true},
      {"id":"quarterly","name":"Τριμηνιαίο","amount":90,"firstExpectedDate":"2026-01-31","recurrenceUnit":"month","recurrenceInterval":3,"accountId":"bank","category":"Άλλο","active":true},
      {"id":"annual","name":"Ετήσιο","amount":120,"firstExpectedDate":"2024-02-29","recurrenceUnit":"year","accountId":"bank","category":"Άλλο","active":true},
      {"id":"override","name":"Παλιό","amount":999,"day":10,"accountId":"bank","category":"Άλλο","active":true},
      {"id":"paid","name":"Πληρωμένο νωρίς","amount":20,"day":20,"accountId":"bank","category":"Άλλο","active":true},
      {"id":"stopped","name":"Σταματημένο","amount":30,"day":15,"status":"stopped","accountId":"bank","category":"Άλλο","active":false}
    ]},"state":{
      "recurringOverrides":{"override":{"id":"override","name":"Ενημερωμένο","amount":40,"day":10,"accountId":"bank","category":"Άλλο","active":false,"status":"paused"}},
      "recurringCustom":[
        {"id":"custom","name":"Custom","amount":60,"firstExpectedDate":"2026-04-15","recurrenceUnit":"month","recurrenceInterval":6,"accountId":"bank","category":"Άλλο","active":true},
        {"id":"custom-paused","name":"Paused","amount":10,"day":15,"accountId":"bank","category":"Άλλο","active":false,"status":"paused"}
      ],"events":[{"id":"linked","recurringId":"paid","date":"2026-10-05","kind":"expense","amount":20,"createdAt":"2026-10-05T10:00:00Z"}]}}
    """.trimIndent()).jsonObject)
    private fun recurring(id: String) = data.allCanonicalRecurringItems().first { it.id == id }

    @Test fun includesSeedOverridesAndCustomRecordsWithoutDuplicatingIdentity() {
        assertEquals(8,data.allCanonicalRecurringItems().size)
        assertEquals("Ενημερωμένο",recurring("override").name)
        assertEquals("paused",recurring("override").effectiveStatus)
        assertEquals(listOf("monthly","quarterly","annual","paid","custom"),
            data.activeCanonicalRecurringItems().map { it.id })
        assertEquals(101.0,data.canonicalRecurringMonthlyTotal(),0.001)
    }

    @Test fun clampsMonthEndLeapYearAndYearCadence() {
        assertEquals(LocalDate.parse("2026-02-28"),
            data.nextCanonicalRecurringDate(recurring("monthly"),LocalDate.parse("2026-02-10")))
        assertEquals(LocalDate.parse("2026-04-30"),
            data.nextCanonicalRecurringDate(recurring("quarterly"),LocalDate.parse("2026-04-15")))
        assertEquals(LocalDate.parse("2025-02-28"),
            data.nextCanonicalRecurringDate(recurring("annual"),LocalDate.parse("2025-02-10")))
        assertEquals(LocalDate.parse("2026-10-15"),
            data.nextCanonicalRecurringDate(recurring("custom"),LocalDate.parse("2026-10-15")))
    }

    @Test fun legacyDayAboveThirtyOneClampsToMonthEndAsWebRecurringDoes() {
        val data=CanonicalFinanceDocument(Json.parseToJsonElement("""
        {"seed":{"recurring":[
          {"id":"legacy-day","name":"End of month","amount":35,"day":32,
           "accountId":"bank","active":true}
        ]},"state":{"events":[]}}
        """.trimIndent()).jsonObject)
        val entry=data.allCanonicalRecurringItems().single()
        assertEquals(32,data.canonicalTypicalRecurringPaymentDay(entry))
        assertEquals(LocalDate.parse("2026-02-28"),
            data.nextCanonicalRecurringDate(entry,LocalDate.parse("2026-02-10")))
        assertEquals(LocalDate.parse("2026-03-31"),
            data.nextCanonicalRecurringDate(entry,LocalDate.parse("2026-03-30")))
        assertEquals(LocalDate.parse("2026-04-30"),
            data.nextCanonicalRecurringDate(entry,LocalDate.parse("2026-04-01")))
    }

    @Test fun linkedPaymentControlsTypicalDayAndSettlesMonth() {
        assertEquals(5,data.canonicalTypicalRecurringPaymentDay(recurring("paid")))
        assertEquals(LocalDate.parse("2026-11-05"),
            data.nextCanonicalRecurringDate(recurring("paid"),LocalDate.parse("2026-10-10")))
    }

    @Test fun pausedAndStoppedNeverCreateUpcomingDates() {
        assertEquals(null,data.nextCanonicalRecurringDate(recurring("override"),LocalDate.parse("2026-10-10")))
        assertEquals(null,data.nextCanonicalRecurringDate(recurring("stopped"),LocalDate.parse("2026-10-10")))
        assertFalse(data.activeCanonicalRecurringItems().any { it.id=="custom-paused" })
    }
}
