package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** Historical duplicate scheduled records are read-only; never guess an ID target. */
class WebScheduledDuplicateIdentitySafetyTest {
    private val time = "2026-10-17T14:00:00Z"
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[{"id":"salary","kind":"bank","bankAccountCategory":"payroll"}],
        "snapshots":[{"date":"2026-10-01","balances":{"salary":500}}],
        "transactions":[]},"state":{"events":[],"scheduled":[
        {"id":"same","kind":"expense","dueDate":"2026-10-10","amount":30,
         "accountId":"salary","note":"Α","status":"pending","createdAt":"2026-09-01T00:00:00Z"},
        {"id":"same","kind":"expense","dueDate":"2026-10-12","amount":40,
         "accountId":"salary","note":"Β","status":"pending","createdAt":"2026-09-02T00:00:00Z"}
      ]}}
    """.trimIndent()).jsonObject)
    @Test fun historicalRowsRemainVisibleAndDoNotChangeBalances() {
        assertEquals(listOf("Α","Β"),doc.canonicalScheduledManagementOrder().map { it.note })
        assertEquals(500.0,doc.accountBalances("2026-10-17")["salary"]!!,0.001)
        assertEquals(2,doc.canonicalScheduled().size)
    }
    @Test fun ambiguousCreateCompleteSkipCancelAndOfflineReplaysFailClosed() {
        val actions = listOf<CanonicalFinanceMutation>(
            CreateCanonicalScheduledTransaction("same",CanonicalScheduledDraft(
                "expense","2026-10-10",30.0,note="Α",accountId="salary"),"2026-09-01T00:00:00Z"),
            CompleteCanonicalScheduledTransaction("same","pay-1","2026-10-17",time),
            CloseCanonicalScheduledTransaction("same","skipped",time),
            CloseCanonicalScheduledTransaction("same","cancelled",time),
        )
        for ((index,action) in actions.withIndex()) {
            assertThrows(IllegalArgumentException::class.java) { action.apply(doc) }
            val intent=PendingCanonicalMutationIntent.fromMutation(action,"q-$index")
            assertFalse(intent.isSatisfiedBy(doc))
            assertThrows(IllegalArgumentException::class.java) { intent.asMutation().apply(doc) }
        }
        assertEquals(0,doc.canonicalEvents().size)
        assertEquals(2,doc.canonicalScheduled().size)
    }
    @Test fun duplicateStatusesNeverFalselyReconcileAnOfflineIntent() {
        val altered = CanonicalFinanceDocument(JsonObject(doc.raw + ("state" to
            doc.state.updated("scheduled",Json.parseToJsonElement("""
            [{"id":"same","status":"completed","completedEventId":"pay-1","kind":"expense",
              "dueDate":"2026-10-10","amount":30,"accountId":"salary"},
             {"id":"same","status":"skipped","kind":"expense","dueDate":"2026-10-12",
              "amount":40,"accountId":"salary"}]
            """.trimIndent())))))
        val skip=PendingCanonicalMutationIntent.fromMutation(
            CloseCanonicalScheduledTransaction("same","skipped",time),"skip")
        val done=PendingCanonicalMutationIntent.fromMutation(
            CompleteCanonicalScheduledTransaction("same","pay-1","2026-10-17",time),"done")
        assertFalse(skip.isSatisfiedBy(altered))
        assertFalse(done.isSatisfiedBy(altered))
        assertEquals(listOf(skip,done),reconcileSatisfiedPendingMutations(altered,listOf(skip,done)))
    }
}
