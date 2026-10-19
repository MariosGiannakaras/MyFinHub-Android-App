package app.myfinhub.android.feature.insights

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** Web six-month flow and privacy-aware text fallback within real Compose. */
class WebSixMonthReportFlowComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun expandedInsightsExposeSixMonthsOfCumulativeAmounts() {
        val points = (0..5).map { index ->
            TrendPoint(
                label="Μήν $index", income=100.0+index,
                expense=40.0+index,
                monthIso=String.format("2026-%02d", index + 1),
                saving=10.0, cumulativeNet=60.0*(index+1),
                isPartial=index==5,
            )
        }
        val scope=InsightPeriodScope(
            id=INSIGHTS_PERIOD_MONTH,label="Μήνας",
            startDate="2026-06-01",endDate="2026-06-15",
            contextLabel="Μερικός μήνας",
            comparison=InsightsComparison("Ιουν","Μαϊ",100.0,40.0,100.0,30.0),
            categories=emptyList(),
        )
        rule.setContent { MyFinHubTheme {
            InsightsScreen(
                state=InsightsUiState(periods=listOf(scope), monthlyTrend=points),
                onOpenSupportingActivity={},
                initialDetailsExpanded=true,
            )
        } }
        rule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_details"))
        rule.onNodeWithTag("insights_six_month_row_0").assertExists()
        rule.onNodeWithTag("insights_six_month_row_5").assertExists()
        rule.onNodeWithTag("insights_chart_text_alternative").assertExists()
        rule.onNodeWithTag("insights_six_month_row_6").assertDoesNotExist()
    }
}
