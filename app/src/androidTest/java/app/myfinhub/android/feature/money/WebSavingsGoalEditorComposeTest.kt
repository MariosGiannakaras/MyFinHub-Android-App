package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real financial goal UI must produce intent parameters only after explicit validation/confirmation. */
class WebSavingsGoalEditorComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun newGoalCannotSaveInvalidPastDeadline() {
        val called=mutableListOf<String>()
        val state=MoneyUiState(asOfDate="2026-10-15")
        rule.setContent { MyFinHubTheme {
            CanonicalSavingsScreen(state,onBack={},
                onSaveGoal={_,name,_,_->called.add(name)})
        } }
        rule.onNodeWithTag("savings_goal_new").performScrollTo().performClick()
        rule.onNodeWithTag("savings_goal_name").performTextInput("Ταξίδι")
        rule.onNodeWithTag("savings_goal_amount").performTextInput("1200")
        rule.onNodeWithTag("savings_goal_date").performTextInput("2026-10-14")
        rule.onNodeWithTag("savings_goal_save").performClick()
        rule.runOnIdle { assertEquals(0,called.size) }
    }

    @Test fun deleteGoalRequiresConfirmationAndDoesNotImplyDeletingSavingsMoney() {
        val called=mutableListOf<String>()
        val state=MoneyUiState(
            asOfDate="2026-10-15",
            savingsGoalItems=listOf(
                SavingsGoalItem("goal1","Ταμείο",200.0,null,100.0,50.0,false),
            ),
        )
        rule.setContent { MyFinHubTheme {
            CanonicalSavingsScreen(state,onBack={},
                onDeleteGoal={called.add(it)})
        } }
        rule.onNodeWithTag("savings_goal_delete_goal1").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(0,called.size) }
        rule.onNodeWithTag("savings_goal_confirm_delete").performClick()
        rule.runOnIdle { assertEquals(listOf("goal1"),called) }
    }
}
