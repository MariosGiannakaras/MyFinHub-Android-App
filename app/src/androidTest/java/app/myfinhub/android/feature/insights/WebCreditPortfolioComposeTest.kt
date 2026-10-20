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
    @Test fun archivedDebtIsVisibleAndStatusIsNotMisrepresentedAsActive() {
        val month=InsightPeriodScope(
            id=INSIGHTS_PERIOD_MONTH,label="Μήνας",
            startDate="2026-10-01",endDate="2026-10-20",
            contextLabel="Μερικός",
            comparison=InsightsComparison("Οκτ","Σεπ",800.0,120.0,800.0,150.0),
            categories=emptyList(),
            creditPortfolio=InsightsCreditPortfolio(
                activeCards=1,totalCards=2,debt=350.0,limit=700.0,
                available=350.0,usage=0.5,
                cards=listOf(
                    InsightsCreditCardSnapshot("a","Visa",true,250.0,700.0,450.0,250.0/700.0),
                    InsightsCreditCardSnapshot("b","Archived",false,100.0,0.0,0.0,0.0),
                ),
            ),
        )
        rule.setContent { MyFinHubTheme {
            InsightsScreen(state=InsightsUiState(periods=listOf(month)),
                onOpenSupportingActivity={})
        } }
        rule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_credit_portfolio"))
        rule.onNodeWithTag("insights_credit_portfolio").assertIsDisplayed()
        rule.onNodeWithText("Archived · Αρχειοθετημένη").assertIsDisplayed()
        rule.onNodeWithTag("insights_credit_portfolio_totals").assertIsDisplayed()
    }
}
