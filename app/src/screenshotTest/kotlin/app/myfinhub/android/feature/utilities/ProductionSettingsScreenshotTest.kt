package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.core.update.LocalUpdateController
import app.myfinhub.android.core.update.UpdateController
import app.myfinhub.android.core.update.UpdateRelease
import app.myfinhub.android.core.update.UpdateUiState
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

// These production previews are the canonical Settings/update acceptance surfaces for the S24 workstream.
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

@PreviewTest
@Preview(
    name = "production_settings_update_available_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun ProductionSettingsUpdateAvailableLargeFontScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        Surface {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MyFinHubSpacing.lg),
            ) {
                UpdateSettingsCard(
                    currentVersionName = "0.1.0",
                    state = UpdateUiState.Available(updateRelease()),
                    onCheck = {},
                    onDownload = {},
                    onInstall = {},
                    onOpenInstallPermission = {},
                )
            }
        }
    }
}

@Composable
private fun ProductionSettingsFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(
            LocalUpdateController provides UpdateController(
                state = UpdateUiState.Available(updateRelease()),
            ),
        ) {
            ProductionSettingsScreen(
                state = FrontendUtilitiesUiState(),
                onAction = {},
                onBack = {},
                diagnostics = null,
                onLogout = {},
            )
        }
    }
}

private fun updateRelease() = UpdateRelease(
    versionCode = 2,
    versionName = "0.2.0",
    downloadUrl = "https://example.invalid/private.apk",
    sha256 = "a".repeat(64),
    sizeBytes = 24L * 1024L * 1024L,
    mandatory = false,
    notes = "Βελτιώσεις σταθερότητας και ιδιωτική ασφαλής ενημέρωση.",
    publishedAt = "2026-09-03T12:00:00Z",
)
