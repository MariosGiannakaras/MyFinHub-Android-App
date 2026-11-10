package app.myfinhub.android.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.home.syntheticHomeUiState
import app.myfinhub.android.feature.plan.syntheticPlanUiState
import org.junit.Rule
import org.junit.Test

class NativePlanInsightsJourneyComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun nativePlanForecastAndBudgetAreReachableFromSamePhoneTab() {
        rule.setContent { MyFinHubTheme {
            MyFinHubAppContent(
                homeState = syntheticHomeUiState(), onHomeAction = {},
                planState = syntheticPlanUiState(),
                legacyTestInitialDestination = TopLevelDestination.PLAN,
            )
        } }
        rule.onNodeWithTag("s7_plan_root")
            .performScrollToNode(hasTestTag("s7_forecast_link"))
        rule.onNodeWithTag("s7_forecast_link").performClick()
        rule.onNodeWithTag("s7_forecast_root").assertExists()
        rule.onNodeWithContentDescription("Πίσω").performClick()
        rule.onNodeWithTag("s7_plan_root")
            .performScrollToNode(hasTestTag("s7_budget_link"))
        rule.onNodeWithTag("s7_budget_link").performClick()
        rule.onNodeWithTag("s7_budget_root").assertExists()
        rule.onNodeWithContentDescription("Πίσω").performClick()
    }

    @Test fun retainedInsightsStillRendersWithoutPrimaryReportsTab() {
        rule.setContent { MyFinHubTheme {
            MyFinHubAppContent(
                homeState = syntheticHomeUiState(),
                onHomeAction = {},
                legacyTestInitialDestination = TopLevelDestination.INSIGHTS,
            )
        } }
        rule.onNodeWithTag("insights_list").assertExists()
        org.junit.Assert.assertFalse(
            primaryMobileDestinations.contains(TopLevelDestination.INSIGHTS),
        )
    }
}
