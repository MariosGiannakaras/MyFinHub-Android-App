package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanonicalLoanMetadataEditorLogicTest {
    private val salary=MoneyAccount("salary","Μισθοδοσία",950.0,"Τράπεζα",
        canonicalKind="bank",bankAccountCategory="payroll")
    private val savings=MoneyAccount("savings","Αποταμίευση",250.0,"Τράπεζα",
        canonicalKind="bank",bankAccountCategory="savings")

    @Test fun scheduleRecalculationMatchesWebLoanEditorOnlyWhenOwnerChangesAField() {
        assertEquals("33.33", loanEditorInstallmentFromTotal("100","3"))
        assertEquals("50", loanEditorInstallmentFromTotal("150","3"))
        assertEquals("99.99", loanEditorTotalFromInstallment("33.33","3"))
        assertEquals("187.5", loanEditorTotalFromInstallment("62.50","3"))
        assertNull(loanEditorInstallmentFromTotal("100","0"))
        assertNull(loanEditorInstallmentFromTotal("100","1201"))
        assertNull(loanEditorInstallmentFromTotal("0.01","1200"))
        assertNull(loanEditorTotalFromInstallment("NaN","3"))
        assertNull(loanEditorTotalFromInstallment("-25","3"))
        assertNull(loanEditorTotalFromInstallment("20","-2"))
    }

    @Test fun metadataEditorOnlyUsesAnAlreadyVerifiedExistingDefaultPayer() {
        val valid=LoanItem("loan","Laptop","",180.0,60.0,"",180.0,
            remainingInstallments=3,totalInstallments=3,kind="installment",
            defaultAccountId="salary")
        assertEquals("salary",loanEditorCurrentDefaultAccount(valid,listOf(salary,savings)))
        assertEquals("",loanEditorCurrentDefaultAccount(null,listOf(salary,savings)))
        assertEquals("",loanEditorCurrentDefaultAccount(
            valid.copy(defaultAccountId="removed"),listOf(salary,savings)))
        assertEquals("",loanEditorCurrentDefaultAccount(
            valid,listOf(salary,salary.copy(canonicalKind="credit"))))
        assertEquals("",loanEditorCurrentDefaultAccount(
            valid,listOf(salary,salary.copy(name="Duplicate"))))
        assertEquals("",loanEditorCurrentDefaultAccount(
            valid.copy(defaultAccountId=" salary "),listOf(salary,savings)))
    }
}
