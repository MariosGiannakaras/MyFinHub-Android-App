package app.myfinhub.android.feature.insights

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

class WebReportLoanBurdenComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun nativeMonthlyInsightsDisplaysRealLongTermLoanBurden() {
        val scope=InsightPeriodScope(
            id=INSIGHTS_PERIOD_MONTH,label="Μήνας",
            startDate="2026-10-01",endDate="2026-10-19",
            contextLabel="Μερικός μήνας",
            comparison=InsightsComparison("Οκτ","Σεπ",900.0,100.0,900.0,110.0),
            categories=emptyList(),
            loanBurden=InsightsLoanBurden(
                total=150.0,count=1,rows=listOf(
                    InsightsLoanObligation("loan","Δάνειο",150.0,900.0,6),
                ),
            ),
        )
        rule.setContent { MyFinHubTheme {
            InsightsScreen(
                state=InsightsUiState(periods=listOf(scope)),
                onOpenSupportingActivity={},
            )
        } }
        rule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_loan_burden"))
        rule.onNodeWithTag("insights_loan_burden").assertExists()
        rule.onNodeWithTag("insights_loan_burden_total").assertExists()
        rule.onNodeWithTag("insights_loan_burden_row_0").assertExists()
    }
}
