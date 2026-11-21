package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePendingRestorePolicyTest {
    private fun intent(
        id: String,
        payloadValue: String = "event-1",
        syncState: PendingMutationSyncState = PendingMutationSyncState.NEVER_SENT,
    ) = PendingCanonicalMutationIntent(
        intentId = id,
        kind = PendingMutationKind.APPEND_EVENT,
        payload = JsonObject(mapOf("source" to JsonPrimitive(payloadValue))),
        syncState = syncState,
    )

    @Test fun distinctCurrentAndLegacyIntentsRemainInOriginalReplayOrder() {
        val newer = intent("new")
        val old = intent("legacy")
        assertEquals(
            listOf(newer, old),
            mergeRestoredPendingMutations(listOf(newer), listOf(old)),
        )
    }

    @Test fun matchingCrossFormatMigrationCopyKeepsConservativeReviewState() {
        val stored = intent("legacy-append-event-1")
        val older = stored.copy(syncState = PendingMutationSyncState.NEEDS_REVIEW)
        val restored = mergeRestoredPendingMutations(listOf(stored), listOf(older))
        assertEquals(1, restored.size)
        assertEquals(stored.intentId, restored.single().intentId)
        assertEquals(PendingMutationSyncState.NEEDS_REVIEW, restored.single().syncState)
    }

    @Test fun conflictingMigrationIdMustNeverDiscardSecondFinancialIntent() {
        val generic = intent("same", "event-1")
        val old = intent("same", "event-2")
        assertTrue(runCatching {
            mergeRestoredPendingMutations(listOf(generic), listOf(old))
        }.exceptionOrNull() is IllegalStateException)
    }

    @Test fun duplicateIdsWithinGenericStorageFailEvenIfPayloadsMatch() {
        val change = intent("same")
        assertTrue(runCatching {
            mergeRestoredPendingMutations(listOf(change, change), emptyList())
        }.exceptionOrNull() is IllegalStateException)
    }

    @Test fun duplicateIdsWithinLegacyStorageFailEvenIfPayloadsMatch() {
        val old = intent("legacy-append-event-1")
        assertTrue(runCatching {
            mergeRestoredPendingMutations(emptyList(), listOf(old, old))
        }.exceptionOrNull() is IllegalStateException)
    }

    @Test fun independentlyPersistedReviewStateCannotBeDowngradedDuringMigration() {
        val current = intent("same", syncState = PendingMutationSyncState.NEEDS_REVIEW)
        val old = current.copy(syncState = PendingMutationSyncState.NEVER_SENT)
        val merged = mergeRestoredPendingMutations(listOf(current), listOf(old))
        assertEquals(PendingMutationSyncState.NEEDS_REVIEW, merged.single().syncState)
    }
}
