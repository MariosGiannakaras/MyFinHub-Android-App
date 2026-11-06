package app.myfinhub.android.feature.money

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** This dialog only edits loan definitions; money events require another owner action. */
@RunWith(AndroidJUnit4::class)
class CanonicalLoanMetadataEditorComposeTest {
    @get:Rule val rule=createComposeRule()
    private val salary=MoneyAccount("salary","Μισθοδοσία",950.0,"Τράπεζα",
        canonicalKind="bank",bankAccountCategory="payroll")
    private val savings=MoneyAccount("save","Αποταμίευση",250.0,"Τράπεζα",
        canonicalKind="bank",bankAccountCategory="savings")

    private fun validFields() {
        rule.onNodeWithTag("loan_editor_name").performScrollTo().performTextInput("Laptop")
        rule.onNodeWithTag("loan_editor_total").performScrollTo().performTextInput("180")
        rule.onNodeWithTag("loan_editor_count").performScrollTo().performTextInput("3")
    }

    @Test fun createRequiresExplicitPayerAndAutoCalculatesCentAccurateInstallment() {
        val created=mutableListOf<LoanMetadataEditRequest>()
        rule.setContent { MyFinHubTheme {
            CanonicalLoanMetadataEditor(null,listOf(salary,savings),listOf("Άλλο"),
                mutationBlocked=false,onDismiss={},onSave={ created+=it })
        } }
        validFields()
        rule.onNodeWithTag("loan_editor_account_required").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("loan_editor_save").assertIsNotEnabled()
        rule.onNodeWithTag("loan_editor_account").performScrollTo().performClick()
        rule.onNodeWithTag("loan_editor_account_choice_salary").performClick()
        rule.onNodeWithTag("loan_editor_save").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(1,created.size)
            assertEquals("salary",created.single().defaultAccountId)
            assertEquals(180.0,created.single().total,0.001)
            assertEquals(60.0,created.single().installment,0.001)
            assertEquals(3,created.single().installments)
            assertEquals("Άλλο",created.single().expenseCategory)
        }
    }

    @Test fun explicitInstallmentChangeRecalculatesTotalWithoutCreatingPayment() {
        val saved=mutableListOf<LoanMetadataEditRequest>()
        rule.setContent { MyFinHubTheme {
            CanonicalLoanMetadataEditor(null,listOf(salary),listOf("Σπίτι"),
                mutationBlocked=false,onDismiss={},onSave={ saved+=it })
        } }
        validFields()
        rule.onNodeWithTag("loan_editor_installment").performScrollTo()
            .performTextReplacement("70")
        rule.onNodeWithTag("loan_editor_account").performScrollTo().performClick()
        rule.onNodeWithTag("loan_editor_account_choice_salary").performClick()
        rule.onNodeWithTag("loan_editor_save").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(1,saved.size)
            assertEquals(210.0,saved.single().total,0.001)
            assertEquals(70.0,saved.single().installment,0.001)
            assertEquals(3,saved.single().installments)
        }
    }

    @Test fun crossKindDuplicatePayerCannotBeChosenOrSaved() {
        val duplicate=salary.copy(name="Duplicate credit identity",canonicalKind="credit")
        var saved=0
        rule.setContent { MyFinHubTheme {
            CanonicalLoanMetadataEditor(null,listOf(salary,duplicate),listOf("Άλλο"),
                mutationBlocked=false,onDismiss={},onSave={ saved++ })
        } }
        validFields()
        rule.onNodeWithTag("loan_editor_account").performScrollTo().performClick()
        rule.onNodeWithTag("loan_editor_account_choice_salary").assertDoesNotExist()
        rule.onNodeWithTag("loan_editor_save").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0,saved) }
    }

    @Test fun existingValidPayerRemainsSelectedButDisappearingPayerBlocksSave() {
        val initial=LoanItem("loan","Laptop","Τράπεζα",180.0,50.0,"",180.0,
            remainingInstallments=3,totalInstallments=3,kind="installment",
            defaultAccountId="salary",expenseCategory="Άλλο")
        val live=mutableStateOf(listOf(salary,savings))
        val saved=mutableListOf<LoanMetadataEditRequest>()
        rule.setContent { MyFinHubTheme {
            CanonicalLoanMetadataEditor(initial,live.value,listOf("Άλλο"),
                mutationBlocked=false,onDismiss={},onSave={saved+=it})
        } }
        rule.onNodeWithTag("loan_editor_save").assertIsEnabled()
        rule.runOnIdle { live.value=listOf(savings) }
        rule.onNodeWithTag("loan_editor_account_required").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("loan_editor_save").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0,saved.size) }
        rule.onNodeWithTag("loan_editor_account").performScrollTo().performClick()
        rule.onNodeWithTag("loan_editor_account_choice_save").performClick()
        rule.onNodeWithTag("loan_editor_save").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals("save",saved.single().defaultAccountId)
            // Opening and changing only the payer must not overwrite the
            // historical installment amount even if total/count differ.
            assertEquals(50.0,saved.single().installment,0.001)
            assertEquals(180.0,saved.single().total,0.001)
        }
    }

    @Test fun noTaxonomyCanCreateAnExplicitlyAccountBoundOtherCategoryLoan() {
        val saved=mutableListOf<LoanMetadataEditRequest>()
        rule.setContent { MyFinHubTheme {
            CanonicalLoanMetadataEditor(null,listOf(salary),emptyList(),
                mutationBlocked=false,onDismiss={},onSave={saved+=it})
        } }
        validFields()
        rule.onNodeWithTag("loan_editor_account").performScrollTo().performClick()
        rule.onNodeWithTag("loan_editor_account_choice_salary").performClick()
        rule.onNodeWithTag("loan_editor_save").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals("Άλλο",saved.single().expenseCategory) }
    }
}
