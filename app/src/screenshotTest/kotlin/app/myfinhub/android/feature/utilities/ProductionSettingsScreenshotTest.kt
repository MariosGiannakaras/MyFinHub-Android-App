package app.myfinhub.android.feature.utilities

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "production_settings_compact_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionSettingsCompactLightScreenshot() {
    ProductionSettingsFixture(darkTheme = false)
}

@PreviewTest
@Preview(name = "production_settings_compact_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionSettingsCompactDarkScreenshot() {
    ProductionSettingsFixture(darkTheme = true)
}

@PreviewTest
@Preview(
    name = "production_settings_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun ProductionSettingsCompactLargeFontScreenshot() {
    ProductionSettingsFixture(darkTheme = false)
}

@Composable
private fun ProductionSettingsFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        ProductionSettingsScreen(
            state = FrontendUtilitiesUiState(),
            onAction = {},
            onBack = {},
            diagnostics = null,
            onLogout = {},
        )
    }
}