package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** User must confirm before any scheduled balance-changing canonical mutation. */
@RunWith(AndroidJUnit4::class)
class ScheduledManagementComposeTest {
    @get:Rule val compose=createComposeRule()
    private val data=PlanUiState(scheduledManageItems=listOf(
        ScheduledManageItem("bill","Internet","expense","2026-10-12",35.0,"due",accountId="bank"),
        ScheduledManageItem("done","Rent","expense","2026-10-10",200.0,"completed",accountId="bank"),
    ))

    @Test fun completeRequiresConfirmationAndKeepsExplicitEnteredAmountDate() {
        val calls=mutableListOf<Triple<String,String,Double>>()
        compose.setContent { MyFinHubTheme {
            CanonicalScheduledManagementScreen(data,onBack={},
                onComplete={id,date,amount->calls+=Triple(id,date,amount)},onClose={_,_->})
        } }
        compose.onNodeWithTag("scheduled_management_root").assertIsDisplayed()
        compose.onNodeWithTag("scheduled_complete_bill").performClick()
        compose.runOnIdle { assertEquals(0,calls.size) }
        compose.onNodeWithTag("scheduled_confirm_date").performTextReplacement("2026-10-17")
        compose.onNodeWithTag("scheduled_confirm_amount").performTextReplacement("32.50")
        compose.onNodeWithTag("scheduled_confirm_action").performClick()
        compose.runOnIdle { assertEquals(listOf(Triple("bill","2026-10-17",32.5)),calls) }
    }

    @Test fun skipIsExplicitAndBlockedStateDisablesMutationActions() {
        val calls=mutableListOf<Pair<String,String>>()
        compose.setContent { MyFinHubTheme {
            CanonicalScheduledManagementScreen(data,onBack={},
                onComplete={_,_,_->},onClose={id,status->calls+=id to status})
        } }
        compose.onNodeWithTag("scheduled_skip_bill").performClick()
        compose.runOnIdle { assertEquals(0,calls.size) }
        compose.onNodeWithTag("scheduled_confirm_action").performClick()
        compose.runOnIdle { assertEquals(listOf("bill" to "skipped"),calls) }
    }

    @Test fun blockedMutationCannotPostOrCloseScheduledRecord() {
        compose.setContent { MyFinHubTheme {
            CanonicalScheduledManagementScreen(data,onBack={},
                onComplete={_,_,_->error("Blocked")},
                onClose={_,_->error("Blocked")},mutationBlocked=true)
        } }
        compose.onNodeWithTag("scheduled_complete_bill").assertIsNotEnabled()
        compose.onNodeWithTag("scheduled_skip_bill").assertIsNotEnabled()
        compose.onNodeWithTag("scheduled_cancel_bill").assertIsNotEnabled()
    }
}
