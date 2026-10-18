package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** The production savings surface renders two genuine goals and historic deadlines. */
class WebSavingsGoalComposeTest {
    @get:Rule val rule=createComposeRule()
    @Test fun webSavingsHistoryShowsDistinctSameIdRowsAndPrefersRealUserNotes() {
        val state = MoneyUiState(
            savingsHistory = listOf(
                SavingsHistoryItem("duplicate", "2026-10-11", 12.0,
                    "Νέο σχόλιο", "Μεταφορά στην αποταμίευση", true, "event"),
                SavingsHistoryItem("duplicate", "2026-10-10", 0.0,
                    "Pay & Save", "Pay & Save", false, "legacy"),
            ),
        )
        rule.setContent { MyFinHubTheme { CanonicalSavingsScreen(state,onBack={}) } }
        rule.onNodeWithTag("savings_history_row_0").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Νέο σχόλιο").assertIsDisplayed()
        rule.onNodeWithTag("savings_history_row_1").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Pay & Save").assertIsDisplayed()
        rule.onNodeWithText("Πρόσφατη αποταμίευση").performScrollTo().assertIsDisplayed()
    }

    @Test fun savingsGoalsShowIndependentTargetsAndDoNotPresentUnsupportedMutationButtons() {
        val state=MoneyUiState(
            savingsCurrent=240.0,
            savingsGoalItems=listOf(
                SavingsGoalItem("old","Ταμείο ασφαλείας",500.0,"2026-09-01",290.0,58.0,true),
                SavingsGoalItem("future","Ταξίδι",100.0,"2027-01-10",290.0,100.0,false),
            ),
        )
        rule.setContent { MyFinHubTheme { CanonicalSavingsScreen(state,onBack={}) } }
        // No synthetic history placeholder should disturb a genuinely empty savings month.
        rule.onNodeWithText("Πρόσφατη αποταμίευση").assertDoesNotExist()
        rule.onNodeWithTag("savings_goal_old").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Η υπάρχουσα προθεσμία έχει παρέλθει:",substring=true)
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("savings_goal_future").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Ταξίδι").assertIsDisplayed()
    }
}
