package app.myfinhub.android.feature.activity

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

class ActivityMonthlyOverviewScopeDisclosureTest {
    @get:Rule val rule = createComposeRule()

    @Test fun filteredListClearlyDistinguishesUnfilteredMonthlyFinanceTotals() {
        val month = ActivityMonthlyOverview(
            month = "2026-11", income = 100.0, expense = 40.0, net = 60.0,
            transactionCount = 2, previousIncome = 80.0, previousExpense = 30.0,
            previousNet = 50.0, previousTransactionCount = 2,
        )
        val state = mutableStateOf(
            ActivityUiState(items = emptyList(), monthOverview = month, query = "coffee"),
        )
        rule.setContent {
            MyFinHubTheme {
                ActivityLedgerScreen(
                    state = state.value,
                    onAction = {}, onOpenDetail = {}, onOpenQuickEntry = {},
                )
            }
        }
        rule.onNodeWithTag("activity_list")
            .performScrollToNode(hasTestTag("activity_month_summary_toggle"))
        rule.onNodeWithTag("activity_month_summary_toggle").performClick()
        rule.onNodeWithTag("activity_list")
            .performScrollToNode(hasTestTag("activity_month_summary_unfiltered_note"))
        rule.onNodeWithTag("activity_month_summary_unfiltered_note").assertIsDisplayed()

        rule.runOnIdle { state.value = state.value.copy(query = "") }
        rule.onNodeWithTag("activity_month_summary_unfiltered_note").assertDoesNotExist()
    }
}
