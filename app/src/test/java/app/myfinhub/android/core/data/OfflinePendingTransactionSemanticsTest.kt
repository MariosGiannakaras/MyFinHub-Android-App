package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePendingTransactionSemanticsTest {
    @Test
    fun stableEventIdReplay_isIdempotentAndCannotCreateDuplicateEvent() {
        val base = emptyDocument()
        val pending = pending(
            id = "evt-offline-stable",
            syncState = PendingTransactionSyncState.NEVER_SENT,
        )

        val once = pending.asMutation().apply(base)
        val twice = pending.asMutation().apply(once)

        assertEquals(
            1,
            twice.state.array("events").count { (it as? JsonObject)?.string("id") == "evt-offline-stable" },
        )
    }

    @Test
    fun serverFirstReconciliation_removesIntentAlreadyAcceptedByServer() {
        val pending = pending(
            id = "evt-server-already-has-it",
            syncState = PendingTransactionSyncState.NEEDS_REVIEW,
        )
        val server = pending.asMutation().apply(emptyDocument())
        val serverIds = server.canonicalEvents().map(CanonicalEvent::id).toSet()

        val remaining = listOf(pending).filterNot { it.eventId in serverIds }

        assertTrue(remaining.isEmpty())
    }

    @Test
    fun ambiguousAttempt_isNotEligibleForAutomaticReplay() {
        val neverSent = pending("evt-never-sent", PendingTransactionSyncState.NEVER_SENT)
        val needsReview = pending("evt-needs-review", PendingTransactionSyncState.NEEDS_REVIEW)

        val automaticReplay = listOf(neverSent, needsReview).filter {
            it.syncState == PendingTransactionSyncState.NEVER_SENT
        }

        assertEquals(listOf("evt-never-sent"), automaticReplay.map(PendingTransactionIntent::eventId))
        assertFalse(needsReview in automaticReplay)
    }

    private fun emptyDocument(): CanonicalFinanceDocument = CanonicalFinanceDocument(
        Json.parseToJsonElement(
            """
            {
              "schemaVersion":3,
              "updatedAt":"2026-09-04T08:00:00Z",
              "seed":{"accounts":[],"transactions":[]},
              "state":{"events":[]}
            }
            """.trimIndent(),
        ).jsonObject,
    )

    private fun pending(
        id: String,
        syncState: PendingTransactionSyncState,
    ): PendingTransactionIntent = PendingTransactionIntent(
        event = JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "date" to JsonPrimitive("2026-09-04"),
                "kind" to JsonPrimitive("expense"),
                "amount" to JsonPrimitive(5.0),
                "note" to JsonPrimitive("Offline test"),
            ),
        ),
        nowIso = "2026-09-04T08:00:00Z",
        syncState = syncState,
    )
}
