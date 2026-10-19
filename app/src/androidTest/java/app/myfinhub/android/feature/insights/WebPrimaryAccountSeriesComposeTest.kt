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

class WebPrimaryAccountSeriesComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun monthSeriesShowsCanonicalRoleLabelsSixDatesAndCurrentCutoff() {
        val scope=InsightPeriodScope(
            id=INSIGHTS_PERIOD_MONTH,label="Μήνας",
            startDate="2026-10-01",endDate="2026-10-19",
            contextLabel="Μερικός μήνας",
            comparison=InsightsComparison("Οκτ","Σεπ",0.0,0.0,0.0,0.0),
            categories=emptyList(),
            primaryAccountSeries=InsightsPrimaryAccountSeries(
                payrollLabel="Μισθοδοσία",savingsLabel="Στόχος",
                months=(0..5).map { i ->
                    InsightsPrimaryAccountMonth(
                        monthIso="2026-" + (5+i).toString().padStart(2,'0'),
                        cutoffDateIso=if (i==5) "2026-10-19" else "2026-09-30",
                        payrollBalance=100.0+i,savingsBalance=500.0+i,
                    )
                },
            ),
        )
        rule.setContent { MyFinHubTheme {
            InsightsScreen(state=InsightsUiState(periods=listOf(scope)),
                onOpenSupportingActivity={})
        } }
        rule.onNodeWithTag("insights_list").performScrollToNode(
            hasTestTag("insights_primary_account_series"))
        rule.onNodeWithTag("insights_primary_account_series").assertIsDisplayed()
        rule.onNodeWithText("Εξέλιξη μισθοδοσίας και αποταμίευσης").assertIsDisplayed()
    }
}
