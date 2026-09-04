package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ActivityEditMutationTest {
    private fun document(): CanonicalFinanceDocument = CanonicalFinanceDocument(
        Json.parseToJsonElement(
            """{
              "updatedAt":"2026-09-01T00:00:00Z",
              "seed":{"accounts":[],"transactions":[]},
              "state":{"events":[{
                "id":"evt-1","date":"2026-09-01","kind":"expense","amount":12.5,
                "note":"Old note","category":"Food","subcategory":"Groceries",
                "legs":[{"accountId":"cash","amount":-12.5}],
                "createdAt":"2026-09-01T10:00:00Z","updatedAt":"2026-09-01T10:00:00Z"
              }]}
            }""",
        ).jsonObject,
    )

    @Test
    fun editActivity_updatesOnlyLedgerSafeEditableFields() {
        val edited = EditCanonicalActivity(
            transactionId = "evt-1",
            note = "Dinner",
            category = "Food",
            nowIso = "2026-09-02T20:00:00Z",
            date = "2026-09-02",
            subcategory = "Dining",
        ).apply(document())

        val event = edited.state.array("events").first() as JsonObject
        assertEquals("2026-09-02", event.string("date"))
        assertEquals("Dinner", event.string("note"))
        assertEquals("Food", event.string("category"))
        assertEquals("Dining", event.string("subcategory"))
        assertEquals(-12.5, (event.array("legs").first() as JsonObject).number("amount")!!, 0.001)
        assertEquals("2026-09-01T10:00:00Z", event.string("createdAt"))
        assertEquals("2026-09-02T20:00:00Z", event.string("updatedAt"))
    }

    @Test
    fun editActivity_emptySubcategoryExplicitlyClearsIt() {
        val edited = EditCanonicalActivity(
            transactionId = "evt-1",
            note = "Old note",
            category = "Food",
            nowIso = "2026-09-02T20:00:00Z",
            subcategory = "",
        ).apply(document())
        val event = edited.state.array("events").first() as JsonObject
        assertFalse("subcategory" in event)
        assertEquals("2026-09-01", event.string("date"))
    }

    @Test
    fun legacyQueuedEdit_withoutDateOrSubcategory_preservesBoth() {
        val intent = PendingCanonicalMutationIntent(
            intentId = "intent-old",
            kind = PendingMutationKind.EDIT_ACTIVITY,
            payload = JsonObject(
                mapOf(
                    "transactionId" to JsonPrimitive("evt-1"),
                    "note" to JsonPrimitive("Queued old edit"),
                    "category" to JsonPrimitive("Food"),
                    "nowIso" to JsonPrimitive("2026-09-02T20:00:00Z"),
                ),
            ),
        )
        val edited = intent.asMutation().apply(document())
        val event = edited.state.array("events").first() as JsonObject
        assertEquals("2026-09-01", event.string("date"))
        assertEquals("Groceries", event.string("subcategory"))
        assertEquals("Queued old edit", event.string("note"))
    }
}
