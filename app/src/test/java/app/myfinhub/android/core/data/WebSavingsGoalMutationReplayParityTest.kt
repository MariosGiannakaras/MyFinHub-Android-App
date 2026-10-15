package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web owner-approved goals and offline intent consistency. */
class WebSavingsGoalMutationReplayParityTest {
    private val now="2026-10-15T10:30:00Z"
    private val base=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[{"id":"saving","kind":"savings"}]},
       "state":{"desktopUnknown":"keep","events":[{"id":"finance","amount":42}],
         "savingsGoals":[
           {"id":"old","name":"Emergency","targetAmount":100,"targetDate":"2026-08-01",
            "createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-09-01T00:00:00Z",
            "webUnknown":"keep"}
         ]}}
    """.trimIndent()).jsonObject)

    @Test fun updatePreservesPastUnchangedDateCreationTimeUnknownFieldsAndFinanceHistory() {
        val mutation=UpsertCanonicalSavingsGoal("old","Νέο όνομα",140.129,
            "2026-08-01","2026-10-15",now)
        val result=mutation.apply(base)
        val goal=result.state.array("savingsGoals").single() as JsonObject
        assertEquals("Νέο όνομα",goal.string("name"))
        assertEquals(140.13,goal.number("targetAmount")!!,0.001)
        assertEquals("2026-08-01",goal.string("targetDate"))
        assertEquals("2026-01-01T00:00:00Z",goal.string("createdAt"))
        assertEquals("keep",goal.string("webUnknown"))
        assertEquals(base.state.array("events"),result.state.array("events"))
        assertEquals("keep",result.state.string("desktopUnknown"))
        assertEquals(result.raw,mutation.apply(result).raw)
    }

    @Test fun newPastDateAndChangedHistoricalDeadlineAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            UpsertCanonicalSavingsGoal("new","Goal",100.0,"2026-10-14","2026-10-15",now).apply(base)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpsertCanonicalSavingsGoal("old","Goal",100.0,"2026-09-01","2026-10-15",now).apply(base)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpsertCanonicalSavingsGoal("old","Goal",0.0,null,"2026-10-15",now).apply(base)
        }
    }

    @Test fun offlineCreateOrEditRemainsStableAcrossReencodeReapplyAndLaterReconciliation() {
        val mutation=UpsertCanonicalSavingsGoal("new","Ταξίδι",600.0,
            "2026-12-31","2026-10-15",now)
        val queued=PendingCanonicalMutationIntent.fromMutation(mutation,"goal-intent")
        assertEquals(PendingMutationKind.UPSERT_SAVINGS_GOAL,queued.kind)
        assertEquals(mutation,queued.asMutation())
        assertFalse(queued.isSatisfiedBy(base))
        val result=queued.asMutation().apply(base)
        assertTrue(queued.isSatisfiedBy(result))
        assertEquals(result.raw,queued.asMutation().apply(result).raw)
        assertTrue(reconcileSatisfiedPendingMutations(result,listOf(queued)).isEmpty())
    }

    @Test fun offlineDeleteOnlyRemovesOneGoalAndMissingGoalIsIdempotentlySettled() {
        val before=UpsertCanonicalSavingsGoal("new","Trip",200.0,null,"2026-10-15",now).apply(base)
        val intent=PendingCanonicalMutationIntent.fromMutation(
            DeleteCanonicalSavingsGoal("old",now),"delete-goal")
        assertFalse(intent.isSatisfiedBy(before))
        val result=intent.asMutation().apply(before)
        assertTrue(intent.isSatisfiedBy(result))
        assertEquals(listOf("new"),result.state.array("savingsGoals")
            .map { (it as JsonObject).string("id") })
        assertEquals(before.state.array("events"),result.state.array("events"))
        assertEquals(result.raw,intent.asMutation().apply(result).raw)
    }
}
