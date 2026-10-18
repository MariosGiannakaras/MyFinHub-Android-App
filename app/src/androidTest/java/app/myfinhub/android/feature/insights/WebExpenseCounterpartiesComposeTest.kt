package app.myfinhub.android.feature.insights

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

class WebExpenseCounterpartiesComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun monthlyCounterpartiesPresentCanonicalSourceAndCountsReadOnly() {
        val sample=InsightCounterparty(
            title="MARKET",category="Τρόφιμα",amount=75.0,
            share=0.75,count=3,lastDateIso="2026-10-14",
        )
        val month=InsightPeriodScope(
            id=INSIGHTS_PERIOD_MONTH,label="Μήνας",
            startDate="2026-10-01",endDate="2026-10-15",
            contextLabel="Μερικός μήνας",
            comparison=InsightsComparison("Οκτ","Σεπ",900.0,100.0,900.0,110.0),
            categories=emptyList(),counterparties=listOf(sample),
        )
        rule.setContent { MyFinHubTheme {
            InsightsScreen(
                state=InsightsUiState(periods=listOf(month)),
                onOpenSupportingActivity={},
            )
        } }
        rule.onNodeWithTag("insights_counterparties").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("insights_counterparty_0").assertIsDisplayed()
        rule.onNodeWithText("MARKET").assertIsDisplayed()
        rule.onNodeWithText("Τρόφιμα · 3 κινήσεις",substring=true).assertIsDisplayed()
    }
}
