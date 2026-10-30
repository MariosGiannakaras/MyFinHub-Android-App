package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.utilities.AmountVisibilityPreference
import org.junit.After
import org.junit.Rule
import org.junit.Test

class NativeD5SavingsLoanIntegrityComposeTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun resetPrivacy() { AmountVisibilityPreference.write(context, false) }

    @Test fun corruptSavingsProgressIsNeverReportedAsZeroPercent() {
        AmountVisibilityPreference.write(context, true)
        val goal = SavingsGoalItem("goal-1", "Αποταμίευση", 600.0, null,
            250.0, Double.NaN, false)
        rule.setContent { MyFinHubTheme {
            CanonicalSavingsScreen(
                MoneyUiState(savingsGoalItems = listOf(goal), savingsCurrent = 250.0),
                onBack = {}, onSaveGoal = { _, _, _, _ -> error("No goal edit") },
            )
        } }
        rule.onNodeWithTag("savings_list")
            .performScrollToNode(hasTestTag("savings_goal_goal-1"))
        rule.onNodeWithTag("savings_goal_goal-1").assertExists()
        rule.onNodeWithText("Μη έγκυρη πρόοδος", substring = true).assertExists()
        rule.onNodeWithText("NaN", substring = true).assertDoesNotExist()
    }

    @Test fun corruptSavingsProgressRemainsHiddenWhenPrivacyIsEnabled() {
        AmountVisibilityPreference.write(context, false)
        val goal = SavingsGoalItem("goal-private", "Μαξιλάρι", 600.0, null,
            250.0, Double.POSITIVE_INFINITY, false)
        rule.setContent { MyFinHubTheme {
            CanonicalSavingsScreen(
                MoneyUiState(savingsGoalItems = listOf(goal), savingsCurrent = 250.0),
                onBack = {},
            )
        } }
        rule.onNodeWithTag("savings_list")
            .performScrollToNode(hasTestTag("savings_goal_goal-private"))
        rule.onNodeWithText("Μη έγκυρη πρόοδος", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Infinity", substring = true).assertDoesNotExist()
    }

    @Test fun duplicateGoalSourceMakesEditAndDeleteReadOnly() {
        val g = SavingsGoalItem("same", "Στόχος", 100.0, null, 50.0, 50.0, false)
        rule.setContent { MyFinHubTheme {
            CanonicalSavingsScreen(
                MoneyUiState(savingsGoalItems = listOf(g, g.copy(name = "Διπλό"))),
                onBack = {},
                onSaveGoal = { _, _, _, _ -> error("Duplicate goal edit") },
                onDeleteGoal = { error("Duplicate goal delete") },
            )
        } }
        rule.onNodeWithTag("savings_list")
            .performScrollToNode(hasTestTag("savings_goal_edit_same"))
        rule.onAllNodesWithTag("savings_goal_edit_same")[0].assertIsNotEnabled()
        rule.onAllNodesWithTag("savings_goal_delete_same")[0].assertIsNotEnabled()
    }

    @Test fun infiniteLoanDebtIsReadOnlyAndNotCalledCompleted() {
        AmountVisibilityPreference.write(context, true)
        val loan = LoanItem(
            id = "source-bad", name = "Ιστορικό δάνειο", lender = "Τράπεζα",
            remaining = Double.POSITIVE_INFINITY, monthlyPayment = 30.0,
            nextPaymentLabel = "5 Νοε", remainingInstallments = 4,
        )
        rule.setContent { MyFinHubTheme {
            CanonicalLoansScreen(
                MoneyUiState(loans = listOf(loan), loanOutstanding = Double.POSITIVE_INFINITY),
                onBack = {}, onRecordPayment = { _, _, _, _, _, _ -> error("Invalid payment") },
            )
        } }
        rule.onNodeWithTag("loans_list")
            .performScrollToNode(hasText("Μη έγκυρα στοιχεία δανείων"))
        rule.onNodeWithText("Μη έγκυρα στοιχεία δανείων").assertExists()
        rule.onNodeWithTag("loan_record_payment_source-bad").assertDoesNotExist()
        rule.onAllNodesWithText("Μη έγκυρο ποσό", substring = true)[0].assertExists()
        rule.onNodeWithText("NaN", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Infinity", substring = true).assertDoesNotExist()
    }
}
