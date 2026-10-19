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

class WebCreditPortfolioComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun creditPortfolioRendersArchivedDebtAndActiveLimitSeparately() {
        val scope=InsightPeriodScope(
            id=INSIGHTS_PERIOD_MONTH,label="Μήνας",
            startDate="2026-10-01",endDate="2026-10-19",
            contextLabel="Μερικός μήνας",
            comparison=InsightsComparison("Οκτ","Σεπ",0.0,0.0,0.0,0.0),
            categories=emptyList(),
            creditPortfolio=InsightsCreditPortfolio(
                activeCards=1,totalCards=2,debt=350.0,
                limit=900.0,available=550.0,usage=350.0/900.0,
                rows=listOf(
                    InsightsCreditCardRow("a","Alpha","alpha",true,200.0,900.0,700.0,200.0/900.0),
                    InsightsCreditCardRow("b","Archived","alpha",false,150.0,0.0,0.0,0.0),
                ),
            ),
        )
        rule.setContent { MyFinHubTheme {
            InsightsScreen(state=InsightsUiState(periods=listOf(scope)),
                onOpenSupportingActivity={})
        } }
        rule.onNodeWithTag("insights_list").performScrollToNode(
            hasTestTag("insights_credit_portfolio"))
        rule.onNodeWithTag("insights_credit_portfolio").assertIsDisplayed()
        rule.onNodeWithText("Εικόνα πιστωτικών καρτών").assertIsDisplayed()
    }
}
