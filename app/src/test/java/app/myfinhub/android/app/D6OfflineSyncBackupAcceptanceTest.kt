package app.myfinhub.android.app

import app.myfinhub.android.core.data.AppendCanonicalEvent
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.PendingCanonicalMutationIntent
import app.myfinhub.android.core.data.PendingMutationSyncState
import app.myfinhub.android.core.data.reconcileSatisfiedPendingMutations
import app.myfinhub.android.feature.utilities.isCurrentBackupExportCandidate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D6 cross-surface acceptance: auth-gated reconnect never silently replays ambiguous
 * finance writes, and Settings export remains blocked until the durable queue settles.
 * Synthetic, no API calls or actual finance writes.
 */
class D6OfflineSyncBackupAcceptanceTest {
    private val initial = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"schemaVersion":3,"seed":{"accounts":[],"transactions":[]},
       "state":{"events":[]}}
    """.trimIndent()).jsonObject)

    private fun append(id: String, sync: PendingMutationSyncState) =
        PendingCanonicalMutationIntent.fromMutation(
            mutation = AppendCanonicalEvent(
                event = Json.parseToJsonElement("""
                    {"id":"$id","date":"2026-10-25","kind":"expense","amount":5,"note":"Test"}
                """.trimIndent()).jsonObject,
                nowIso = "2026-10-25T10:00:00Z",
            ),
            intentId = "intent-$id",
            syncState = sync,
        )

    private fun exportAllowed(document: JsonObject, queue: List<PendingCanonicalMutationIntent>) =
        isCurrentBackupExportCandidate(
            selectedSnapshot = document,
            currentDocument = document,
            pendingChangeCount = financePendingCounts(
                total = queue.size,
                needsReview = queue.count { it.syncState == PendingMutationSyncState.NEEDS_REVIEW },
                exposePending = false,
            ).blockingBackupCount,
            mutationBlocked = false,
        )

    @Test fun pendingOfflineWriteRequiresReconcileAndBlocksConnectedUiBackup() {
        val queued = listOf(append("unsent", PendingMutationSyncState.NEVER_SENT))
        assertEquals(MutationDeliveryMode.RECONCILE_PENDING_FIRST, mutationDeliveryMode(
            serverAvailable = true, repositoryReady = true, hasPendingMutations = true,
        ))
        assertEquals(listOf("intent-unsent"), queued.takeWhile {
            it.syncState == PendingMutationSyncState.NEVER_SENT
        }.map { it.intentId })
        assertFalse(exportAllowed(initial.raw, queued))

        val serverAcknowledged = queued.single().asMutation().apply(initial)
        assertTrue(reconcileSatisfiedPendingMutations(serverAcknowledged, queued).isEmpty())
        assertTrue(exportAllowed(serverAcknowledged.raw, emptyList()))
    }

    @Test fun ambiguousPrecedingMutationPreventsBlindReplayAndBackup() {
        val unresolved = listOf(
            append("ambiguous", PendingMutationSyncState.NEEDS_REVIEW),
            append("later", PendingMutationSyncState.NEVER_SENT),
        )
        // A later NEVER_SENT edit cannot skip ahead of an unresolved earlier write.
        val automatic = unresolved.takeWhile { it.syncState == PendingMutationSyncState.NEVER_SENT }
        assertTrue(automatic.isEmpty())
        assertEquals(unresolved, reconcileSatisfiedPendingMutations(initial, unresolved))
        assertFalse(exportAllowed(initial.raw, unresolved))

        val serverAcknowledgedFirst = unresolved.first().asMutation().apply(initial)
        val remaining = reconcileSatisfiedPendingMutations(serverAcknowledgedFirst, unresolved)
        assertEquals(listOf("intent-later"), remaining.map { it.intentId })
        assertFalse(exportAllowed(serverAcknowledgedFirst.raw, remaining))
    }

    @Test fun offlineStateWithNoUnsentChangesRetainsValidCanonicalExport() {
        assertEquals(MutationDeliveryMode.DURABLE_OFFLINE_QUEUE, mutationDeliveryMode(
            serverAvailable = false, repositoryReady = true, hasPendingMutations = false,
        ))
        assertTrue(exportAllowed(initial.raw, emptyList()))
    }
}
