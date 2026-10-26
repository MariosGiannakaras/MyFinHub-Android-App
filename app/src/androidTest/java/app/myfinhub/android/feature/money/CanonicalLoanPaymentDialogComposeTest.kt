package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A screen interaction never records money before explicit user confirmation. */
@RunWith(AndroidJUnit4::class)
class CanonicalLoanPaymentDialogComposeTest {
    @get:Rule val rule = createComposeRule()
    private val accounts = listOf(
        MoneyAccount("salary","Μισθοδοσία",900.0,"Τράπεζα",
            canonicalKind="bank",bankAccountCategory="payroll"),
        MoneyAccount("savings","Αποταμίευση",200.0,"Ταμιευτήριο",
            canonicalKind="bank",bankAccountCategory="savings"),
    )

    @Test fun loanPaymentRequiresReviewAndConfirm() {
        val calls=mutableListOf<List<Any?>>()
        val state=MoneyUiState(accounts=accounts,loanOutstanding=180.0,
            loans=listOf(LoanItem("loan","Laptop","Τράπεζα",180.0,100.0,"",300.0,
                remainingInstallments=2,defaultAccountId="salary")))
        rule.setContent { MyFinHubTheme {
            CanonicalLoansScreen(state,onBack={},onRecordPayment={
                id,acc,date,count,amount,target -> calls += listOf(id,acc,date,count,amount,target)
            })
        } }
        rule.onNodeWithTag("loan_record_payment_loan").performClick()
        rule.runOnIdle { assertEquals(0,calls.size) }
        rule.onNodeWithTag("loan_payment_confirm").performClick()
        rule.runOnIdle {
            assertEquals(1,calls.size)
            assertEquals("loan",calls.single()[0])
            assertEquals("salary",calls.single()[1])
            assertEquals(1,calls.single()[3])
        }
    }


    @Test fun loanSegmentsPresentHonestPaidCountWithNoExtraMoneyMutation() {
        val state=MoneyUiState(accounts=accounts,loanOutstanding=200.0,
            loans=listOf(LoanItem("progress","Laptop","",200.0,50.0,"",300.0,
                remainingInstallments=4,totalInstallments=6)))
        rule.setContent { MyFinHubTheme {
            CanonicalLoansScreen(state, onBack = {})
        } }
        rule.onNodeWithTag("loan_paid_installment_count_progress").assertIsDisplayed()
        rule.onNodeWithText("2 / 6 πληρωμένες δόσεις").assertIsDisplayed()
        rule.onNodeWithTag("loan_installment_segments_progress").assertIsDisplayed()
    }

    @Test fun hiddenPrivacyMasksOutstandingAndReviewAmountInActualDialog() {
        val loan=LoanItem("loan","Laptop","",180.0,100.0,"",300.0,
            remainingInstallments=2,totalInstallments=3,defaultAccountId="salary")
        var posts=0
        rule.setContent { MyFinHubTheme {
            CanonicalLoanPaymentDialog(
                loan,accounts,onDismiss={},onConfirm={_,_,_,_,_->posts++},
                amountsVisible=false)
        } }
        rule.onNodeWithTag("loan_payment_remaining").assertIsDisplayed()
        rule.onNodeWithTag("loan_payment_review_amount").assertIsDisplayed()
        rule.onNodeWithText("180,00",substring=true).assertDoesNotExist()
        rule.onNodeWithText("100,00",substring=true).assertDoesNotExist()
        rule.runOnIdle { assertEquals(0,posts) }
    }

    @Test fun visibleReviewShowsTheCentExactRealAmountWithNoImplicitPosting() {
        val loan=LoanItem("loan","Laptop","",180.0,100.0,"",300.0,
            remainingInstallments=2,totalInstallments=3,defaultAccountId="salary")
        var posts=0
        rule.setContent { MyFinHubTheme {
            CanonicalLoanPaymentDialog(
                loan,accounts,onDismiss={},onConfirm={_,_,_,_,_->posts++},
                amountsVisible=true)
        } }
        rule.onNodeWithText("180,00",substring=true).assertIsDisplayed()
        rule.onNodeWithText("100,00",substring=true).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0,posts) }
    }

    @Test fun blockedMoneyWritesLeaveAllLoanPaymentButtonsDisabled() {
        val state=MoneyUiState(accounts=accounts,loans=listOf(
            LoanItem("loan","Laptop","",100.0,50.0,"",200.0,remainingInstallments=2)))
        rule.setContent { MyFinHubTheme {
            CanonicalLoansScreen(state,onBack={},onRecordPayment={
                _,_,_,_,_,_->error("Blocked mutation")
            },mutationBlocked=true)
        } }
        rule.onNodeWithTag("loan_record_payment_loan").assertIsNotEnabled()
    }
}
