package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** The real Plan forecast displays Web portfolio windows without replacing H7 spendable. */
class WebForecastWindowComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun allThreePortfolioWindowsRenderWarningsAndPartialData() {
        val state = PlanUiState(
            forecastHorizonDays = 30,
            forecastStartDateIso = "2026-10-15",
            forecastEndDateIso = "2026-11-14",
            forecastStartBalance = 1000.0,
            forecastEndBalance = 900.0,
            forecastWindows = listOf(
                ForecastWindow(30, "30 ημέρες", 200.0, 300.0, 0.0,
                    projectedPortfolio = 1200.0, minimumPortfolio = 200.0),
                ForecastWindow(60, "60 ημέρες", 200.0, 350.0, -50.0,
                    projectedPortfolio = 1150.0, minimumPortfolio = -25.0,
                    accountsWithLowBalance = 1, accountsWithNegativeBalance = 1),
                ForecastWindow(90, "90 ημέρες", 300.0, 500.0, -100.0,
                    projectedPortfolio = 1100.0, minimumPortfolio = -50.0,
                    omittedSourceCount = 2),
            ),
        )
        rule.setContent { MyFinHubTheme {
            CanonicalPlanForecastScreen(state = state, onBack = {})
        } }
        rule.onNodeWithTag("s7_forecast_portfolio_30").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("s7_forecast_portfolio_60").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Προσοχή:",substring=true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("s7_forecast_portfolio_90").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("2 κινήσεις παραλείφθηκαν",substring=true).assertIsDisplayed()
    }
}
