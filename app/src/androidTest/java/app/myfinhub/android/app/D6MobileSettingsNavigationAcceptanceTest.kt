package app.myfinhub.android.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.home.syntheticHomeUiState
import app.myfinhub.android.feature.utilities.AppDiagnosticsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Full native Home → phone Settings route acceptance, not the retired Web admin screen. */
class D6MobileSettingsNavigationAcceptanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realSettingsRouteOpensExistingPrivacySafeNoticeHistory() {
        compose.setContent {
            MyFinHubTheme {
                MyFinHubAppContent(
                    homeState = syntheticHomeUiState(),
                    onHomeAction = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Ρυθμίσεις").performClick()
        compose.onNodeWithText("Ιστορικό ειδοποιήσεων")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Δεν υπάρχουν ειδοποιήσεις").assertIsDisplayed()
    }

    @Test fun realSettingsRouteOpensExistingDiagnosticsOnlyWithSnapshot() {
        compose.setContent {
            MyFinHubTheme {
                MyFinHubAppContent(
                    homeState = syntheticHomeUiState(),
                    onHomeAction = {},
                    diagnostics = AppDiagnosticsSnapshot(
                        versionName = "1.0.0-test",
                        buildType = "debug",
                        environment = "Test",
                        apiHost = "api.example.invalid",
                        networkStatus = "Χωρίς σύνδεση",
                        apiStatus = "Χωρίς δεδομένα",
                        sessionStatus = "Ενεργή",
                        lastSuccessfulSync = "2026-11-16T10:00:00Z",
                        lastDiagnosticCode = null,
                    ),
                )
            }
        }
        compose.onNodeWithContentDescription("Ρυθμίσεις").performClick()
        compose.onNodeWithText("Διαγνωστικά και υποστήριξη")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Ασφαλή στοιχεία για έλεγχο και υποστήριξη")
            .assertIsDisplayed()
    }

    @Test fun homeSettingsRouteKeepsPrivacyAndLogoutReachableWithoutWebAdministration() {
        var logoutCount = 0
        compose.setContent {
            MyFinHubTheme {
                MyFinHubAppContent(
                    homeState = syntheticHomeUiState(),
                    onHomeAction = {},
                    onLogout = { logoutCount += 1 },
                )
            }
        }

        compose.onNodeWithContentDescription("Ρυθμίσεις").assertIsDisplayed().performClick()
        compose.onNodeWithText("Ρυθμίσεις").assertIsDisplayed()
        compose.onNodeWithText("Εμφάνιση ποσών").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Αποσύνδεση από αυτή τη συσκευή")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, logoutCount) }
        compose.onNodeWithText("Αντίγραφο οικονομικών δεδομένων").assertDoesNotExist()
        compose.onNodeWithText("Αυτόματη ταξινόμηση").assertDoesNotExist()
    }
}
