package app.myfinhub.android.feature.utilities

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private fun s9Diagnostics() = AppDiagnosticsSnapshot(
    versionName = "1.0.0-rc8",
    buildType = "release",
    environment = "Production",
    apiHost = "api.myfinhub.example",
    networkStatus = "Συνδεδεμένο",
    apiStatus = "Συγχρονισμένο",
    sessionStatus = "Ενεργή και επαληθευμένη",
    lastSuccessfulSync = null,
    lastDiagnosticCode = "SYNC-READY",
)

@PreviewTest
@Preview(name = "s9_settings_light", widthDp = 412, heightDp = 1050, showBackground = true)
@Composable
fun S9SettingsLight() = MyFinHubTheme(false) {
    ProductionSettingsScreen(
        state = FrontendUtilitiesUiState(),
        onAction = {},
        onBack = {},
        diagnostics = s9Diagnostics(),
        noticeHistoryCount = 3,
        onLogout = {},
    )
}

@PreviewTest
@Preview(name = "s9_settings_dark", widthDp = 412, heightDp = 1050, showBackground = true)
@Composable
fun S9SettingsDark() = MyFinHubTheme(true) {
    ProductionSettingsScreen(
        state = FrontendUtilitiesUiState(), onAction = {}, onBack = {}, diagnostics = s9Diagnostics(), noticeHistoryCount = 3, onLogout = {},
    )
}

@PreviewTest
@Preview(name = "s9_settings_large", widthDp = 412, heightDp = 1400, fontScale = 1.5f, showBackground = true)
@Composable
fun S9SettingsLarge() = MyFinHubTheme(false) {
    ProductionSettingsScreen(
        state = FrontendUtilitiesUiState(), onAction = {}, onBack = {}, diagnostics = s9Diagnostics(), noticeHistoryCount = 3, onLogout = {},
    )
}

@PreviewTest
@Preview(name = "s9_diagnostics_light", widthDp = 412, heightDp = 1050, showBackground = true)
@Composable
fun S9DiagnosticsLight() = MyFinHubTheme(false) { ProductionDiagnosticsScreen(s9Diagnostics(), {}) }

@PreviewTest
@Preview(name = "s9_diagnostics_dark", widthDp = 412, heightDp = 1050, showBackground = true)
@Composable
fun S9DiagnosticsDark() = MyFinHubTheme(true) { ProductionDiagnosticsScreen(s9Diagnostics(), {}) }

@PreviewTest
@Preview(name = "s9_diagnostics_large", widthDp = 412, heightDp = 1350, fontScale = 1.5f, showBackground = true)
@Composable
fun S9DiagnosticsLarge() = MyFinHubTheme(false) { ProductionDiagnosticsScreen(s9Diagnostics(), {}) }
