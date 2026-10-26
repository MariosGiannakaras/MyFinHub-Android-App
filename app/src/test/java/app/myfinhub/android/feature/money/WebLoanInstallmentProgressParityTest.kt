package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure golden parity with Web loans.ts loanVisualInstallmentProgress. */
class WebLoanInstallmentProgressParityTest {
    @Test fun visibleSegmentsMatchRealCountAndClamping() {
        assertEquals(CanonicalLoanInstallmentVisualProgress(0,0),
            canonicalLoanVisualInstallmentProgress(0,6))
        assertEquals(CanonicalLoanInstallmentVisualProgress(12,7),
            canonicalLoanVisualInstallmentProgress(12,7))
        assertEquals(CanonicalLoanInstallmentVisualProgress(12,12),
            canonicalLoanVisualInstallmentProgress(12,15))
        assertEquals(CanonicalLoanInstallmentVisualProgress(12,0),
            canonicalLoanVisualInstallmentProgress(12,-1))
    }

    @Test fun overSixtyVisualSegmentsNeverShowPaidInFullUntilReallySettled() {
        assertEquals(CanonicalLoanInstallmentVisualProgress(60,0),
            canonicalLoanVisualInstallmentProgress(120,1))
        assertEquals(CanonicalLoanInstallmentVisualProgress(60,30),
            canonicalLoanVisualInstallmentProgress(120,60))
        assertEquals(CanonicalLoanInstallmentVisualProgress(60,59),
            canonicalLoanVisualInstallmentProgress(120,119))
        assertEquals(CanonicalLoanInstallmentVisualProgress(60,60),
            canonicalLoanVisualInstallmentProgress(120,120))
        assertEquals(CanonicalLoanInstallmentVisualProgress(4,3),
            canonicalLoanVisualInstallmentProgress(120,119,4))
    }

    private val accounts = listOf(
        MoneyAccount("salary","Μισθοδοσία",900.0,"",canonicalKind="bank",
            bankAccountCategory="payroll"),
        MoneyAccount("savings","Αποταμίευση",200.0,"",canonicalKind="bank",
            bankAccountCategory="savings"),
    )

    @Test fun repaymentReviewUsesCentsAndClampsTheLastRealInstallments() {
        val loan=LoanItem("real","Laptop","",180.0,100.0,"",300.0,
            remainingInstallments=2,totalInstallments=3)
        val first=canonicalLoanPaymentPreview(
            loan,accounts,"2026-10-27","salary","1","","")!!
        val both=canonicalLoanPaymentPreview(
            loan,accounts,"2026-10-27","salary","2","","")!!
        assertEquals(100.0,first.shownAmount,0.00001)
        assertEquals(180.0,both.shownAmount,0.00001)
        assertNull(both.amount)
        assertNull(canonicalLoanPaymentPreview(
            loan,accounts,"2026-10-27","salary","3","",""))
    }

    @Test fun selfLoanSubcentsAndRoundedOversettlementFailClosedBeforeConfirm() {
        val loan=LoanItem("help","ΒΟΗΘΕΙΑ","",50.0,50.0,"",50.0,
            remainingInstallments=1,isSelfLoan=true)
        assertNull(canonicalLoanPaymentPreview(
            loan,accounts,"2026-10-27","salary","1","20.001","savings"))
        assertNull(canonicalLoanPaymentPreview(
            loan,accounts,"2026-10-27","salary","1","50.005","savings"))
        assertNull(canonicalLoanPaymentPreview(
            loan,accounts,"2026-10-27","salary","1","50.01","savings"))
        val valid=canonicalLoanPaymentPreview(
            loan,accounts,"2026-10-27","salary","1","49,99","savings")!!
        assertEquals(49.99,valid.shownAmount,0.00001)
    }
}
