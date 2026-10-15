package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSecretCleanupCompletionTest {
    private val timestamp = "2026-10-15T09:10:00Z"
    private val removed = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[]},"state":{
        "cards":[{"id":"live","kind":"debit","active":true}],
        "pendingCardSecretDeletes":["deleted","other"],
        "deletedCards":[{"id":"deleted","kind":"credit","createdAt":"2026-08-01","deletedAt":"2026-10-15"}],
        "events":[{"id":"historic","date":"2026-09-01","kind":"card_purchase","cardId":"deleted","amount":12,"legs":[]}]
      }}
    """.trimIndent()).jsonObject)

    @Test fun completionClearsOnlyItsMarkerWithoutTouchingDebtHistoryOrTombstone() {
        val complete = CompleteCanonicalCardSecretCleanup("deleted", timestamp)
        val updated = complete.apply(removed)
        assertEquals(listOf("other"), updated.state.array("pendingCardSecretDeletes").map { (it as JsonPrimitive).content })
        assertEquals(removed.state.array("events"), updated.state.array("events"))
        assertEquals(removed.state.array("deletedCards"), updated.state.array("deletedCards"))
        assertEquals(removed.state.array("cards"), updated.state.array("cards"))
        assertEquals(timestamp, updated.raw.string("updatedAt"))
        assertEquals(updated.raw, complete.apply(updated).raw)
    }

    @Test fun neverAcknowledgeCleanupForExistingProfileOrMalformedId() {
        assertTrue(runCatching { CompleteCanonicalCardSecretCleanup("live", timestamp).apply(removed) }.isFailure)
        assertTrue(runCatching { CompleteCanonicalCardSecretCleanup("..bad/id", timestamp).apply(removed) }.isFailure)
        assertTrue(removed.state.array("pendingCardSecretDeletes").any { (it as JsonPrimitive).content == "deleted" })
    }

    @Test fun stableMutationIntentReplaysAndRecognizesOnlyServerConfirmedMarkerRemoval() {
        val intent = PendingCanonicalMutationIntent.fromMutation(
            CompleteCanonicalCardSecretCleanup("deleted", timestamp),
            intentId = "ack-delete-1",
        )
        assertEquals(PendingMutationKind.COMPLETE_CARD_SECRET_CLEANUP, intent.kind)
        assertEquals("deleted", intent.affectedCardId)
        assertFalse(intent.isSatisfiedBy(removed))
        val updated = intent.asMutation().apply(removed)
        assertTrue(intent.isSatisfiedBy(updated))
        assertEquals("other", (updated.state.array("pendingCardSecretDeletes").single() as JsonPrimitive).content)
    }
}
