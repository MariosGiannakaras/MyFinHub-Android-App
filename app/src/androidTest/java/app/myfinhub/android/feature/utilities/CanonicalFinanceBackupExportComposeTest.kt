package app.myfinhub.android.feature.utilities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test

class CanonicalFinanceBackupExportComposeTest {
    @get:Rule val compose = createComposeRule()
    private val snapshot = JsonObject(mapOf("revision" to JsonPrimitive("synthetic")))

    @Test fun exportBecomesDisabledWhenPendingOrBlockedAndRecoversWhenSettled() {
        var pending by mutableStateOf(0)
        var blocked by mutableStateOf(false)
        compose.setContent { MyFinHubTheme {
            CanonicalFinanceBackupExport(
                canonicalDocument = snapshot,
                pendingChangeCount = pending,
                mutationBlocked = blocked,
                offline = false,
            )
        } }
        compose.onNodeWithTag("s9_finance_backup_save").assertIsEnabled()
        compose.runOnIdle { pending = 2 }
        compose.onNodeWithTag("s9_finance_backup_save").assertIsNotEnabled()
        compose.onNodeWithText("Υπάρχουν 2 εκκρεμείς αλλαγές.", substring = true)
            .assertExists()
        compose.runOnIdle { pending = 0; blocked = true }
        compose.onNodeWithTag("s9_finance_backup_save").assertIsNotEnabled()
        compose.runOnIdle { blocked = false }
        compose.onNodeWithTag("s9_finance_backup_save").assertIsEnabled()
    }

    @Test fun offlineSnapshotShowsWarningButDoesNotInventAnOnlineRequirement() {
        compose.setContent { MyFinHubTheme {
            CanonicalFinanceBackupExport(
                canonicalDocument = snapshot,
                pendingChangeCount = 0,
                mutationBlocked = false,
                offline = true,
            )
        } }
        compose.onNodeWithText("Εκτός σύνδεσης:", substring = true)
            .assertExists()
        compose.onNodeWithTag("s9_finance_backup_save").assertIsEnabled()
    }
}
