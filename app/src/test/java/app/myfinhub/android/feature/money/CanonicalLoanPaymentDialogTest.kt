package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanonicalLoanPaymentDialogTest {
    private val accounts = listOf(
        MoneyAccount("salary","Μισθοδοσία",900.0,"Τράπεζα",canonicalKind="bank",
            bankAccountCategory="payroll"),
        MoneyAccount("save","Αποταμίευση",200.0,"Αποταμίευση",canonicalKind="bank",
            bankAccountCategory="savings"),
    )
    private val regular=LoanItem("regular","Laptop","Τράπεζα",180.0,100.0,"",300.0,
        remainingInstallments=2,defaultAccountId="salary")
    private val help=LoanItem("help","ΒΟΗΘΕΙΑ","",150.0,50.0,"",200.0,
        remainingInstallments=1,isSelfLoan=true,defaultAccountId="salary")

    @Test fun confirmsOnlyValidDateCountAndSourceAndClampsInstallmentToOutstanding() {
        val plan=canonicalLoanPaymentPreview(regular,accounts,"2026-10-17","salary",
            "2","","")!!
        assertEquals(180.0,plan.shownAmount,0.001)
        assertEquals(2,plan.count)
        assertNull(plan.amount)
        assertNull(canonicalLoanPaymentPreview(regular,accounts,"2026-10-17",
            "salary","3","",""))
        assertNull(canonicalLoanPaymentPreview(regular,accounts,"2026-10-17",
            "missing","1","",""))
        assertNull(canonicalLoanPaymentPreview(regular,accounts,"bad",
            "salary","1","",""))
    }

    @Test fun selfLoanRequiresActualSavingsDestinationAndPositiveBoundedAmount() {
        val valid=canonicalLoanPaymentPreview(help,accounts,"2026-10-17","salary",
            "1","60,00","save")!!
        assertEquals(60.0,valid.amount!!,0.001)
        assertEquals("save",valid.savingsAccountId)
        assertNull(canonicalLoanPaymentPreview(help,accounts,"2026-10-17","salary",
            "1","160","save"))
        assertNull(canonicalLoanPaymentPreview(help,accounts,"2026-10-17","salary",
            "1","60","salary"))
        assertNull(canonicalLoanPaymentPreview(help,accounts,"2026-10-17","salary",
            "1","0","save"))
    }
}
