package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Web src/lib/loans.ts / ContextualQuickAdd.tsx: loanId and installmentCount
 * decorate existing expense, transfer, card_payment events, not a new EventKind.
 */
class WebLinkedLoanPaymentMutationParityTest {
    private val now = "2026-10-17T10:00:00Z"
    private val document = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[
        {"id":"salary","kind":"bank","bankAccountCategory":"payroll","providerId":"piraeus"},
        {"id":"savings","kind":"bank","bankAccountCategory":"savings"}
       ],"snapshots":[{"date":"2026-10-01",
          "balances":{"salary":900,"savings":200}}],
       "loans":[
         {"id":"regular","name":"Laptop","total":300,"installment":100,
          "installments":3,"paidCount":1,"kind":"installment"},
         {"id":"liability","name":"Υποχρέωση","total":240,"installment":80,
          "installments":3,"accountingMode":"liability-repayment"},
         {"id":"help","name":"ΒΟΗΘΕΙΑ","total":200,"installment":200,
          "installments":1,"kind":"self-loan","source":"self-loan",
          "forgivenAmount":50}
       ],"transactions":[]},"state":{"events":[],"settings":{}}}
    """.trimIndent()).jsonObject)

    private fun raw(doc: CanonicalFinanceDocument): JsonObject =
        doc.state.array("events").single() as JsonObject

    @Test fun regularLoanPaymentClampsRequestedCountAndUsesExistingExpenseEvent() {
        val plan = document.canonicalLoanInstallmentPaymentPlan("regular",99,"2026-10-17")!!
        assertEquals(2,plan.count)
        assertEquals(2,plan.firstInstallment)
        assertEquals(3,plan.lastInstallment)
        assertEquals(200.0,plan.amount,0.001)
        val mutation = createCanonicalLoanPaymentMutation(document,"regular","2026-10-17",
            "salary","loan-regular-1",now,requestedInstallments=99)
        val paid = mutation.apply(document)
        val event = paid.canonicalEvents().single()
        assertEquals("expense",event.kind)
        assertEquals("regular",event.loanId)
        assertEquals(2.0,raw(paid).number("installmentCount")!!,0.001)
        assertEquals("Δόσεις / δάνεια",event.category)
        assertEquals(-200.0,event.legs.single().amount,0.001)
        assertEquals(0.0,paid.canonicalLoanBalanceRows("2026-10-17")
            .first { it.id=="regular" }.remaining,0.001)
        assertEquals(0,paid.canonicalLoanBalanceRows("2026-10-17")
            .first { it.id=="regular" }.remainingInstallments)
        assertEquals(700.0,paid.accountBalances("2026-10-17")["salary"]!!,0.001)
        assertEquals(paid.raw,mutation.apply(paid).raw)
    }

    @Test fun normalInstallmentLeavesBalanceAndDoesNotBackdateIntoEarlierSnapshot() {
        val paid = createCanonicalLoanPaymentMutation(document,"regular","2026-10-17",
            "salary","loan-single",now).apply(document)
        assertEquals(100.0,paid.canonicalLoanBalanceRows("2026-10-17")
            .first { it.id=="regular" }.remaining,0.001)
        assertEquals(1,paid.canonicalLoanBalanceRows("2026-10-17")
            .first { it.id=="regular" }.remainingInstallments)
        assertEquals(200.0,document.canonicalLoanBalanceRows("2026-10-17")
            .first { it.id=="regular" }.remaining,0.001)
        assertEquals(200.0,paid.canonicalLoanBalanceRows("2026-10-16")
            .first { it.id=="regular" }.remaining,0.001)
        assertEquals(100.0,paid.monthlyFlow("2026-10").expense,0.001)
        assertEquals("regular",paid.canonicalEvents().single().loanId)
    }

    @Test fun liabilityModeIsCardPaymentLinkedToLoanButNotToUnrelatedCard() {
        val paid = createCanonicalLoanPaymentMutation(document,"liability","2026-10-17",
            "salary","loan-liability",now).apply(document)
        val event = paid.canonicalEvents().single()
        assertEquals("card_payment",event.kind)
        assertEquals("liability",event.loanId)
        assertNull(event.cardId)
        assertEquals(1.0,raw(paid).number("installmentCount")!!,0.001)
        assertEquals(-80.0,event.legs.first().amount,0.001)
        assertEquals(80.0,event.legs.last().amount,0.001)
        assertEquals(80.0,event.creditDelta,0.001)
        assertEquals(160.0,paid.canonicalLoanBalanceRows("2026-10-17")
            .first { it.id=="liability" }.remaining,0.001)
        assertEquals(0.0,paid.monthlyFlow("2026-10").expense,0.001)
        assertEquals(paid.raw,createCanonicalLoanPaymentMutation(document,"liability","2026-10-17",
            "salary","loan-liability",now).apply(paid).raw)
    }

    @Test fun selfLoanReturnsMoneyToSavingsAndCountsOnlyRecognizableTransfer() {
        val paid = createCanonicalLoanPaymentMutation(document,"help","2026-10-17",
            "salary","help-return",now,selfLoanAmount=60.0).apply(document)
        val event = paid.canonicalEvents().single()
        assertEquals("transfer",event.kind)
        assertEquals("help",event.loanId)
        assertTrue(event.note.startsWith("ΕΠΙΣΤΡΟΦΗ:"))
        assertEquals(2,event.legs.size)
        assertEquals(-60.0,event.legs.first().amount,0.001)
        assertEquals(60.0,event.legs.last().amount,0.001)
        assertEquals(840.0,paid.accountBalances("2026-10-17")["salary"]!!,0.001)
        assertEquals(260.0,paid.accountBalances("2026-10-17")["savings"]!!,0.001)
        assertEquals(90.0,paid.canonicalLoanBalanceRows("2026-10-17")
            .first { it.id=="help" }.remaining,0.001)
        assertEquals(0.0,paid.monthlyFlow("2026-10").expense,0.001)
    }

    @Test fun rejectsForgeryOverpaymentWrongAccountAndUnlinkedInstallmentCount() {
        fun bad(draft: CanonicalTransactionEntryDraft) = assertThrows(
            IllegalArgumentException::class.java
        ) { createCanonicalTransactionEntryMutation(document,draft,"invalid",now) }
        bad(CanonicalTransactionEntryDraft(kind="expense",date="2026-10-17",
            amount=100.0,accountId="salary",loanId="unknown",installmentCount=1))
        bad(CanonicalTransactionEntryDraft(kind="expense",date="2026-10-17",
            amount=200.0,accountId="salary",loanId="regular",installmentCount=1))
        bad(CanonicalTransactionEntryDraft(kind="expense",date="2026-10-17",
            amount=100.0,accountId="salary",installmentCount=1))
        bad(CanonicalTransactionEntryDraft(kind="transfer",date="2026-10-17",
            amount=60.0,fromAccountId="salary",toAccountId="salary",loanId="help",
            note="ΕΠΙΣΤΡΟΦΗ: ΒΟΗΘΕΙΑ"))
        bad(CanonicalTransactionEntryDraft(kind="card_payment",date="2026-10-17",
            amount=80.0,fromAccountId="salary",loanId="liability",
            cardId="unrelated-card",installmentCount=1))
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalLoanPaymentMutation(document,"help","2026-10-17",
                "salary","over-help",now,selfLoanAmount=151.0)
        }
    }
}
