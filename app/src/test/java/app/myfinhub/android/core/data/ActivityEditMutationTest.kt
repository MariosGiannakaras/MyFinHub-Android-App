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


    private fun historicalDocument(): CanonicalFinanceDocument = CanonicalFinanceDocument(
        Json.parseToJsonElement("""
            {"seed":{"accounts":[
              {"id":"cash","kind":"cash","name":"Μετρητά"},
              {"id":"payroll","kind":"bank","name":"Μισθοδοσία"}
             ],"transactions":[
              {"id":"seed-old","date":"2026-09-15","type":"expense",
               "amount":12.5,"accountId":"cash","note":"Αρχικό",
               "source":"import","cell":"B3","sheet":"Sep","formula":"=10+2.5"}
             ]},"state":{"events":[
              {"id":"event","kind":"expense","date":"2026-09-14","amount":40,
               "note":"Event","legs":[{"accountId":"cash","amount":-40}]}
             ]}}
        """.trimIndent()).jsonObject,
    )

    @Test
    fun fullLegacyReviewOverrideChangesTypeAmountAndEndpointsWithoutTouchingSeed() {
        val original = historicalDocument()
        val mutation = EditCanonicalActivity(
            transactionId = "seed-old", note = "Μεταφορά προς μισθοδοσία",
            category = "Λοιπά", nowIso = "2026-09-18T10:00:00Z",
            date = "2026-09-18", subcategory = "",
            legacyType = "transfer", legacyAmount = 120.50,
            legacyFromAccountId = "cash", legacyToAccountId = "payroll",
        )
        val edited = mutation.apply(original)
        val seed = original.seed.array("transactions").single() as JsonObject
        val override = edited.state.obj("overrides")["seed-old"] as JsonObject
        assertEquals(seed, edited.seed.array("transactions").single())
        assertEquals("import", override.string("source"))
        assertEquals("B3", override.string("cell"))
        assertEquals("Sep", override.string("sheet"))
        assertEquals("=10+2.5", override.string("formula"))
        assertEquals("transfer", override.string("type"))
        assertEquals("2026-09-18", override.string("date"))
        assertEquals(120.50, override.number("amount")!!, 0.001)
        assertEquals(null, override.string("accountId"))
        assertEquals("cash", override.string("fromAccountId"))
        assertEquals("payroll", override.string("toAccountId"))
        assertEquals(null, override.string("subcategory"))
        assertEquals(original.state.array("events"), edited.state.array("events"))
        val expense = mutation.copy(
            legacyType = "expense", legacyAmount = 19.25,
            legacyAccountId = "payroll",
            legacyFromAccountId = null, legacyToAccountId = null,
        ).apply(edited)
        val revised = expense.state.obj("overrides")["seed-old"] as JsonObject
        assertEquals("expense", revised.string("type"))
        assertEquals(19.25, revised.number("amount")!!, 0.001)
        assertEquals("payroll", revised.string("accountId"))
        assertEquals(null, revised.string("fromAccountId"))
        assertEquals(null, revised.string("toAccountId"))
        assertEquals(seed, expense.seed.array("transactions").single())
    }

    @Test
    fun fullLegacyOverrideRejectsInvalidAccountsKindsAndMoneyWithoutMutatingEvents() {
        val source = historicalDocument()
        fun edit(type: String = "expense", amount: Double = 20.0,
                 account: String? = "cash", from: String? = null, to: String? = null
        ) = EditCanonicalActivity("seed-old", "Note", "Food", "2026-09-20T00:00:00Z",
            legacyType = type, legacyAmount = amount, legacyAccountId = account,
            legacyFromAccountId = from, legacyToAccountId = to)
        val invalid = listOf(
            edit(type = "card_payment"), edit(amount = 0.0),
            edit(amount = Double.NaN), edit(account = "missing"),
            edit(type = "transfer", account = null, from = "cash", to = "cash"),
            edit(type = "transfer", account = null, from = "cash", to = "missing"),
        )
        invalid.forEach { mutation ->
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                mutation.apply(source)
            }
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            edit().copy(transactionId = "event").apply(source)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            EditCanonicalActivity("seed-old", "Note", "Food", "2026-09-20T00:00:00Z",
                legacyAmount = 10.0).apply(source)
        }
        val negativeAdjustment = edit(type = "adjustment", amount = -3.21).apply(source)
        assertEquals(-3.21, (negativeAdjustment.state.obj("overrides")["seed-old"]
            as JsonObject).number("amount")!!, 0.001)
        assertEquals(source.seed, negativeAdjustment.seed)
    }

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
