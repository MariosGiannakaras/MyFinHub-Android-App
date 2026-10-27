package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.myfinhub.android.core.data.CanonicalCashFlowForecast
import app.myfinhub.android.core.data.CanonicalForecastAccount
import app.myfinhub.android.core.data.CanonicalForecastAssumption
import app.myfinhub.android.core.data.CanonicalForecastPoint
import app.myfinhub.android.core.data.webForecastScenario
import app.myfinhub.android.designsystem.MyFinHubTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/** Real hosted emulator account stress UI uses hypothetical, privacy-safe rows. */
class WebForecastScenarioAccountRiskComposeTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.parse("2026-08-17")
    private fun scenario(count: Int = 2) : app.myfinhub.android.core.data.CanonicalForecastScenario {
        val balances = (1..count).associate { "acc-$it" to 200.0 }
        val total = count * 200.0
        val baseline = CanonicalCashFlowForecast(
            asOf = today,endDate = today.plusDays(30),horizonDays = 30,
            currentPortfolio = total,projectedPortfolio = total,
            minimumPortfolio = total,minimumPortfolioDate = today,
            points = listOf(
                CanonicalForecastPoint(today,total,balances),
                CanonicalForecastPoint(today.plusDays(30),total,balances),
            ),
            movements = emptyList(),
            accounts = (1..count).map {
                CanonicalForecastAccount(
                    accountId="acc-$it",current=200.0,projected=200.0,
                    minimum=200.0,firstLowDate=null,firstNegativeDate=null)
            },
            omitted = listOf("unknown account"),
            sourceCounts = emptyMap(),
        )
        return baseline.webForecastScenario(listOf(
            CanonicalForecastAssumption(
                "stress","acc-1","2026-08-18","extra_expense",250.0)
        ))
    }

    private fun accounts(count: Int) = (1..count).map {
        ScheduledAccountOption("acc-$it","Λογαριασμός $it")
    }

    @Test fun negativeAccountWarningAppearsWithoutFalsePortfolioWarning() {
        rule.setContent { MyFinHubTheme {
            CanonicalForecastScenarioAccountStress(
                scenario(), accounts(2), amountsVisible=true)
        } }
        rule.onNodeWithTag("s7_forecast_scenario_account_stress").assertExists()
        rule.onNodeWithTag("s7_forecast_scenario_account_negative_1").assertExists()
        rule.onNodeWithTag("s7_forecast_scenario_account_projected_2").assertExists()
        rule.onNodeWithTag("s7_forecast_scenario_omitted_warning").assertExists()
        rule.onNodeWithText("Λογαριασμός 1").assertExists()
    }

    @Test fun hiddenAmountPreferenceCoversBaselineDeltaMinimumAndStressAmount() {
        rule.setContent { MyFinHubTheme {
            CanonicalForecastScenarioAccountStress(
                scenario(), accounts(2), amountsVisible=false)
        } }
        rule.onNodeWithTag("s7_forecast_scenario_account_delta_1").assertExists()
        rule.onNodeWithText("250,00",substring=true).assertDoesNotExist()
        rule.onNodeWithText("200,00",substring=true).assertDoesNotExist()
        rule.onNodeWithText("50,00",substring=true).assertDoesNotExist()
        rule.onNodeWithText("2026-08-18",substring=true).assertExists()
    }

    @Test fun paginatesEightAccountsWithoutDuplicationOrUnboundedRows() {
        rule.setContent { MyFinHubTheme {
            CanonicalForecastScenarioAccountStress(
                scenario(8), accounts(8), amountsVisible=true)
        } }
        rule.onNodeWithTag("s7_forecast_scenario_account_row_5").assertExists()
        rule.onNodeWithTag("s7_forecast_scenario_account_row_6").assertDoesNotExist()
        rule.onNodeWithTag("s7_forecast_scenario_account_more").performClick()
        rule.onNodeWithTag("s7_forecast_scenario_account_row_8").assertExists()
        rule.onNodeWithTag("s7_forecast_scenario_account_more").assertDoesNotExist()
    }
}
