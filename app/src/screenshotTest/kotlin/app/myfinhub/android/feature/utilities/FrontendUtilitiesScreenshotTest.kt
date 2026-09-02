package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
@Preview(name = "settings_diagnostics_phone", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun SettingsDiagnosticsPhoneScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            DiagnosticsCard(
                diagnostics = AppDiagnosticsSnapshot(
                    versionName = "0.1.0",
                    buildType = "debug",
                    environment = "Production public client",
                    apiHost = "mgfinhub.vercel.app",
                    networkStatus = "Χωρίς σύνδεση",
                    apiStatus = "Απαιτεί ανάκτηση",
                    sessionStatus = "Ενεργή · AAL2",
                    lastSuccessfulSync = "2026-09-02T06:20:00Z",
                    lastDiagnosticCode = "MFH-NET-OFFLINE-PENDING",
                ),
                modifier = Modifier.padding(16.dp),
            )
        }
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
