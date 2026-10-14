package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalPermanentCardMutationTest {
    private val now = "2026-10-14T20:00:00Z"
    private fun doc(events: String = "[]", archived: Boolean = true): CanonicalFinanceDocument =
        CanonicalFinanceDocument(Json.parseToJsonElement("""
        {"seed":{"accounts":[]},"state":{
          "cards":[
            {"id":"credit-old","kind":"credit","nickname":"Older","bankId":"alpha",
             "active":${!archived},"createdAt":"2026-01-01T00:00:00Z"},
            {"id":"credit-new","kind":"credit","nickname":"New","bankId":"alpha",
             "active":true,"createdAt":"2026-08-01T00:00:00Z"},
            {"id":"debit","kind":"debit","nickname":"Debit","bankId":"piraeus",
             "active":true,"createdAt":"2026-09-01T00:00:00Z"}
          ],
          "events":$events,
          "settings":{"unrelatedKey":"retained"}
        }}
        """.trimIndent()).jsonObject)

    private fun entries(d: CanonicalFinanceDocument, field: String) =
        d.state.array(field).mapNotNull { it as? JsonObject }

    @Test fun archivedSettledCreditDeletionIsIdempotentPreservesEventsAndCreatedAtTombstone() {
        val base = doc(events = """[
            {"id":"legacy-purchase","kind":"card_purchase","date":"2026-10-01","amount":80,"legs":[]},
            {"id":"legacy-payment","kind":"card_payment","date":"2026-10-03","amount":80,"legs":[]}
        ]""")
        val operation = PermanentlyDeleteCanonicalCard("credit-old",now)
        val result = operation.apply(base)
        assertEquals(listOf("credit-new","debit"),entries(result,"cards").map { it.string("id") })
        assertEquals(2,result.state.array("events").size)
        assertEquals("retained",result.state.obj("settings").string("unrelatedKey"))
        assertEquals(listOf("credit-old"), entries(result,"deletedCards").map { it.string("id") })
        assertEquals("2026-01-01T00:00:00Z",entries(result,"deletedCards").single().string("createdAt"))
        assertEquals(listOf("credit-old"),
            result.state.array("pendingCardSecretDeletes").map { (it as JsonPrimitive).content })
        assertEquals(0.0,result.creditDebtForCardAt("credit-old","2026-10-14"),0.001)
        assertEquals(result.raw,operation.apply(result).raw)
        assertTrue(PendingCanonicalMutationIntent.fromMutation(operation,"id").isSatisfiedBy(result))
    }

    @Test fun creditCannotBeDeletedWhenActiveOrUnsettledOrFutureMovementExists() {
        val active = doc(archived=false)
        assertThrows(IllegalArgumentException::class.java) {
            PermanentlyDeleteCanonicalCard("credit-old",now).apply(active)
        }
        val unsettled = doc("""[
          {"id":"purchase","kind":"card_purchase","cardId":"credit-old",
           "date":"2026-10-02","amount":50,"legs":[]}
        ]""")
        assertThrows(IllegalArgumentException::class.java) {
            PermanentlyDeleteCanonicalCard("credit-old",now).apply(unsettled)
        }
        val future = doc("""[
          {"id":"future","kind":"card_purchase","cardId":"credit-old",
           "date":"2026-10-20","amount":50,"legs":[]}
        ]""")
        assertThrows(IllegalArgumentException::class.java) {
            PermanentlyDeleteCanonicalCard("credit-old",now).apply(future)
        }
    }

    @Test fun debitEventOwnershipBlocksDeletionAndUnknownIdentityFailsClosed() {
        val used = doc("""[
          {"id":"debit-purchase","kind":"card_purchase","cardId":"debit",
           "date":"2026-10-02","amount":20,"legs":[]}
        ]""")
        assertThrows(IllegalArgumentException::class.java) {
            PermanentlyDeleteCanonicalCard("debit",now).apply(used)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PermanentlyDeleteCanonicalCard("unknown",now).apply(used)
        }
    }

    @Test fun durableIntentRoundTripsAndPreservesOrderedReconciliationAfterServerCommit() {
        val operation = PermanentlyDeleteCanonicalCard("credit-old",now)
        val pending = PendingCanonicalMutationIntent.fromMutation(
            operation,"delete-credit-old", PendingMutationSyncState.NEEDS_REVIEW)
        assertEquals(PendingMutationKind.PERMANENTLY_DELETE_CARD,pending.kind)
        assertEquals("credit-old",pending.affectedCardId)
        assertFalse(pending.isSatisfiedBy(doc()))
        val result = pending.asMutation().apply(doc())
        assertTrue(pending.isSatisfiedBy(result))
        assertEquals(result.raw,operation.apply(doc()).raw)
        assertTrue(reconcileSatisfiedPendingMutations(result,listOf(pending)).isEmpty())
    }

    @Test fun nonCreditDeletionQueuesSecretCleanupWithoutInventingCreditTombstone() {
        val base = doc()
        val result = PermanentlyDeleteCanonicalCard("debit",now).apply(base)
        assertFalse(entries(result,"cards").any { it.string("id")=="debit" })
        assertTrue(entries(result,"deletedCards").isEmpty())
        assertEquals(1,result.state.array("pendingCardSecretDeletes").size)
        assertEquals(result.raw,PermanentlyDeleteCanonicalCard("debit",now).apply(result).raw)
    }
}
