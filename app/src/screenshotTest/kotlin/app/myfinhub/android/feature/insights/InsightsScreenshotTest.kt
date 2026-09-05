package app.myfinhub.android.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

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

@Composable
private fun InsightsFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        InsightsScreen(
            state = InsightsUiState(),
            onOpenSupportingActivity = {},
        )
    }
}
