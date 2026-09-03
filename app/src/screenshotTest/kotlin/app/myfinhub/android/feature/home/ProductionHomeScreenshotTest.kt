package app.myfinhub.android.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "production_home_compact_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionHomeCompactLightScreenshot() {
    ProductionHomeFixture(darkTheme = false)
}

@PreviewTest
@Preview(name = "production_home_compact_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionHomeCompactDarkScreenshot() {
    ProductionHomeFixture(darkTheme = true)
}

@PreviewTest
@Preview(
    name = "production_home_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun ProductionHomeCompactLargeFontScreenshot() {
    ProductionHomeFixture(darkTheme = false)
}

@Composable
private fun ProductionHomeFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        ProductionHomeScreen(
            state = syntheticHomeUiState().copy(amountsVisible = true),
            onAction = {},
            onOpenAttention = {},
            onOpenSettings = {},
            onOpenQuickEntry = {},
        )
    }
}