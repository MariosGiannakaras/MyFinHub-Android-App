package app.myfinhub.android.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.home.syntheticHomeUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Full native Home → phone Settings route acceptance, not the retired Web admin screen. */
class D6MobileSettingsNavigationAcceptanceTest {
    @get:Rule val compose = createComposeRule()

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
        compose.onNodeWithText("Εφαρμογή και συσκευή").assertIsDisplayed()
        compose.onNodeWithText("Εμφάνιση ποσών").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Αποσύνδεση από αυτή τη συσκευή")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, logoutCount) }
        compose.onNodeWithText("Αντίγραφο οικονομικών δεδομένων").assertDoesNotExist()
        compose.onNodeWithText("Αυτόματη ταξινόμηση").assertDoesNotExist()
    }
}
