package app.myfinhub.android.feature.insights

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

class WebMonthlyInsightRatesComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun monthSummaryShowsWebThreeMonthBaselineWithoutMutatingTransactions() {
        val scope=InsightPeriodScope(
            id=INSIGHTS_PERIOD_MONTH,label="Μήνας",
            startDate="2026-10-01",endDate="2026-10-19",
            contextLabel="Μερικός μήνας",
            comparison=InsightsComparison("Οκτ","Σεπ",1000.0,300.0,900.0,200.0),
            categories=emptyList(),
            monthlyRates=InsightMonthlyRates(0.1,0.05,100.0,2.0,1.22),
        )
        rule.setContent { MyFinHubTheme {
            InsightsScreen(
                state=InsightsUiState(periods=listOf(scope)),
                onOpenSupportingActivity={},
            )
        } }
        rule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_monthly_rates"))
        rule.onNodeWithTag("insights_savings_rate").assertExists()
        rule.onNodeWithTag("insights_trailing_expense_average").assertExists()
    }
}
