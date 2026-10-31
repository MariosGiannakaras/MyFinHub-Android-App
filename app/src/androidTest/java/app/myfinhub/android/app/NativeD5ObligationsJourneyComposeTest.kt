package app.myfinhub.android.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.home.syntheticHomeUiState
import app.myfinhub.android.feature.money.syntheticMoneyUiState
import app.myfinhub.android.feature.plan.syntheticPlanUiState
import org.junit.Rule
import org.junit.Test

/** Full production app routes, with no live write callback. */
class NativeD5ObligationsJourneyComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun savingsLoansScheduledAndRecurringOpenFromNativePlanWithBackNavigation() {
        rule.setContent { MyFinHubTheme {
            MyFinHubAppContent(
                homeState = syntheticHomeUiState(), onHomeAction = {},
                moneyState = syntheticMoneyUiState(),
                planState = syntheticPlanUiState(),
                onSaveSavingsGoal = { _, _, _, _ -> },
            )
        } }
        rule.onNodeWithContentDescription("Πλάνο", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("s7_plan_root")
            .performScrollToNode(hasTestTag("s7_savings_link"))
        rule.onNodeWithTag("s7_savings_link").performClick()
        rule.onNodeWithTag("savings_goal_new").performScrollTo().performClick()
        rule.onNodeWithTag("savings_goal_name").assertExists()
        rule.onNodeWithText("Ακύρωση").performClick()
        rule.onNodeWithContentDescription("Πίσω").performClick()

        rule.onNodeWithTag("s7_plan_root")
            .performScrollToNode(hasTestTag("s7_loans_link"))
        rule.onNodeWithTag("s7_loans_link").performClick()
        rule.onNodeWithTag("loan_total_outstanding").assertExists()
        rule.onNodeWithContentDescription("Πίσω").performClick()

        rule.onNodeWithTag("s7_plan_root")
            .performScrollToNode(hasTestTag("s7_scheduled_management_link"))
        rule.onNodeWithTag("s7_scheduled_management_link").performClick()
        rule.onNodeWithTag("scheduled_management_root").assertExists()
        rule.onNodeWithContentDescription("Πίσω").performClick()

        rule.onNodeWithTag("s7_plan_root")
            .performScrollToNode(hasTestTag("s7_recurring_management_link"))
        rule.onNodeWithTag("s7_recurring_management_link").performClick()
        rule.onNodeWithTag("mobile_recurring_overview").assertExists()
    }
}
