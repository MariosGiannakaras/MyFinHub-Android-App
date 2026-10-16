package app.myfinhub.android.feature.utilities

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.core.network.DeviceSessionRecord
import app.myfinhub.android.core.update.LocalUpdateController
import app.myfinhub.android.core.update.UpdateController
import app.myfinhub.android.core.update.UpdateUiState
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WebDeviceSessionsSettingsComposeTest {
    @get:Rule val compose=createComposeRule()
    private val sample=DeviceSessionRecord(
        sessionId="5b90719d-65c1-4abc-8299-3e07b192ac00",
        platform="android", label="Galaxy S24 Ultra", appVersion="1.0.0",
        firstSeenAt="2026-10-01T09:00:00Z",lastSeenAt="2026-10-16T09:00:00Z",
        current=true,
    )
    @Test fun ownerReadOnlyDevicesAreVisibleWithoutRawSessionIdsOrRevokeButtons() {
        compose.setContent { MyFinHubTheme { CompositionLocalProvider(
            LocalUpdateController provides UpdateController(state=UpdateUiState.Idle),
        ) {
            ProductionSettingsScreen(FrontendUtilitiesUiState(), {}, {},
                deviceSessions=listOf(sample),deviceSessionsLoaded=true)
        } } }
        compose.onNodeWithTag("s9_device_sessions").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Galaxy S24 Ultra").assertIsDisplayed()
        compose.onNodeWithText("Αυτή η συνεδρία",substring=true).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText(sample.sessionId).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Ανάκληση",substring=true)
            .fetchSemanticsNodes().isEmpty())
    }
    @Test fun otherDeviceNeedsTwoStepsAndNeverAllowsCurrentSession() {
        var revoked: String? = null
        val other = sample.copy(
            sessionId = "5b90719d-65c1-4abc-8299-3e07b192ac11",
            label = "Office browser", current = false,
        )
        compose.setContent { MyFinHubTheme { CompositionLocalProvider(
            LocalUpdateController provides UpdateController(state = UpdateUiState.Idle),
        ) {
            ProductionSettingsScreen(FrontendUtilitiesUiState(), {}, {},
                deviceSessions = listOf(sample, other), deviceSessionsLoaded = true,
                onRevokeDeviceSession = { revoked = it })
        } } }
        compose.onNodeWithText("Αποσύνδεση συσκευής").performScrollTo().performClick()
        compose.onNodeWithText("Επιβεβαίωση αποσύνδεσης").assertIsDisplayed()
        compose.runOnIdle { assertTrue(revoked == null) }
        compose.onNodeWithText("Ακύρωση").performClick()
        compose.runOnIdle { assertTrue(revoked == null) }
        compose.onNodeWithText("Αποσύνδεση συσκευής").performScrollTo().performClick()
        compose.onNodeWithTag("s9_device_revoke_confirm").performClick()
        compose.runOnIdle { assertTrue(revoked == other.sessionId) }
        assertTrue(compose.onAllNodesWithText(sample.sessionId).fetchSemanticsNodes().isEmpty())
    }

    @Test fun unverifiedOrOfflineSessionDoesNotDisplayPriorDeviceMetadata() {
        compose.setContent { MyFinHubTheme { CompositionLocalProvider(
            LocalUpdateController provides UpdateController(state=UpdateUiState.Idle),
        ) {
            ProductionSettingsScreen(FrontendUtilitiesUiState(), {}, {},
                deviceSessions=emptyList(),deviceSessionsLoaded=false)
        } } }
        assertTrue(compose.onAllNodesWithText("Galaxy S24 Ultra").fetchSemanticsNodes().isEmpty())
    }
}
