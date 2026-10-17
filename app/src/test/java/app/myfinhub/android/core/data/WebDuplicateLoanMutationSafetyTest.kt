package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Web effectiveLoans preserves duplicate source records. An ambiguous linked
 * payment must not reduce every matching historical loan simultaneously.
 */
class WebDuplicateLoanMutationSafetyTest {
    private val now="2026-10-17T11:00:00Z"
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[
        {"id":"salary","kind":"bank","bankAccountCategory":"payroll"},
        {"id":"savings","kind":"savings"}
      ],"snapshots":[{"date":"2026-10-01",
        "balances":{"salary":500,"savings":100}}],
       "loans":[
        {"id":"duplicate","name":"First","total":200,"installment":100,"installments":2},
        {"id":"duplicate","name":"Second","total":300,"installment":100,"installments":3},
        {"id":"help","name":"ΒΟΗΘΕΙΑ","kind":"self-loan","total":60,"installment":60,"installments":1},
        {"id":"safe","name":"Unique","total":50,"installment":50,"installments":1}
       ],"transactions":[]},"state":{
        "customLoans":[
          {"id":"help","name":"Second HELP","kind":"self-loan",
           "total":100,"installment":100,"installments":1}
        ],"events":[],"settings":{}}}
    """.trimIndent()).jsonObject)

    @Test fun duplicateHistoricalRowsAreVisibleButNoAmbiguousInstallmentPlanIsAvailable() {
        assertEquals(2,doc.canonicalLoanBalanceRows().count { it.id=="duplicate" })
        assertNull(doc.canonicalLoanInstallmentPaymentPlan("duplicate",1,"2026-10-17"))
        assertEquals(1,doc.canonicalLoanInstallmentPaymentPlan("safe",1,"2026-10-17")!!.count)
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalLoanPaymentMutation(doc,"duplicate","2026-10-17",
                "salary","unsafe",now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalTransactionEntryMutation(doc,CanonicalTransactionEntryDraft(
                kind="expense",date="2026-10-17",amount=100.0,
                accountId="salary",loanId="duplicate",installmentCount=1
            ),"forged",now)
        }
        assertEquals(500.0,doc.accountBalances("2026-10-17")["salary"]!!,0.001)
    }

    @Test fun duplicateHelpLoanCannotTransferMoneyThroughSameAmbiguousLoanId() {
        assertEquals(2,doc.canonicalLoanBalanceRows().count { it.id=="help" })
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalLoanPaymentMutation(doc,"help","2026-10-17","salary",
                "duplicate-help",now,selfLoanAmount=10.0,savingsAccountId="savings")
        }
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalTransactionEntryMutation(doc,CanonicalTransactionEntryDraft(
                kind="transfer",date="2026-10-17",amount=10.0,
                fromAccountId="salary",toAccountId="savings",
                note="ΕΠΙΣΤΡΟΦΗ: ΒΟΗΘΕΙΑ",loanId="help",
            ),"forged-help",now)
        }
    }

    @Test fun uniquelyIdentifiedLoanPaymentStillPostsNormallyWithoutDoubleCounting() {
        val action=createCanonicalLoanPaymentMutation(doc,"safe","2026-10-17",
            "salary","safe-event",now)
        val paid=action.apply(doc)
        assertEquals(0.0,paid.canonicalLoanBalanceRows("2026-10-17")
            .first { it.id=="safe" }.remaining,0.001)
        assertEquals(450.0,paid.accountBalances("2026-10-17")["salary"]!!,0.001)
        assertEquals(paid.raw,action.apply(paid).raw)
        assertEquals(2,paid.canonicalLoanBalanceRows("2026-10-17").count {
            it.id=="duplicate"
        })
    }
}
