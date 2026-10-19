package app.myfinhub.android.feature.insights

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

class WebReportInsightSignalsComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun monthSignalsRenderPrivacyAwareMoneyRatiosAndTrailingHistory() {
        val scope=InsightPeriodScope(
            id=INSIGHTS_PERIOD_MONTH,label="Μήνας",
            startDate="2026-10-01",endDate="2026-10-19",
            contextLabel="Μερικός μήνας",
            comparison=InsightsComparison("Οκτ","Σεπ",1000.0,200.0,800.0,100.0),
            categories=emptyList(),
            monthlyReportSignals=InsightMonthlyReportSignals(
                incomeChange=.25,expenseChange=1.0,savingChange=2.0,
                netFlow=800.0,savingsRate=.15,previousSavingsRate=.0625,
                trailingExpenseAverage=200.0,expenseVsTrailingAverage=0.0,
                recurringMonthlyTotal=90.0,recurringBurden=.09,
                topCategoryName="Τρόφιμα › Σούπερ",
                topCategoryAmount=200.0,topCategoryShare=1.0,
                topCategoryChange=1.0,sufficientExpenseHistory=true,
            ),
        )
        rule.setContent { MyFinHubTheme {
            InsightsScreen(state=InsightsUiState(periods=listOf(scope)),
                onOpenSupportingActivity={})
        } }
        rule.onNodeWithTag("insights_list").performScrollToNode(
            hasTestTag("insights_monthly_report_signals"))
        rule.onNodeWithTag("insights_monthly_report_signals").assertIsDisplayed()
        rule.onNodeWithTag("insights_report_savings_rate").assertIsDisplayed()
        rule.onNodeWithText("Δείκτες μήνα").assertIsDisplayed()
        rule.onNodeWithTag("insights_report_trailing_expense").assertIsDisplayed()
    }
}
