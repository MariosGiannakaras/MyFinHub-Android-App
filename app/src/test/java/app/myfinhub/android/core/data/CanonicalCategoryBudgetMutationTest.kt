package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web budget categories: canonical, replayable writes without event/balance changes. */
class CanonicalCategoryBudgetMutationTest {
    private val before = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[{"id":"cash","kind":"cash"}]},
       "state":{"events":[{"id":"expense","kind":"expense","amount":50,
          "date":"2026-10-01","category":"Τρόφιμα","accountId":"cash"}],
          "budgets":[{"id":"food","month":"2026-10","scope":"category",
            "category":"Τρόφιμα","amount":100,"alertThreshold":0.8,
            "createdAt":"2026-10-01T00:00:00Z","note":"retain"}]}}
    """.trimIndent()).jsonObject)
    private val now = "2026-10-23T10:00:00Z"
    private val source get() = before.state.array("budgets").single() as JsonObject

    @Test fun editPreservesIdentityMetadataLedgerAndPendingReplay() {
        val mutation = UpsertCategoryBudget("2026-10","Τρόφιμα",125.50,85,"food",source,now)
        val after = mutation.apply(before)
        val row = after.state.array("budgets").single() as JsonObject
        assertEquals("food",row.string("id"))
        assertEquals("retain",row.string("note"))
        assertEquals("2026-10-01T00:00:00Z",row.string("createdAt"))
        assertEquals(125.50,row.number("amount")!!,0.001)
        assertEquals(0.85,row.number("alertThreshold")!!,0.001)
        assertEquals(before.state.array("events"),after.state.array("events"))
        assertTrue(mutation.satisfiedBy(after))
        assertEquals(after.raw,mutation.apply(after).raw)
        val intent = PendingCanonicalMutationIntent.fromMutation(mutation,"pending-food")
        assertEquals(PendingMutationKind.UPSERT_CATEGORY_BUDGET,intent.kind)
        assertFalse(intent.isSatisfiedBy(before))
        assertEquals(after.raw,intent.asMutation().apply(before).raw)
        assertTrue(intent.isSatisfiedBy(after))
    }

    @Test fun categoryCreationUsesStableIdAndRejectsConcurrentRemoteCreation() {
        val mutation = UpsertCategoryBudget("2026-10","Μετακινήσεις",75.0,80,
            "budget-new",null,now)
        val after = mutation.apply(before)
        assertEquals(2,after.state.array("budgets").size)
        assertEquals(before.state.array("events"),after.state.array("events"))
        val intent = PendingCanonicalMutationIntent.fromMutation(mutation,"new")
        assertTrue(intent.isSatisfiedBy(after))
        val changedRow = JsonObject(mapOf(
            "id" to JsonPrimitive("remote"),
            "month" to JsonPrimitive("2026-10"),
            "scope" to JsonPrimitive("category"),
            "category" to JsonPrimitive("Μετακινήσεις"),
            "amount" to JsonPrimitive(42.0),
        ))
        val remote = before.copy(raw = JsonObject(before.raw + mapOf(
            "state" to before.state.updated("budgets",JsonArray(
                before.state.array("budgets") + changedRow
            )),
        )))
        assertFalse(intent.isSatisfiedBy(remote))
        assertThrows(IllegalArgumentException::class.java) { mutation.apply(remote) }
    }

    @Test fun staleEditAndDuplicateHistoricalCategoryAreReadOnly() {
        val mutation = UpsertCategoryBudget("2026-10","Τρόφιμα",120.0,85,"food",source,now)
        val changed = JsonObject(source + mapOf("amount" to JsonPrimitive(140.0)))
        val remote = before.copy(raw=JsonObject(before.raw + mapOf(
            "state" to before.state.updated("budgets",JsonArray(listOf(changed))),
        )))
        assertThrows(IllegalArgumentException::class.java) { mutation.apply(remote) }
        val duplicated = before.copy(raw=JsonObject(before.raw + mapOf(
            "state" to before.state.updated("budgets",JsonArray(listOf(source,source))),
        )))
        assertFalse(mutation.satisfiedBy(duplicated))
        assertThrows(IllegalArgumentException::class.java) { mutation.apply(duplicated) }
    }

    @Test fun moneyThresholdAndMonthValidationPreventUnsafeWrites() {
        assertThrows(IllegalArgumentException::class.java) {
            UpsertCategoryBudget("2026-13","Τρόφιμα",100.0,80,"food",source,now).apply(before)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpsertCategoryBudget("2026-10","Τρόφιμα",0.0,80,"food",source,now).apply(before)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpsertCategoryBudget("2026-10","Τρόφιμα",100.0,40,"food",source,now).apply(before)
        }
    }
}
