package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
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

    @Test fun nextEightKnownMovementsAndAccountRisksCanBeExpandedReadOnly() {
        val state = PlanUiState(
            forecastWindows = listOf(
                ForecastWindow(
                    days = 30, label = "30 ημέρες",
                    expectedIncome = 0.0, expectedOutflow = 0.0,
                    balanceDeltaFromThirtyDays = 0.0,
                    movementPreviews = listOf(
                        ForecastMovementPreview(
                            id="forecast-recurring-internet-2026-10-20",
                            dateIso="2026-10-20", label="Internet", source="recurring",
                            portfolioDelta=-30.0,
                        ),
                    ),
                    accountRisks = listOf(
                        ForecastAccountRisk(
                            id="payroll", name="Μισθοδοσία",
                            firstLowDateIso="2026-10-23",
                            firstNegativeDateIso="2026-10-28",
                        ),
                    ),
                ),
            ),
        )
        rule.setContent { MyFinHubTheme {
            CanonicalPlanForecastScreen(state, onBack={})
        } }
        rule.onNodeWithTag("s7_forecast_movements_toggle_30")
            .performScrollTo().performClick()
        rule.onNodeWithTag("s7_forecast_movement_30_0")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Internet · 2026-10-20").assertIsDisplayed()
        rule.onNodeWithTag("s7_forecast_account_risks_toggle_30")
            .performScrollTo().performClick()
        rule.onNodeWithTag("s7_forecast_account_risk_30_0")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Μισθοδοσία · Αρνητικό από 2026-10-28")
            .assertIsDisplayed()
    }

    @Test fun sourceProvenanceAndOmissionsAreExpandableWithoutSyntheticMovements() {
        val state = PlanUiState(
            forecastWindows = listOf(
                ForecastWindow(
                    days = 30, label = "30 ημέρες", expectedIncome = 0.0,
                    expectedOutflow = 0.0, balanceDeltaFromThirtyDays = 0.0,
                    sourceCounts = mapOf("scheduled" to 1,"recurring" to 2,
                        "loan" to 1,"dated_event" to 0),
                    omittedSourceCount = 2,
                    omittedWarnings = listOf(
                        "Το πάγιο Α δεν προβλήθηκε επειδή λείπει ημερομηνία.",
                        "Η δόση Β δεν προβλήθηκε επειδή λείπει λογαριασμός.",
                    ),
                ),
            ),
        )
        rule.setContent { MyFinHubTheme {
            CanonicalPlanForecastScreen(state,onBack={})
        } }
        rule.onNodeWithText("Πηγές πρόβλεψης:", substring=true)
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("s7_forecast_omitted_toggle_30")
            .performScrollTo().performClick()
        rule.onNodeWithText("Το πάγιο Α δεν προβλήθηκε", substring=true)
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Η δόση Β δεν προβλήθηκε", substring=true)
            .performScrollTo().assertIsDisplayed()
    }
}
