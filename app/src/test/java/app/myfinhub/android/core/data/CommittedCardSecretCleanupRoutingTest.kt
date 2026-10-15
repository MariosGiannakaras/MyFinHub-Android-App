package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommittedCardSecretCleanupRoutingTest {
    private val now = "2026-10-15T12:00:00Z"

    private fun document(cards: String, pending: String) = CanonicalFinanceDocument(
        Json.parseToJsonElement("""
          {"seed":{"accounts":[]},"state":{
            "cards":$cards,
            "pendingCardSecretDeletes":$pending
          }}
        """.trimIndent()).jsonObject
    )

    private fun intent(mutation: CanonicalFinanceMutation, id: String): PendingCanonicalMutationIntent =
        PendingCanonicalMutationIntent.fromMutation(mutation, "intent-$id")

    @Test fun reversibleArchiveAndRestoreMustNeverDeleteServerOrLocalSecrets() {
        val archived = intent(DeactivateCanonicalCard("salary-visa", now), "archive")
        val restored = intent(RestoreCanonicalCard("salary-visa", now), "restore")
        val server = document("""[{"id":"salary-visa","kind":"debit","active":false}]""", "[]")
        assertEquals(emptyList<String>(), committedPermanentCardCleanupIds(listOf(archived,restored),server))
    }

    @Test fun onlyServerConfirmedPermanentDeletionWithPendingMarkerMayPurge() {
        val removed = intent(PermanentlyDeleteCanonicalCard("deleted-credit",now), "delete")
        val restored = intent(RestoreCanonicalCard("deleted-credit",now), "restore")
        val acknowledged = intent(CompleteCanonicalCardSecretCleanup("deleted-credit",now), "ack")
        val withMarker = document("[]", """["deleted-credit"]""")
        assertEquals(listOf("deleted-credit"), committedPermanentCardCleanupIds(
            listOf(removed, restored, acknowledged, removed), withMarker
        ))
        assertTrue(committedPermanentCardCleanupIds(listOf(removed), document("[]","[]")).isEmpty())
    }

    @Test fun persistedCleanupMarkerReplaysAfterRestartOnlyForRemovedProfiles() {
        val saved = document(
            """[{"id":"archived-card","active":false},{"id":"live-card","active":true}]""",
            """["deleted-card","deleted-card","archived-card","live-card","/bad-id"]""",
        )
        assertEquals(listOf("deleted-card"), confirmedPendingCardSecretCleanupIds(saved))
        assertEquals(emptyList<String>(), confirmedPendingCardSecretCleanupIds(document("[]","[]")))
    }

    @Test fun oneAcknowledgedMarkerLeavesOtherPendingCardRetryable() {
        val first = document("[]", """["deleted-first","deleted-second"]""")
        assertEquals(listOf("deleted-first","deleted-second"), confirmedPendingCardSecretCleanupIds(first))
        // Server's acknowledged state, not a lost one-shot notification, drives the next cleanup.
        val afterAck = document("[]", """["deleted-second"]""")
        assertEquals(listOf("deleted-second"), confirmedPendingCardSecretCleanupIds(afterAck))
    }

    @Test fun staleDeleteIntentNeverPurgesNewlyRestoredCard() {
        val intent = intent(PermanentlyDeleteCanonicalCard("same-id",now), "delete")
        val nowActive = document("""[{"id":"same-id","kind":"credit","active":true}]""", """["same-id"]""")
        assertEquals(emptyList<String>(), committedPermanentCardCleanupIds(listOf(intent),nowActive))
    }
}
