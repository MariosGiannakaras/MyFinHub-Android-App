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

    @Test fun staleDeleteIntentNeverPurgesNewlyRestoredCard() {
        val intent = intent(PermanentlyDeleteCanonicalCard("same-id",now), "delete")
        val nowActive = document("""[{"id":"same-id","kind":"credit","active":true}]""", """["same-id"]""")
        assertEquals(emptyList<String>(), committedPermanentCardCleanupIds(listOf(intent),nowActive))
    }
}
