package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web historical loans may share IDs; Compose list keys must not collide or link unsafe writes. */
class WebDuplicateLoanRowUiSafetyTest {
    private fun row(id: String, remaining: Double=100.0, remainingCount: Int=1) =
        LoanItem(id=id,name="Legacy ${id}",lender="",remaining=remaining,
            monthlyPayment=50.0,nextPaymentLabel="",originalAmount=100.0,
            remainingInstallments=remainingCount)

    @Test fun repeatedHistoricalIdsRenderDistinctRowsWithoutComposeKeyCollisions() {
        val rows=listOf(row("dup"),row("dup"),row("another"),row("dup#row#0"))
        assertEquals(rows.size,rows.mapIndexed { index, loan -> canonicalLoanRowKey(loan, index) }.distinct().size)
        assertEquals("dup#row#0",canonicalLoanRowKey(rows.first(),0))
        assertEquals("dup#row#1",canonicalLoanRowKey(rows[1],1))
    }

    @Test fun duplicateIdCannotOpenAmbiguousRepaymentWhereSingleRemainingLoanCan() {
        val a=row("dup")
        val b=row("dup",remaining=20.0)
        val safe=row("safe")
        val list=listOf(a,b,safe)
        assertFalse(canonicalLoanPaymentIsUnambiguous(a,list))
        assertFalse(canonicalLoanPaymentIsUnambiguous(b,list))
        assertTrue(canonicalLoanPaymentIsUnambiguous(safe,list))
        assertFalse(canonicalLoanPaymentIsUnambiguous(row("paid",remaining=0.0),
            listOf(row("paid",remaining=0.0))))
        assertFalse(canonicalLoanPaymentIsUnambiguous(row("no-count",remainingCount=0),
            listOf(row("no-count",remainingCount=0))))
    }
}
