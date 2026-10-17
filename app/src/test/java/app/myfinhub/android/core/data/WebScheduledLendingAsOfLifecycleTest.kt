package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web lending.ts / scheduled.ts goldens for as-of limits and atomic finance transitions. */
class WebScheduledLendingAsOfLifecycleTest {
    private val at="2026-10-17T11:00:00Z"
    private val base=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[
        {"id":"salary","kind":"bank","bankAccountCategory":"payroll"},
        {"id":"savings","kind":"savings"}
      ],"snapshots":[{"date":"2026-10-01","balances":{"salary":500,"savings":200}}],
        "lending":[{"person":"Μαρία","outstanding":30,"entries":[]}],
        "transactions":[],"recurring":[],"loans":[]},
       "state":{"events":[
         {"id":"repaid","date":"2026-10-10","kind":"repayment","amount":10,"person":"Μαρία",
          "receivableDelta":-10,"legs":[{"accountId":"salary","amount":10}]},
         {"id":"future","date":"2026-11-30","kind":"lending","amount":80,"person":"Μαρία",
          "receivableDelta":80,"legs":[{"accountId":"salary","amount":-80}]},
         {"id":"undated","kind":"lending","amount":100,"person":"Μαρία",
          "receivableDelta":100,"legs":[]}
       ],"scheduled":[
         {"id":"rent","dueDate":"2026-10-14","kind":"expense","amount":40,
          "accountId":"salary","note":"Νοίκι","status":"pending"},
         {"id":"bonus","dueDate":"2026-10-19","kind":"income","amount":90,
          "accountId":"salary","note":"Bonus","status":"pending"},
         {"id":"move","dueDate":"2026-10-18","kind":"transfer","amount":50,
          "fromAccountId":"salary","toAccountId":"savings",
          "note":"Μεταφορά","status":"pending"}
       ],"settings":{}}}
    """.trimIndent()).jsonObject)

    @Test fun repaymentUsesOutstandingOnDraftDateIgnoringFutureAndUndatedDeltas() {
        assertEquals(20.0,base.lendingOutstandingForPerson(" Μαρία ","2026-10-17"),0.001)
        assertEquals(200.0,base.lendingOutstandingForPerson("Μαρία"),0.001)
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalTransactionEntryMutation(base,CanonicalTransactionEntryDraft(
                kind="repayment",date="2026-10-17",amount=25.0,accountId="salary",person="Μαρία"
            ),"invalid",at)
        }
        val mutation=createCanonicalTransactionEntryMutation(base,CanonicalTransactionEntryDraft(
            kind="repayment",date="2026-10-17",amount=20.0,accountId="salary",person="Μαρία"
        ),"valid",at)
        val paid=mutation.apply(base)
        assertEquals(0.0,paid.lendingOutstandingForPerson("Μαρία","2026-10-17"),0.001)
        assertEquals(paid.raw,mutation.apply(paid).raw)
    }

    @Test fun completingOverdueExpenseMovesMoneyAndExcludesPendingForecastOnce() {
        val initial=base.cashFlowForecast(LocalDate.parse("2026-10-17"),30)
        assertEquals(3,initial.sourceCounts["scheduled"])
        assertEquals(710.0,initial.currentPortfolio,0.001)
        assertEquals(760.0,initial.projectedPortfolio,0.001)
        val action=CompleteCanonicalScheduledTransaction("rent","rent-event","2026-10-17",at)
        val done=action.apply(base)
        assertEquals("completed",done.canonicalScheduled().first { it.id=="rent" }.status)
        assertEquals(-40.0,done.canonicalEvents().first { it.id=="rent-event" }.legs.single().amount,0.001)
        val after=done.cashFlowForecast(LocalDate.parse("2026-10-17"),30)
        assertEquals(670.0,after.currentPortfolio,0.001)
        assertEquals(760.0,after.projectedPortfolio,0.001)
        assertEquals(2,after.sourceCounts["scheduled"])
        assertEquals(done.raw,action.apply(done).raw)
        assertThrows(IllegalArgumentException::class.java) {
            CompleteCanonicalScheduledTransaction("rent","another","2026-10-17",at).apply(done)
        }
    }

    @Test fun transferOverrideAndSkipNeverDoubleCountPortfolio() {
        val action=CompleteCanonicalScheduledTransaction("move","transfer-event","2026-10-18",at,
            amount=45.0)
        val done=action.apply(base)
        val event=done.canonicalEvents().first { it.id=="transfer-event" }
        assertEquals(listOf(-45.0,45.0),event.legs.map { it.amount })
        assertEquals(465.0,done.accountBalances("2026-10-18")["salary"]!!,0.001)
        assertEquals(245.0,done.accountBalances("2026-10-18")["savings"]!!,0.001)
        assertEquals(0.0,event.legs.sumOf { it.amount },0.001)
        assertEquals(done.raw,action.apply(done).raw)
        val skipped=CloseCanonicalScheduledTransaction("bonus","skipped",at).apply(base)
        assertEquals(2,skipped.cashFlowForecast(LocalDate.parse("2026-10-17"),30)
            .sourceCounts["scheduled"])
        assertEquals(base.state.array("events"),skipped.state.array("events"))
        assertEquals(skipped.raw,CloseCanonicalScheduledTransaction("bonus","skipped",at).apply(skipped).raw)
        assertThrows(IllegalArgumentException::class.java) {
            CompleteCanonicalScheduledTransaction("bonus","late","2026-10-17",at).apply(skipped)
        }
    }

    @Test fun durableOfflineScheduledIntentsRoundTripAndReconcileWithServer() {
        val create=CreateCanonicalScheduledTransaction("offline-created",CanonicalScheduledDraft(
            kind="expense",dueDate="2026-10-30",amount=25.0,accountId="salary"
        ),at)
        val saved=PendingCanonicalMutationIntent.fromMutation(create,"local-create")
        assertEquals(PendingMutationKind.CREATE_SCHEDULED,saved.kind)
        val server=saved.asMutation().apply(base)
        assertEquals(server.raw,create.apply(base).raw)
        assertTrue(saved.isSatisfiedBy(server))
        val complete=CompleteCanonicalScheduledTransaction("offline-created","offline-paid",
            "2026-10-30",at)
        val completedIntent=PendingCanonicalMutationIntent.fromMutation(complete,"local-complete")
        assertEquals(PendingMutationKind.COMPLETE_SCHEDULED,completedIntent.kind)
        val paid=completedIntent.asMutation().apply(server)
        assertEquals("completed",paid.canonicalScheduled().first { it.id=="offline-created" }.status)
        assertTrue(completedIntent.isSatisfiedBy(paid))
        assertEquals(paid.raw,complete.apply(paid).raw)
        val skip=CloseCanonicalScheduledTransaction("bonus","skipped",at)
        val skipped=PendingCanonicalMutationIntent.fromMutation(skip,"local-skip")
        assertEquals(PendingMutationKind.CLOSE_SCHEDULED,skipped.kind)
        assertTrue(skipped.isSatisfiedBy(skipped.asMutation().apply(base)))
        assertEquals(2,reconcileSatisfiedPendingMutations(
            server,listOf(saved,completedIntent,skipped)).size)
        assertTrue(reconcileSatisfiedPendingMutations(paid,listOf(saved,completedIntent)).isEmpty())
    }

    @Test fun completionHonorsActualAccountOverrideAndDurableOfflineReplay() {
        // A scheduled account may have been closed; Web scheduledToEvent allows
        // selecting an eligible account when the real transaction is confirmed.
        val pay=CompleteCanonicalScheduledTransaction(
            "rent","actual-rent-from-savings","2026-10-17",at,
            amount=35.0,accountId="savings",
        )
        val intent=PendingCanonicalMutationIntent.fromMutation(pay,"rent-override")
        val paid=intent.asMutation().apply(base)
        val event=paid.canonicalEvents().first { it.id=="actual-rent-from-savings" }
        assertEquals("savings",event.legs.single().accountId)
        assertEquals(-35.0,event.legs.single().amount,0.001)
        assertEquals(510.0,paid.accountBalances("2026-10-17")["salary"]!!,0.001)
        assertEquals(165.0,paid.accountBalances("2026-10-17")["savings"]!!,0.001)
        assertTrue(intent.isSatisfiedBy(paid))
        assertEquals(paid.raw,intent.asMutation().apply(paid).raw)

        val reverse=CompleteCanonicalScheduledTransaction(
            "move","actual-savings-to-salary","2026-10-18",at,
            amount=25.0,fromAccountId="savings",toAccountId="salary",
        ).apply(base)
        val legs=reverse.canonicalEvents().first { it.id=="actual-savings-to-salary" }.legs
        assertEquals(listOf("savings","salary"),legs.map { it.accountId })
        assertEquals(listOf(-25.0,25.0),legs.map { it.amount })
        assertEquals(535.0,reverse.accountBalances("2026-10-18")["salary"]!!,0.001)
        assertEquals(175.0,reverse.accountBalances("2026-10-18")["savings"]!!,0.001)
        assertEquals(0.0,legs.sumOf { it.amount },0.001)
    }

    @Test fun scheduleCreationRoundsMoneyAndRejectsInvalidDraftsAndCollidingEvents() {
        val intent=CreateCanonicalScheduledTransaction("added",CanonicalScheduledDraft(
            kind="expense",dueDate="2026-10-31",amount=1.005,accountId="salary"
        ),at)
        val created=intent.apply(base)
        assertEquals(1.01,created.canonicalScheduled().first { it.id=="added" }.amount,0.001)
        assertEquals("upcoming",created.canonicalScheduled().first { it.id=="added" }
            .canonicalLifecycle("2026-10-17"))
        assertEquals("due",created.canonicalScheduled().first { it.id=="added" }
            .canonicalLifecycle("2026-10-31"))
        assertEquals(created.raw,intent.apply(created).raw)
        for(d in listOf(
            CanonicalScheduledDraft("expense","2026-02-30",10.0,accountId="salary"),
            CanonicalScheduledDraft("expense","2026-10-30",10.0,accountId="missing"),
            CanonicalScheduledDraft("transfer","2026-10-30",10.0,fromAccountId="salary",toAccountId="salary"),
            CanonicalScheduledDraft("expense","2026-10-30",Double.POSITIVE_INFINITY,accountId="salary")
        ))assertThrows(IllegalArgumentException::class.java) {
            CreateCanonicalScheduledTransaction("invalid",d,at).apply(base)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CompleteCanonicalScheduledTransaction("rent","repaid","2026-10-17",at).apply(base)
        }
        assertTrue(base.canonicalScheduled().all { it.status=="pending" })
    }
}
