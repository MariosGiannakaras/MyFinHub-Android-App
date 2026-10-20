package app.myfinhub.android.feature.insights

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

class WebOperationalReportComposeTest {
    @get:Rule val rule=createComposeRule()
    @Test fun monthSnapshotExposesCreditBudgetAndOptionalAccountBalances() {
        val scope=InsightPeriodScope(
            id=INSIGHTS_PERIOD_MONTH,label="Μήνας",
            startDate="2026-10-01",endDate="2026-10-20",
            contextLabel="Μερικός",
            comparison=InsightsComparison("Οκτ","Σεπ",900.0,140.0,900.0,100.0),
            categories=emptyList(),
            operationalReport=InsightsOperationalReport(
                income=900.0,expense=140.0,saving=20.0,
                previousIncome=800.0,previousExpense=110.0,
                creditDebt=55.0,creditLimit=300.0,creditUsage=55.0/300.0,
                creditAvailable=245.0,activeCreditCards=1,
                receivables=80.0,recurring=30.0,savingsTotal=20.0,
                savingsBySource=mapOf("cash_offset" to 20.0),
                budget=500.0,budgetRemaining=360.0,
                accountBalances=listOf(
                    InsightsOperationalAccountBalance("cash","Μετρητά",300.0),
                ),
            ),
        )
        rule.setContent { MyFinHubTheme {
            InsightsScreen(state=InsightsUiState(periods=listOf(scope)),
                onOpenSupportingActivity={})
        } }
        rule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_operational_report"))
        rule.onNodeWithTag("insights_operational_report").assertIsDisplayed()
        rule.onNodeWithTag("insights_operational_accounts_toggle")
            .performScrollTo().performClick()
        rule.onNodeWithTag("insights_operational_account_0")
            .performScrollTo().assertIsDisplayed()
    }
}
