package app.myfinhub.android.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private fun ownerInsightsScreenshotState(): InsightsUiState {
    val comparison = InsightsComparison(
        currentLabel = "1 Σεπ – 10 Σεπ 2026",
        previousLabel = "1 Αυγ – 10 Αυγ 2026",
        currentIncome = 920.0,
        currentExpense = 455.0,
        previousIncome = 920.0,
        previousExpense = 510.0,
    )
    val categories = listOf(
        InsightCategory("Στέγαση", 210.0, 0.46f),
        InsightCategory("Τρόφιμα", 125.0, 0.27f),
        InsightCategory("Μετακινήσεις", 70.0, 0.15f),
        InsightCategory("Λοιπά", 50.0, 0.11f, listOf("Έξοδος", "Υγεία", "Αγορές")),
    )
    return InsightsUiState(
        monthlyTrend = listOf(
            TrendPoint("Ιουν", 1_840.0, 980.0),
            TrendPoint("Ιουλ", 1_920.0, 1_260.0),
            TrendPoint("Αυγ", 1_840.0, 910.0),
            TrendPoint("Σεπ", 920.0, 455.0, isPartial = true, periodDetail = "έως 10 Σεπ"),
        ),
        categories = categories,
        averageMonthlySpend = 1_050.0,
        comparison = comparison,
        categoryStartDate = "2026-09-01",
        categoryEndDate = "2026-09-10",
        periods = listOf(
            InsightPeriodScope(INSIGHTS_PERIOD_MONTH, "Μήνας", "2026-09-01", "2026-09-10", "Μερικός μήνας · έως 10 Σεπ", comparison, categories),
            InsightPeriodScope(INSIGHTS_PERIOD_30_DAYS, "30 ημ.", "2026-08-12", "2026-09-10", "Κυλιόμενο διάστημα 30 ημερών", comparison, categories),
            InsightPeriodScope(INSIGHTS_PERIOD_90_DAYS, "90 ημ.", "2026-06-13", "2026-09-10", "Κυλιόμενο διάστημα 90 ημερών", comparison, categories),
        ),
    )
}

@PreviewTest
@Preview(name = "insights_compact_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun InsightsCompactLightScreenshot() = InsightsFixture(false)

@PreviewTest
@Preview(name = "insights_compact_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun InsightsCompactDarkScreenshot() = InsightsFixture(true)

@PreviewTest
@Preview(name = "insights_compact_large_font", widthDp = 412, heightDp = 1050, fontScale = 1.5f, showBackground = true)
@Composable
fun InsightsCompactLargeFontScreenshot() = InsightsFixture(false)

@PreviewTest
@Preview(name = "insights_full_large_font", widthDp = 412, heightDp = 1900, fontScale = 1.5f, showBackground = true)
@Composable
fun InsightsFullLargeFontScreenshot() = InsightsFixture(false)

@Composable
private fun InsightsFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        InsightsScreen(
            state = ownerInsightsScreenshotState(),
            onOpenSupportingActivity = {},
        )
    }
}
