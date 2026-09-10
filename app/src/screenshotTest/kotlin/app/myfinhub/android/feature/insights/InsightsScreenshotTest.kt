package app.myfinhub.android.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private fun ownerInsightsScreenshotState() = InsightsUiState(
    monthlyTrend = listOf(
        TrendPoint("Ιουν", 1_840.0, 980.0),
        TrendPoint("Ιουλ", 1_920.0, 1_260.0),
        TrendPoint("Αυγ", 1_840.0, 910.0),
        TrendPoint("Σεπ", 920.0, 455.0, isPartial = true, periodDetail = "έως 10 Σεπ"),
    ),
    categories = listOf(
        InsightCategory("Στέγαση", 210.0, 0.46f),
        InsightCategory("Τρόφιμα", 125.0, 0.27f),
        InsightCategory("Μετακινήσεις", 70.0, 0.15f),
        InsightCategory("Έξοδος", 50.0, 0.11f),
    ),
    averageMonthlySpend = 1_050.0,
    comparison = InsightsComparison(
        currentLabel = "1–10 Σεπ 2026",
        previousLabel = "1–10 Αυγ 2026",
        currentIncome = 920.0,
        currentExpense = 455.0,
        previousIncome = 920.0,
        previousExpense = 510.0,
    ),
)

@PreviewTest
@Preview(name = "insights_compact_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun InsightsCompactLightScreenshot() {
    InsightsFixture(darkTheme = false)
}

@PreviewTest
@Preview(name = "insights_compact_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun InsightsCompactDarkScreenshot() {
    InsightsFixture(darkTheme = true)
}

@PreviewTest
@Preview(
    name = "insights_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun InsightsCompactLargeFontScreenshot() {
    InsightsFixture(darkTheme = false)
}

@PreviewTest
@Preview(
    name = "insights_full_large_font",
    widthDp = 412,
    heightDp = 1800,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun InsightsFullLargeFontScreenshot() {
    InsightsFixture(darkTheme = false)
}

@Composable
private fun InsightsFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        InsightsScreen(
            state = ownerInsightsScreenshotState(),
            onOpenSupportingActivity = {},
            onOpenCategoryActivity = {},
        )
    }
}
