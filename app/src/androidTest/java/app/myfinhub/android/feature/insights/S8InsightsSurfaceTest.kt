package app.myfinhub.android.feature.insights

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class S8InsightsSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val month = InsightPeriodScope(
        id = INSIGHTS_PERIOD_MONTH,
        label = "Μήνας",
        startDate = "2026-09-01",
        endDate = "2026-09-10",
        contextLabel = "Μερικός μήνας · έως 10 Σεπ",
        comparison = InsightsComparison("1 Σεπ – 10 Σεπ 2026", "1 Αυγ – 10 Αυγ 2026", 900.0, 455.0, 900.0, 510.0),
        categories = listOf(InsightCategory("Τρόφιμα", 200.0, .44f)),
    )
    private val days30 = month.copy(id = INSIGHTS_PERIOD_30_DAYS, label = "30 ημ.", startDate = "2026-08-12")
    private val state = InsightsUiState(periods = listOf(month, days30), categories = month.categories, comparison = month.comparison)

    @Test
    fun periodSelector_changesRequestedStablePeriod() {
        var selected = INSIGHTS_PERIOD_MONTH
        composeRule.setContent {
            MyFinHubTheme {
                InsightsScreen(
                    state = state,
                    selectedPeriodId = selected,
                    onPeriodSelected = { selected = it },
                    onOpenSupportingActivity = {},
                )
            }
        }
        composeRule.onNodeWithTag("insights_period_${INSIGHTS_PERIOD_30_DAYS}").performClick()
        composeRule.runOnIdle { assertEquals(INSIGHTS_PERIOD_30_DAYS, selected) }
    }

    @Test
    fun categoryWholeRow_opensExactIdentityAndDateRange() {
        var result: Triple<InsightCategory, String, String>? = null
        composeRule.setContent {
            MyFinHubTheme {
                InsightsScreen(
                    state = state,
                    onOpenSupportingActivity = {},
                    onOpenCategoryActivity = { category, start, end -> result = Triple(category, start, end) },
                )
            }
        }
        composeRule.onNodeWithTag("insights_category_Τρόφιμα").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("Τρόφιμα", result?.first?.name)
            assertEquals("2026-09-01", result?.second)
            assertEquals("2026-09-10", result?.third)
        }
    }

    @Test
    fun secondaryIncomeNetTrend_isExpandableAndHasTextAlternative() {
        composeRule.setContent { MyFinHubTheme { InsightsScreen(state = state, onOpenSupportingActivity = {}) } }
        composeRule.onNodeWithTag("insights_details_toggle").performScrollTo().performClick()
        composeRule.onNodeWithText("Έσοδα περιόδου").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("insights_chart_text_alternative").performScrollTo().assertIsDisplayed()
    }
}
