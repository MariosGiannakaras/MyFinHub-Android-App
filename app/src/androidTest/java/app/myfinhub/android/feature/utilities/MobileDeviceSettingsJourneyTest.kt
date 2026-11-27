package app.myfinhub.android.feature.utilities

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import app.myfinhub.android.app.DeviceSessionsUiSnapshot
import app.myfinhub.android.core.network.DeviceSessionRecord
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
        compose.onNodeWithText("Ρυθμίσεις").assertIsDisplayed()
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

    @Test fun supportDestinationsAreSecondaryButReachableAndCallOnlyTheirHandlers() {
        var historyOpens = 0
        var diagnosticsOpens = 0
        compose.setContent {
            MyFinHubTheme {
                MobileDeviceSettingsScreen(
                    onBack = {},
                    onLogout = {},
                    noticeHistoryCount = 2,
                    onOpenNoticeHistory = { historyOpens += 1 },
                    onOpenDiagnostics = { diagnosticsOpens += 1 },
                )
            }
        }
        compose.onNodeWithText("Ιστορικό ειδοποιήσεων · 2")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Διαγνωστικά και υποστήριξη")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, historyOpens)
            assertEquals(1, diagnosticsOpens)
        }
        compose.onNodeWithText("Αυτόματη ταξινόμηση").assertDoesNotExist()
    }

    @Test fun missingSupportCallbacksDoNotShowDeadActions() {
        compose.setContent {
            MyFinHubTheme {
                MobileDeviceSettingsScreen(onBack = {}, onLogout = {})
            }
        }
        compose.onNodeWithText("Ιστορικό ειδοποιήσεων").assertDoesNotExist()
        compose.onNodeWithText("Διαγνωστικά και υποστήριξη").assertDoesNotExist()
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
            compose.onNodeWithText("Η απόκρυψη καλύπτει ποσά, όχι γραφήματα ή την κατεύθυνση των μεταβολών.")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Εμφάνιση ποσών")
                .performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(true, AmountVisibilityPreference.read(context))
            }
            compose.onNodeWithText("Η απόκρυψη καλύπτει ποσά, όχι γραφήματα ή την κατεύθυνση των μεταβολών.").assertIsDisplayed()
            compose.onNodeWithText("Εμφάνιση ποσών").performClick()
            compose.runOnIdle {
                assertEquals(false, AmountVisibilityPreference.read(context))
            }
        } finally {
            AmountVisibilityPreference.write(context, originallyVisible)
        }
    }

    @Test fun modernDeviceNicknameRequiresExplicitSaveAndDoesNotSurviveSessionLoss() {
        val id = "5b90719d-65c1-4abc-8299-3e07b192ac00"
        val row = DeviceSessionRecord(
            id, "android", "Work phone", null,
            "2026-10-01T09:00:00Z", "2026-10-16T09:00:00Z", true,
            nickname = "Work phone", defaultLabel = "Galaxy S24 Ultra", systemName = "Android",
        )
        val snapshot = mutableStateOf(DeviceSessionsUiSnapshot(loaded = true, devices = listOf(row)))
        val submissions = mutableListOf<Pair<String, String>>()
        compose.setContent {
            MyFinHubTheme {
                MobileDeviceSettingsScreen(
                    onBack = {}, onLogout = {},
                    deviceSessions = snapshot.value,
                    onRenameDeviceSession = { target, nickname -> submissions += target to nickname },
                )
            }
        }
        compose.onNodeWithTag("device_nickname_$id").performScrollTo().performClick()
        compose.onNodeWithTag("device_nickname_field").performTextReplacement(" Home  phone ")
        compose.runOnIdle { assertEquals(emptyList<Pair<String, String>>(), submissions) }
        compose.onNodeWithTag("device_nickname_confirm").performClick()
        compose.runOnIdle { assertEquals(listOf(id to "Home phone"), submissions) }

        compose.onNodeWithTag("device_nickname_$id").performScrollTo().performClick()
        compose.runOnIdle { snapshot.value = DeviceSessionsUiSnapshot() }
        compose.onNodeWithTag("device_nickname_confirm").assertDoesNotExist()
        compose.runOnIdle {
            snapshot.value = DeviceSessionsUiSnapshot(loaded = true, devices = listOf(row))
        }
        compose.onNodeWithTag("device_nickname_confirm").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, submissions.size) }
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
