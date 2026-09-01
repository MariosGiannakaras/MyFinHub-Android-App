package app.myfinhub.android.feature.utilities

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "settings_compact_large_font", widthDp = 412, heightDp = 915, fontScale = 1.5f, showBackground = true)
@Composable
fun SettingsCompactLargeFontScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        SettingsScreen(state = FrontendUtilitiesUiState(), onAction = {}, onBack = {})
    }
}

@PreviewTest
@Preview(name = "change_history_compact", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ChangeHistoryCompactScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        ChangeHistoryScreen(state = FrontendUtilitiesUiState(), onAction = {}, onBack = {})
    }
}
