package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecurringManagementComposeTest {
    @get:Rule val rule=createComposeRule()
    private val state = PlanUiState(recurringManageItems=listOf(
        RecurringManageItem("active","Internet",35.0,"active","2026-10-30"),
        RecurringManageItem("paused","Ασφάλεια",50.0,"paused"),
        RecurringManageItem("stopped","Παλιό",15.0,"stopped"),
    ))

    @Test fun nativeButtonsEmitCorrectCanonicalLifecycleIntent() {
        val calls=mutableListOf<Pair<String,String>>()
        rule.setContent { MyFinHubTheme {
            CanonicalRecurringManagementScreen(state, onBack={},
                onStatusChanged={id,status -> calls += id to status})
        } }
        rule.onNodeWithText("Παύση").performClick()
        rule.onNodeWithText("Διακοπή")[0].performClick()
        rule.onNodeWithText("Επανενεργοποίηση")[0].performClick()
        rule.runOnIdle {
            assertEquals("active" to "paused",calls[0])
            assertEquals("active" to "stopped",calls[1])
            assertEquals("paused" to "active",calls[2])
        }
    }

    @Test fun blockedMutationCannotEmitStatusChanges() {
        rule.setContent { MyFinHubTheme {
            CanonicalRecurringManagementScreen(state,onBack={},
                onStatusChanged={_,_->error("Blocked operation")},mutationBlocked=true)
        } }
        rule.onNodeWithText("Παύση").assertIsNotEnabled()
    }
}
