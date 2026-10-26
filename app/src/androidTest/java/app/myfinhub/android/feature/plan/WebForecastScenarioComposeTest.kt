package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.cashFlowForecast
import app.myfinhub.android.core.data.canonicalEvents
import app.myfinhub.android.designsystem.MyFinHubTheme
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** A real hosted-S24 plan what-if is advice only, not a synthetic transaction. */
class WebForecastScenarioComposeTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.parse("2026-08-17")
    private fun doc() = CanonicalFinanceDocument(Json.parseToJsonElement("""
        {"seed":{"accounts":[{"id":"cash","kind":"cash"}],
          "snapshots":[{"date":"2026-08-17","balances":{"cash":500}}]},
         "state":{"events":[]}}
    """.trimIndent()).jsonObject)
    private fun uiState(doc: CanonicalFinanceDocument): PlanUiState =
        PlanUiState(
            scheduledAccounts = listOf(ScheduledAccountOption("cash", "Μετρητά")),
            forecastScenarioBaselines = listOf(30,60,90)
                .associateWith { doc.cashFlowForecast(today, it) },
        )

    @Test fun explicitWhatIfDisplaysForecastWithoutChangingCanonicalDocument() {
        val data = doc()
        val initial = data.raw
        rule.setContent { MyFinHubTheme {
            CanonicalPlanForecastScreen(uiState(data), onBack = {})
        } }
        rule.onNodeWithTag("s7_forecast_scenario_toggle").performScrollTo().performClick()
        rule.onNodeWithTag("s7_forecast_scenario_add").performScrollTo().performClick()
        rule.onNodeWithTag("s7_forecast_scenario_amount_1")
            .performScrollTo().performTextReplacement("20")
        rule.onNodeWithTag("s7_forecast_scenario_results").performScrollTo().assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(initial, data.raw)
            assertEquals(0, data.canonicalEvents().size)
        }
    }

    @Test fun invalidDateIsNotPresentedAsProjectedRealMoney() {
        val data = doc()
        rule.setContent { MyFinHubTheme {
            CanonicalPlanForecastScreen(uiState(data), onBack = {})
        } }
        rule.onNodeWithTag("s7_forecast_scenario_toggle").performScrollTo().performClick()
        rule.onNodeWithTag("s7_forecast_scenario_add").performScrollTo().performClick()
        rule.onNodeWithTag("s7_forecast_scenario_amount_1")
            .performScrollTo().performTextReplacement("15.50")
        rule.onNodeWithTag("s7_forecast_scenario_date_1")
            .performScrollTo().performTextReplacement("2026-02-30")
        rule.onNodeWithTag("s7_forecast_scenario_error").performScrollTo().assertIsDisplayed()
    }
}
