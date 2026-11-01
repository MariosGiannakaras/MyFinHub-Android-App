package app.myfinhub.android.feature.utilities

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** D6 acceptance for the actual phone Settings route, not legacy Web administration. */
class MobileDeviceSettingsJourneyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun phoneSettingsExposeMobilePrivacyNotificationsAndLogoutOnly() {
        var logoutCalls = 0
        compose.setContent {
            MyFinHubTheme {
                MobileDeviceSettingsScreen(
                    onBack = {},
                    onLogout = { logoutCalls += 1 },
                )
            }
        }
        compose.onNodeWithText("Ρυθμίσεις συσκευής").assertIsDisplayed()
        compose.onNodeWithText("Εμφάνιση ποσών").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ειδοποιήσεις Android").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Αποσύνδεση από αυτή τη συσκευή")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, logoutCalls) }

        // Per owner-approved mobile scope, Web finance administration is NOT a phone destination.
        compose.onNodeWithText("Αντίγραφο οικονομικών δεδομένων").assertDoesNotExist()
        compose.onNodeWithText("Έλεγχος αρχείου backup").assertDoesNotExist()
        compose.onNodeWithText("Αυτόματη ταξινόμηση").assertDoesNotExist()
        compose.onNodeWithText("Ιστορικό αλλαγών στον server").assertDoesNotExist()
        compose.onNodeWithText("Συνδεδεμένες συσκευές").assertDoesNotExist()
    }

    @Test fun privacySwitchUpdatesSharedDevicePreferenceWithoutFinanceWrite() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originallyVisible = AmountVisibilityPreference.read(context)
        try {
            AmountVisibilityPreference.write(context, false)
            compose.setContent {
                MyFinHubTheme {
                    MobileDeviceSettingsScreen(onBack = {}, onLogout = {})
                }
            }
            compose.onNodeWithContentDescription("Εμφάνιση ποσών")
                .performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(true, AmountVisibilityPreference.read(context))
            }
            compose.onNodeWithContentDescription("Εμφάνιση ποσών").performClick()
            compose.runOnIdle {
                assertEquals(false, AmountVisibilityPreference.read(context))
            }
        } finally {
            AmountVisibilityPreference.write(context, originallyVisible)
        }
    }

    @Test fun unauthenticatedPhoneSettingsCannotExposeLogoutAction() {
        compose.setContent {
            MyFinHubTheme {
                MobileDeviceSettingsScreen(onBack = {}, onLogout = null)
            }
        }
        compose.onNodeWithText("Αποσύνδεση από αυτή τη συσκευή").assertDoesNotExist()
        compose.onNodeWithText("Ασφάλεια και ειδοποιήσεις").performScrollTo().assertIsDisplayed()
    }
}
