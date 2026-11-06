package app.myfinhub.android.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
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
import app.myfinhub.android.feature.plan.PlanUiState
import app.myfinhub.android.feature.plan.PlannedFlow
import app.myfinhub.android.feature.plan.PlannedItem
import app.myfinhub.android.feature.plan.PlannedKind
import app.myfinhub.android.feature.plan.RecurringManageItem
import app.myfinhub.android.feature.quickentry.QuickEntryAccountOption
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import app.myfinhub.android.feature.quickentry.reduceQuickEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Real Plan → Forecast → editable recurring payment route. No finance writes. */
class NativeForecastRecurringPaymentComposeTest {
    @get:Rule val rule = createComposeRule()
    private val bank = QuickEntryAccountOption("bank","Τράπεζα","bank")
    private val row = PlannedItem(
        id="electric",title="Ρεύμα",dueLabel="25 Νοε",amount=250.0,
        kind=PlannedKind.RECURRING,flow=PlannedFlow.OBLIGATION,
        dueDateIso="2026-11-25",
    )
    private fun recurring() = RecurringManageItem(
        id="electric",title="Ρεύμα",amount=80.0,status="active",
        accountId="bank",category="Σπίτι",amountMode="variable",
        estimateMin=60.0,estimateMax=100.0,
    )
    private fun plan(owner: RecurringManageItem=recurring()) = PlanUiState(
        items=listOf(row),recurringManageItems=listOf(owner),
        forecastStartDateIso="2026-11-06",forecastEndDateIso="2026-12-06",
        forecastHorizonDays=30,
    )
    private fun openForecast() {
        rule.onNodeWithContentDescription("Πλάνο",useUnmergedTree=true).performClick()
        rule.onNodeWithTag("s7_plan_root")
            .performScrollToNode(hasTestTag("s7_forecast_link"))
        rule.onNodeWithTag("s7_forecast_link").performClick()
        rule.onNodeWithTag("s7_forecast_expand").performClick()
    }

    @Test fun variableForecastCreatesLinkedDraftWithNoEstimatedPaymentAmount() {
        var draft by mutableStateOf(QuickEntryUiState(accounts=listOf(bank),
            accountId="bank",defaultExpenseAccountId="bank",amountText="999.99"))
        val actions=mutableListOf<QuickEntryAction>()
        rule.setContent { MyFinHubTheme {
            MyFinHubAppContent(
                homeState=syntheticHomeUiState(),onHomeAction={},
                planState=plan(),quickEntryState=draft,
                onQuickEntryAction={ action ->
                    actions+=action
                    draft=reduceQuickEntry(draft,action)
                },
            )
        } }
        openForecast()
        rule.onNodeWithTag("s7_forecast_record_RECURRING:electric")
            .performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(QuickEntryKind.EXPENSE,draft.kind)
            assertEquals("electric",draft.recurringId)
            assertEquals("bank",draft.accountId)
            assertEquals("",draft.amountText)
            assertFalse(actions.any { it==QuickEntryAction.Save })
            assertFalse(draft.persisted)
        }
        rule.onNodeWithContentDescription("Πίσω").assertExists()
    }

    @Test fun invalidPayerDisablesForecastFinancialAction() {
        rule.setContent { MyFinHubTheme {
            MyFinHubAppContent(
                homeState=syntheticHomeUiState(),onHomeAction={},
                planState=plan(recurring().copy(accountId="removed")),
                quickEntryState=QuickEntryUiState(accounts=listOf(bank)),
                onQuickEntryAction={ error("unsafe recurring payment") },
            )
        } }
        openForecast()
        rule.onNodeWithTag("s7_forecast_record_RECURRING:electric")
            .performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Το πάγιο ή ο λογαριασμός πληρωμής χρειάζεται έλεγχο",substring=true)
            .assertExists()
    }
}
