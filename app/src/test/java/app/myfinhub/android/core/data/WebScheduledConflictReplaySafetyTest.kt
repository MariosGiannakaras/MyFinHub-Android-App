package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web scheduled.ts create identity and offline reconciliation collision goldens. */
class WebScheduledConflictReplaySafetyTest {
    private val now = "2026-10-17T15:00:00Z"
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{
        "accounts":[
          {"id":"payroll","kind":"bank","bankAccountCategory":"payroll"},
          {"id":"cash","kind":"cash"},
          {"id":"savings","kind":"savings"}
        ],
        "transactions":[]},
       "state":{"scheduled":[],"events":[]}}
    """.trimIndent()).jsonObject)

    private fun scheduled(
        account: String = "payroll",
        note: String = "Ενοίκιο",
        category: String = "Στέγαση",
        timestamp: String = now,
    ): CreateCanonicalScheduledTransaction = CreateCanonicalScheduledTransaction(
        scheduledId = "schedule-1",
        draft = CanonicalScheduledDraft(
            kind = "expense", dueDate = "2026-11-01", amount = 120.0,
            note = note, category = category, accountId = account,
        ),
        nowIso = timestamp,
    )

    @Test fun exactCreateReplaysWithStableIdAfterPendingIntentRoundtrip() {
        val mutation = scheduled()
        val pending = PendingCanonicalMutationIntent.fromMutation(mutation,"queued")
        val once = pending.asMutation().apply(doc)
        assertEquals(once.raw,pending.asMutation().apply(once).raw)
        assertEquals(1,once.canonicalScheduled().size)
        assertTrue(pending.isSatisfiedBy(once))
        assertTrue(reconcileSatisfiedPendingMutations(once,listOf(pending)).isEmpty())
    }

    @Test fun conflictingAccountWithSameIdAmountAndDateCannotBeDroppedOrSilentlyIgnored() {
        val previouslyCommitted=scheduled(account="cash").apply(doc)
        val intended=scheduled()
        val pending=PendingCanonicalMutationIntent.fromMutation(intended,"queued",
            PendingMutationSyncState.NEEDS_REVIEW)
        assertFalse(pending.isSatisfiedBy(previouslyCommitted))
        assertEquals(listOf(pending),reconcileSatisfiedPendingMutations(previouslyCommitted,listOf(pending)))
        assertThrows(IllegalArgumentException::class.java) {
            pending.asMutation().apply(previouslyCommitted)
        }
        assertEquals(0,previouslyCommitted.state.array("events").size)
    }

    @Test fun conflictingTimestampCategoryOrNoteCannotImpersonateQueuedCreate() {
        val original=scheduled().apply(doc)
        for(variant in listOf(
            scheduled(note="Εντελώς διαφορετικό"),
            scheduled(category="Άλλο"),
            scheduled(timestamp="2026-10-18T15:00:00Z"),
        )){
            val pending=PendingCanonicalMutationIntent.fromMutation(variant,"queued")
            assertFalse(pending.isSatisfiedBy(original))
            assertThrows(IllegalArgumentException::class.java) {
                variant.apply(original)
            }
        }
        assertEquals(original.raw,scheduled().apply(original).raw)
    }

    @Test fun completedOrSkippedCanonicalRowStillProvesItsOriginalCreateIntent() {
        val intent=PendingCanonicalMutationIntent.fromMutation(scheduled(),"queued")
        val created=intent.asMutation().apply(doc)
        val skipped=CloseCanonicalScheduledTransaction("schedule-1","skipped",now).apply(created)
        assertTrue(intent.isSatisfiedBy(skipped))
        assertEquals(skipped.raw,intent.asMutation().apply(skipped).raw)
        assertEquals("skipped",skipped.canonicalScheduled().single().status)
        assertEquals(0,skipped.state.array("events").size)
    }

    @Test fun oldUnrelatedSameIdEventDoesNotSatisfyOrCompleteScheduledIntent() {
        val before=scheduled().apply(doc)
        val collision=CanonicalFinanceDocument(JsonObject(before.raw +
            ("state" to before.state.updated("events",Json.parseToJsonElement("""
                [{"id":"payment-1","kind":"expense","amount":5,
                  "date":"2026-10-15","legs":[]}]
            """.trimIndent())))))
        val intended=CompleteCanonicalScheduledTransaction("schedule-1","payment-1",
            "2026-10-17",now)
        val pending=PendingCanonicalMutationIntent.fromMutation(intended,"pending")
        assertFalse(pending.isSatisfiedBy(collision))
        assertThrows(IllegalArgumentException::class.java) {
            pending.asMutation().apply(collision)
        }
        assertEquals("pending",collision.canonicalScheduled().single().status)
    }
}
