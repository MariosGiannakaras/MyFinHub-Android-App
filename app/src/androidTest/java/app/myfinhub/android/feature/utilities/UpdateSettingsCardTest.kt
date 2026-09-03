package app.myfinhub.android.feature.utilities

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.myfinhub.android.core.update.LocalUpdateController
import app.myfinhub.android.core.update.UpdateController
import app.myfinhub.android.core.update.UpdateRelease
import app.myfinhub.android.core.update.UpdateUiState
import app.myfinhub.android.designsystem.MyFinHubTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UpdateSettingsCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun availableUpdate_isVisibleAndDownloadActionIsWired() {
        var downloads = 0
        composeRule.setContent {
            MyFinHubTheme {
                CompositionLocalProvider(
                    LocalUpdateController provides UpdateController(
                        state = UpdateUiState.Available(release()),
                        download = { downloads += 1 },
                    ),
                ) {
                    ProductionSettingsScreen(
                        state = FrontendUtilitiesUiState(),
                        onAction = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Νέα έκδοση 0.2.0 διαθέσιμη").assertIsDisplayed()
        composeRule.onNodeWithText("Λήψη ενημέρωσης").performClick()
        assertEquals(1, downloads)
    }

    @Test
    fun permissionRequired_exposesOnlyPlatformPermissionHandoffActions() {
        var permissionOpens = 0
        var installs = 0
        composeRule.setContent {
            MyFinHubTheme {
                CompositionLocalProvider(
                    LocalUpdateController provides UpdateController(
                        state = UpdateUiState.PermissionRequired(release(), File("/tmp/verified.apk")),
                        install = { installs += 1 },
                        openInstallPermission = { permissionOpens += 1 },
                    ),
                ) {
                    UpdateSettingsCard(
                        currentVersionName = "0.1.0",
                        state = LocalUpdateController.current.state,
                        onCheck = {},
                        onDownload = {},
                        onInstall = LocalUpdateController.current.install,
                        onOpenInstallPermission = LocalUpdateController.current.openInstallPermission,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Άνοιγμα ρύθμισης εγκατάστασης").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Έλεγχος άδειας και εγκατάσταση").assertIsDisplayed().performClick()
        assertEquals(1, permissionOpens)
        assertEquals(1, installs)
    }

    private fun release() = UpdateRelease(
        versionCode = 2,
        versionName = "0.2.0",
        downloadUrl = "https://storage.example.test/storage/v1/object/authenticated/android-releases/0.2.0/MyFinHub.apk",
        sha256 = "a".repeat(64),
        sizeBytes = 24L * 1024L * 1024L,
        mandatory = false,
        notes = "Βελτιώσεις σταθερότητας",
        publishedAt = "2026-09-03T12:00:00Z",
    )
}
