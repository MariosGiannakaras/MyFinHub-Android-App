package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** An already queued card-payment intent must remain attached to its chosen owner. */
class CanonicalCardPaymentReplayIdentityTest {
    private val now = "2026-10-15T10:30:00Z"

    private fun document(rows: String): CanonicalFinanceDocument =
        CanonicalFinanceDocument(Json.parseToJsonElement(
            """{"seed":{"accounts":[]},"state":{"events":[],"creditStatements":$rows}}"""
        ).jsonObject)

    private fun payment(link: String?, id: String = "pay-queued", card: String = "card-a"): JsonObject =
        JsonObject(buildMap {
            put("id", JsonPrimitive(id))
            put("kind", JsonPrimitive("card_payment"))
            put("date", JsonPrimitive("2026-10-15"))
            put("amount", JsonPrimitive(10.0))
            put("cardId", JsonPrimitive(card))
            link?.let { put("statementId", JsonPrimitive(it)) }
        })

    private val owned = """{"id":"stmt","cardId":"card-a","openDate":"2026-09-01",
        "closeDate":"2026-09-30","dueDate":"2026-10-25"}"""

    @Test fun uniqueMatchingStatementAllowsIntentAndRetainsExactLink() {
        val source = document("[$owned]")
        val pending = AppendCanonicalEvent(payment("stmt"), now)
        val committed = pending.apply(source)
        assertEquals("stmt", (committed.state.array("events").single() as JsonObject).string("statementId"))
        assertEquals(1, committed.state.array("creditStatements").size)
        assertEquals(committed.raw, pending.apply(committed).raw)
        assertTrue(source.state.array("events").isEmpty())
    }

    @Test fun collisionAppearingAfterQueueingBlocksReplayWithoutChangingSource() {
        val original = document("[$owned]")
        val queued = AppendCanonicalEvent(payment("stmt"), now)
        val collision = document("""[$owned,{"id":"stmt","cardId":"card-b"}]""")
        assertEquals("stmt", (queued.apply(original).state.array("events").single() as JsonObject).string("statementId"))
        val unchanged = collision.raw
        assertTrue(runCatching { queued.apply(collision) }.isFailure)
        assertEquals(unchanged, collision.raw)
        assertTrue(collision.state.array("events").isEmpty())
    }

    @Test fun duplicateSameOwnerIsAlsoAmbiguous() {
        val duplicate = document("[$owned,$owned]")
        assertTrue(runCatching { AppendCanonicalEvent(payment("stmt"), now).apply(duplicate) }.isFailure)
        assertEquals(2, duplicate.state.array("creditStatements").size)
    }

    @Test fun missingForeignWhitespaceAndMalformedOwnerCannotBecomeUnlinkedMoneyMoves() {
        val sources = listOf(
            document("[]") to "stmt",
            document("""[{"id":"stmt","cardId":"card-b"}]""") to "stmt",
            document("""[{"id":"stmt"}]""") to "stmt",
            document("""[{"id":"   ","cardId":"card-a"}]""") to "   ",
        )
        sources.forEach { (doc, id) ->
            assertTrue("Unexpected linked replay for '$id'",
                runCatching { AppendCanonicalEvent(payment(id), now).apply(doc) }.isFailure)
            assertTrue(doc.state.array("events").isEmpty())
        }
    }

    @Test fun trulyUnlinkedPaymentsArePreservedAndReplayIsIdempotent() {
        val empty = document("[]")
        val pending = AppendCanonicalEvent(payment(null), now)
        val committed = pending.apply(empty)
        assertFalse((committed.state.array("events").single() as JsonObject).containsKey("statementId"))
        assertEquals(committed.raw, pending.apply(committed).raw)
        val explicitEmpty = AppendCanonicalEvent(payment("", "empty-link"), now).apply(empty)
        // Web treats an empty ID as falsy, but retains the original empty field.
        // Only a genuinely absent statementId is represented by a missing key.
        assertEquals("", (explicitEmpty.state.array("events").single() as JsonObject).string("statementId"))
        assertEquals(explicitEmpty.raw, AppendCanonicalEvent(payment("", "empty-link"), now).apply(explicitEmpty).raw)
    }

    @Test fun alreadyCommittedEventRemainsIdempotentIfLaterSourceBecomesAmbiguous() {
        val source = document("[$owned]")
        val event = payment("stmt")
        val committed = AppendCanonicalEvent(event, now).apply(source)
        val contested = CanonicalFinanceDocument(JsonObject(
            committed.raw + ("state" to JsonObject(
                committed.state + ("creditStatements" to Json.parseToJsonElement(
                    """[$owned,{"id":"stmt","cardId":"card-b"}]"""
                ))
            ))
        ))
        // Existing exact event ID is a replay no-op; never create a second payment.
        val replayed = AppendCanonicalEvent(event, now).apply(contested)
        assertEquals(contested.raw, replayed.raw)
        assertEquals(1, replayed.state.array("events").size)
    }
}
