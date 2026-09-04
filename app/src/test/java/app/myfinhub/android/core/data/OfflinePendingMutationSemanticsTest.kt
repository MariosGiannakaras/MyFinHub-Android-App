package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePendingMutationSemanticsTest {
    @Test
    fun everySupportedMutation_roundTripsThroughDurableIntent() {
        val base = document()
        val mutations = listOf<CanonicalFinanceMutation>(
            AppendCanonicalEvent(event("evt-new", "Νέα"), NOW),
            EditCanonicalActivity("evt-existing", "Νέα σημείωση", "Νέα κατηγορία", NOW),
            DeleteCanonicalActivity("evt-existing", NOW),
            UpsertOverallBudget("2026-09", 420.50, 73, "budget-new", NOW),
            DeactivateCanonicalCard("card-1", NOW),
        )

        mutations.forEachIndexed { index, mutation ->
            val intent = PendingCanonicalMutationIntent.fromMutation(mutation, "intent-$index")
            assertEquals(mutation.apply(base).raw, intent.asMutation().apply(base).raw)
        }
    }

    @Test
    fun stableEventIdReplay_isIdempotentAndCannotCreateDuplicateEvent() {
        val pending = PendingCanonicalMutationIntent.fromMutation(
            AppendCanonicalEvent(event("evt-stable", "Offline"), NOW),
            intentId = "append-stable",
        )

        val once = pending.asMutation().apply(document())
        val twice = pending.asMutation().apply(once)

        assertEquals(
            1,
            twice.state.array("events").count { (it as? JsonObject)?.string("id") == "evt-stable" },
        )
    }

    @Test
    fun freshServerState_reconcilesAlreadySatisfiedMutationsWithoutReplay() {
        val append = PendingCanonicalMutationIntent.fromMutation(
            AppendCanonicalEvent(event("evt-new", "Νέα"), NOW),
            "append",
            PendingMutationSyncState.NEEDS_REVIEW,
        )
        val edit = PendingCanonicalMutationIntent.fromMutation(
            EditCanonicalActivity("evt-existing", "Edited", "Food", NOW),
            "edit",
            PendingMutationSyncState.NEEDS_REVIEW,
        )
        val budget = PendingCanonicalMutationIntent.fromMutation(
            UpsertOverallBudget("2026-09", 500.0, 80, "budget-new", NOW),
            "budget",
            PendingMutationSyncState.NEEDS_REVIEW,
        )
        val card = PendingCanonicalMutationIntent.fromMutation(
            DeactivateCanonicalCard("card-1", NOW),
            "card",
            PendingMutationSyncState.NEEDS_REVIEW,
        )
        val delete = PendingCanonicalMutationIntent.fromMutation(
            DeleteCanonicalActivity("evt-delete", NOW),
            "delete",
            PendingMutationSyncState.NEEDS_REVIEW,
        )
        val pending = listOf(append, edit, budget, card, delete)
        val server = pending.fold(document()) { current, item -> item.asMutation().apply(current) }

        assertTrue(reconcileSatisfiedPendingMutations(server, pending).isEmpty())
    }

    @Test
    fun reconciliation_doesNotDropLaterDeleteBehindUnresolvedAmbiguousCreate() {
        val append = PendingCanonicalMutationIntent.fromMutation(
            AppendCanonicalEvent(event("evt-ambiguous-local", "Temporary"), NOW),
            "append-ambiguous",
            PendingMutationSyncState.NEEDS_REVIEW,
        )
        val delete = PendingCanonicalMutationIntent.fromMutation(
            DeleteCanonicalActivity("evt-ambiguous-local", NOW),
            "delete-unsent",
            PendingMutationSyncState.NEVER_SENT,
        )
        val queue = listOf(append, delete)

        // The fresh server does not contain the event. The delete looks satisfied in isolation,
        // but it depends on the unresolved create and must remain queued behind it.
        val remaining = reconcileSatisfiedPendingMutations(document(), queue)

        assertEquals(listOf("append-ambiguous", "delete-unsent"), remaining.map { it.intentId })
    }

    @Test
    fun reconciliation_removesOnlySatisfiedPrefixAndPreservesFollowingIntent() {
        val append = PendingCanonicalMutationIntent.fromMutation(
            AppendCanonicalEvent(event("evt-prefix", "Created"), NOW),
            "append",
            PendingMutationSyncState.NEEDS_REVIEW,
        )
        val delete = PendingCanonicalMutationIntent.fromMutation(
            DeleteCanonicalActivity("evt-prefix", NOW),
            "delete",
            PendingMutationSyncState.NEVER_SENT,
        )
        val serverWithCreate = append.asMutation().apply(document())

        val remaining = reconcileSatisfiedPendingMutations(serverWithCreate, listOf(append, delete))

        assertEquals(listOf("delete"), remaining.map { it.intentId })
    }

    @Test
    fun ambiguousAttempt_isNeverEligibleForAutomaticReplay() {
        val neverSent = PendingCanonicalMutationIntent.fromMutation(
            DeleteCanonicalActivity("evt-existing", NOW),
            "never-sent",
        )
        val needsReview = PendingCanonicalMutationIntent.fromMutation(
            UpsertOverallBudget("2026-09", 450.0, 75, "budget", NOW),
            "needs-review",
            PendingMutationSyncState.NEEDS_REVIEW,
        )

        val automaticReplay = listOf(neverSent, needsReview).filter {
            it.syncState == PendingMutationSyncState.NEVER_SENT
        }

        assertEquals(listOf("never-sent"), automaticReplay.map(PendingCanonicalMutationIntent::intentId))
        assertFalse(needsReview in automaticReplay)
    }

    @Test
    fun deletingNeverSentLocalAppend_compactsCreateEditDeleteToNothing() {
        val append = PendingCanonicalMutationIntent.fromMutation(
            AppendCanonicalEvent(event("evt-local", "Original"), NOW),
            "append",
        )
        val edit = PendingCanonicalMutationIntent.fromMutation(
            EditCanonicalActivity("evt-local", "Edited", "Food", NOW),
            "edit",
        )
        val delete = PendingCanonicalMutationIntent.fromMutation(
            DeleteCanonicalActivity("evt-local", NOW),
            "delete",
        )

        val afterAppend = compactPendingMutationIntents(emptyList(), append)
        val afterEdit = compactPendingMutationIntents(afterAppend, edit)
        val afterDelete = compactPendingMutationIntents(afterEdit, delete)

        assertTrue(afterDelete.isEmpty())
    }

    @Test
    fun repeatedEditAndBudget_compactOnlyNeverSentOlderIntent() {
        val edit1 = PendingCanonicalMutationIntent.fromMutation(
            EditCanonicalActivity("evt-existing", "One", "A", NOW),
            "edit-1",
        )
        val edit2 = PendingCanonicalMutationIntent.fromMutation(
            EditCanonicalActivity("evt-existing", "Two", "B", NOW),
            "edit-2",
        )
        val budget1 = PendingCanonicalMutationIntent.fromMutation(
            UpsertOverallBudget("2026-09", 100.0, 60, "budget-1", NOW),
            "budget-1",
        )
        val budget2 = PendingCanonicalMutationIntent.fromMutation(
            UpsertOverallBudget("2026-09", 200.0, 70, "budget-2", NOW),
            "budget-2",
        )

        var queue = compactPendingMutationIntents(emptyList(), edit1)
        queue = compactPendingMutationIntents(queue, edit2)
        queue = compactPendingMutationIntents(queue, budget1)
        queue = compactPendingMutationIntents(queue, budget2)

        assertEquals(listOf("edit-2", "budget-2"), queue.map(PendingCanonicalMutationIntent::intentId))
    }

    @Test
    fun deleteIsSatisfiedWhenTransactionIsAlreadyAbsent() {
        val intent = PendingCanonicalMutationIntent.fromMutation(
            DeleteCanonicalActivity("does-not-exist", NOW),
            "delete",
            PendingMutationSyncState.NEEDS_REVIEW,
        )
        assertTrue(intent.isSatisfiedBy(document()))
    }

    private fun document(): CanonicalFinanceDocument = CanonicalFinanceDocument(
        Json.parseToJsonElement(
            """
            {
              "schemaVersion":3,
              "updatedAt":"2026-09-04T08:00:00Z",
              "seed":{"accounts":[],"transactions":[]},
              "state":{
                "events":[
                  {"id":"evt-existing","date":"2026-09-04","kind":"expense","amount":10.0,"note":"Original","category":"Old"},
                  {"id":"evt-delete","date":"2026-09-04","kind":"expense","amount":7.0,"note":"Delete me"}
                ],
                "cards":[{"id":"card-1","active":true,"nickname":"Test"}],
                "budgets":[{"id":"budget-old","month":"2026-09","scope":"overall","amount":300.0,"alertThreshold":60,"createdAt":"$NOW","updatedAt":"$NOW"}]
              }
            }
            """.trimIndent(),
        ).jsonObject,
    )

    private fun event(id: String, note: String): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "date" to JsonPrimitive("2026-09-04"),
            "kind" to JsonPrimitive("expense"),
            "amount" to JsonPrimitive(5.0),
            "note" to JsonPrimitive(note),
        ),
    )

    private companion object {
        const val NOW = "2026-09-04T08:00:00Z"
    }
}
