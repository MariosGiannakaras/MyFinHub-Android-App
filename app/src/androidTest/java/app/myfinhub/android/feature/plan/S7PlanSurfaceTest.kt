package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class S7PlanSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val obligation = PlannedItem(
        id = "rent",
        title = "Ενοίκιο",
        dueLabel = "8 Σεπ",
        amount = 680.0,
        kind = PlannedKind.SCHEDULED,
        flow = PlannedFlow.OBLIGATION,
        category = "Στέγαση",
        dueDateIso = "2026-09-08",
        urgency = PlannedUrgency.OVERDUE,
    )
    private val state = PlanUiState(
        items = listOf(obligation),
        budget = BudgetDraft("800", "80"),
        forecastHorizonDays = 30,
        forecastStartDateIso = "2026-09-10",
        forecastEndDateIso = "2026-10-10",
        forecastStartBalance = 1_200.0,
        forecastObligations = 680.0,
        forecastEndBalance = 520.0,
        budgetSpent = 700.0,
        budgetMonthLabel = "Σεπ 2026",
    )

    @Test
    fun plan_exposesUrgencyForecastAndBudget_asDecisionHierarchy() {
        composeRule.setContent { MyFinHubTheme { CanonicalPlan2026Screen(state, {}, {}) } }
        composeRule.onNodeWithText("Πρώτα").assertIsDisplayed()
        composeRule.onNodeWithTag("s7_forecast_link").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("s7_budget_link").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun forecast_contextualRecording_returnsExactCanonicalSource() {
        var recorded: PlannedItem? = null
        composeRule.setContent {
            MyFinHubTheme { CanonicalPlanForecastScreen(state, onBack = {}, onRecordItem = { recorded = it }) }
        }
        composeRule.onNodeWithTag("s7_forecast_expand").performClick()
        composeRule.onNodeWithTag("s7_forecast_record_SCHEDULED:rent").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("rent", recorded?.id) }
    }

    @Test
    fun budget_usesProgressWarning_withoutPushNotificationPromise() {
        composeRule.setContent { MyFinHubTheme { CanonicalBudget2026Screen(state, { _, _ -> }, {}) } }
        composeRule.onNodeWithTag("s7_budget_threshold_warning").assertIsDisplayed()
        composeRule.onNodeWithText("Θα ειδοποιείσαι", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Όριο προειδοποίησης").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun budget_saveIsChangeDriven_andBlocksDuplicateSubmitWhileInFlight() {
        var saveCalls = 0
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalBudget2026Screen(
                    state = state,
                    onSaveBudget = { _, _ -> saveCalls += 1 },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("s7_budget_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("s7_budget_limit").performTextReplacement("850")
        composeRule.onNodeWithTag("s7_budget_save").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, saveCalls) }
        composeRule.onNodeWithTag("s7_budget_save").assertIsNotEnabled()
    }

    @Test
    fun budget_inFlightState_disablesFieldsAndShowsProgressCopy() {
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalBudget2026Screen(
                    state = state,
                    onSaveBudget = { _, _ -> },
                    onBack = {},
                    mutationInFlight = true,
                )
            }
        }
        composeRule.onNodeWithTag("s7_budget_save").assertIsNotEnabled()
        composeRule.onNodeWithText("Αποθήκευση…").assertIsDisplayed()
        composeRule.onNodeWithTag("s7_budget_limit").assertIsNotEnabled()
        composeRule.onNodeWithTag("s7_budget_threshold").assertIsNotEnabled()
    }
}
