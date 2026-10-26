package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.myfinhub.android.core.data.CanonicalGoalScenario
import app.myfinhub.android.core.data.CanonicalSavingsGoalProjection
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** Hosted S24 real Compose coverage: Web goal advice is non-posting and privacy-safe. */
class WebSavingsGoalAdvisoryComposeTest {
    @get:Rule val rule = createComposeRule()
    private val projection = CanonicalSavingsGoalProjection(
        status = "active", asOf = "2026-10-26", targetDate = "2027-01-30",
        target = 290.0, sharedSavingsBalance = 90.0, remaining = 200.0,
        contributionMonths = 4, suggestedMonthly = 50.0,
        operatingLiquidityMinimum90d = 180.0, knownObligationCount90d = 3,
        forecastOmissions = 0, forecastCompleteness = "known_movements",
        suggestedContributionRisk = "none",
        scenarios = listOf(
            CanonicalGoalScenario("none", 0.0, 90.0, 200.0),
            CanonicalGoalScenario("suggested", 50.0, 290.0, 0.0),
        ),
    )
    private fun state(projection: CanonicalSavingsGoalProjection? = this.projection,
        error: String? = null) = MoneyUiState(
        savingsGoalItems = listOf(
            SavingsGoalItem("goal", "Ταμείο", 290.0, "2027-01-30", 90.0, 31.0, false),
        ),
        savingsGoalAdvisories = listOf(SavingsGoalAdvisoryUiItem(projection, error)),
    )

    @Test fun opensRealGoalAdvisoryAndCustomScenarioWithoutPosting() {
        rule.setContent { MyFinHubTheme { CanonicalSavingsScreen(state(), onBack = {}) } }
        rule.onNodeWithTag("savings_goal_projection_goal").performScrollTo().performClick()
        rule.onNodeWithTag("savings_goal_projection_details_goal").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Προτεινόμενη συνεισφορά").assertIsDisplayed()
        rule.onNodeWithTag("savings_goal_custom_monthly").performTextInput("12,34")
        rule.onNodeWithTag("savings_goal_scenario_custom").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("savings_goal_projection_goal").performScrollTo().performClick()
        rule.onNodeWithTag("savings_goal_projection_details_goal").assertDoesNotExist()
    }

    @Test fun errorsAreVisibleAndDoNotInventProjectionValues() {
        rule.setContent { MyFinHubTheme {
            CanonicalSavingsScreen(state(projection = null, error = "Μη έγκυρη προθεσμία στόχου."),
                onBack = {})
        } }
        rule.onNodeWithTag("savings_goal_projection_goal").performScrollTo().performClick()
        rule.onNodeWithTag("savings_goal_projection_error").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("savings_goal_scenario_suggested").assertDoesNotExist()
    }

    @Test fun privacyMaskedAdviceStillShowsLiquidityAndCompletenessWarnings() {
        rule.setContent { MyFinHubTheme {
            CanonicalSavingsGoalAdvisoryDetails(
                SavingsGoalAdvisoryUiItem(
                    projection = projection.copy(
                        suggestedContributionRisk = "liquidity_warning",
                        forecastCompleteness = "limited",
                    ),
                ),
                amountsVisible = false,
            )
        } }
        rule.onNodeWithTag("savings_goal_liquidity_warning").assertIsDisplayed()
        rule.onNodeWithTag("savings_goal_forecast_limited").assertIsDisplayed()
        rule.onNodeWithTag("savings_goal_scenario_suggested").assertIsDisplayed()
        // Masking keeps actual financial amounts off this advice surface.
        rule.onNodeWithText("290,00", substring = true).assertDoesNotExist()
        rule.onNodeWithText("50,00", substring = true).assertDoesNotExist()
    }
}
