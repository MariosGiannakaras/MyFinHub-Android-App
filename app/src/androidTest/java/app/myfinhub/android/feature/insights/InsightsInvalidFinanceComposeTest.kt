package app.myfinhub.android.feature.insights

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.utilities.AmountVisibilityPreference
import org.junit.After
import org.junit.Rule
import org.junit.Test

class InsightsInvalidFinanceComposeTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun restorePrivacy() { AmountVisibilityPreference.write(context, false) }

    private val corruptedScope = InsightPeriodScope(
        id = INSIGHTS_PERIOD_MONTH, label = "Μήνας",
        startDate = "2026-10-01", endDate = "2026-10-30",
        contextLabel = "Οκτ 2026",
        comparison = InsightsComparison("Οκτ", "Σεπ",
            1000.0, Double.NaN, 900.0, 100.0),
        categories = listOf(InsightCategory("Τρόφιμα", 100.0, Float.NaN)),
        operationalReport = InsightsOperationalReport(
            income = 1000.0, expense = 400.0, saving = Double.NaN,
            previousIncome = 950.0, previousExpense = 340.0,
            creditDebt = 50.0, creditLimit = 100.0, creditUsage = Double.NaN,
            creditAvailable = 50.0, activeCreditCards = 1,
            receivables = 0.0, recurring = 20.0, savingsTotal = 100.0,
            savingsBySource = emptyMap(), budget = 500.0,
            budgetRemaining = 100.0,
            accountBalances = listOf(InsightsOperationalAccountBalance(
                "cash", "Μετρητά", Double.POSITIVE_INFINITY)),
        ),
        historicTrend = listOf(TrendPoint("Οκτ", Double.NaN, 400.0)),
    )

    @Test fun invalidNativeReportRendersExplicitWarningsWithoutFakeZeroOrNaN() {
        AmountVisibilityPreference.write(context, true)
        rule.setContent { MyFinHubTheme {
            InsightsScreen(state = InsightsUiState(periods = listOf(corruptedScope)),
                onOpenSupportingActivity = {})
        } }
        rule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_spending_comparison"))
        rule.onNodeWithText("Μη έγκυρη σύγκριση εξόδων").assertExists()
        rule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_operational_report"))
        rule.onAllNodesWithText("Μη έγκυρο ποσοστό", substring = true)[0].assertExists()
        rule.onNodeWithTag("insights_operational_accounts_toggle")
            .performScrollTo().performClick()
        rule.onAllNodesWithText("Μη έγκυρο ποσό", substring = true)[0].assertExists()
        rule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_details"))
        rule.onNodeWithTag("insights_details_toggle").performClick()
        rule.onNodeWithTag("insights_chart_invalid_data").performScrollTo().assertExists()
        rule.onNodeWithText("NaN", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Infinity", substring = true).assertDoesNotExist()
    }

    @Test fun hiddenInvalidFinanceDoesNotExposeNumericOrWarningValues() {
        AmountVisibilityPreference.write(context, false)
        rule.setContent { MyFinHubTheme {
            InsightsScreen(state = InsightsUiState(periods = listOf(corruptedScope)),
                onOpenSupportingActivity = {})
        } }
        rule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_operational_report"))
        rule.onNodeWithText("Μη έγκυρο ποσοστό", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_categories"))
        rule.onNodeWithText("NaN", substring = true).assertDoesNotExist()
    }
}
