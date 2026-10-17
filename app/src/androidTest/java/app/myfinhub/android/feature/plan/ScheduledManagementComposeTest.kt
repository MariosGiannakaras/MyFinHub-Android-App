package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
    private val data=PlanUiState(scheduledAccounts=listOf(
        ScheduledAccountOption("bank","Μισθοδοσία"),
        ScheduledAccountOption("save","Αποταμίευση"),
    ),scheduledManageItems=listOf(
        ScheduledManageItem("bill","Internet","expense","2026-10-12",35.0,"due",accountId="bank"),
        ScheduledManageItem("done","Rent","expense","2026-10-10",200.0,"completed",accountId="bank"),
    ))

    @Test fun completeRequiresConfirmationAndKeepsExplicitEnteredAmountDate() {
        val calls=mutableListOf<Triple<String,String,Double>>()
        compose.setContent { MyFinHubTheme {
            CanonicalScheduledManagementScreen(data,onBack={},
                onComplete={request->calls+=Triple(request.id,request.date,request.amount)},onClose={_,_->})
        } }
        compose.onNodeWithTag("scheduled_management_root").assertIsDisplayed()
        compose.onNodeWithTag("scheduled_complete_bill").performClick()
        compose.runOnIdle { assertEquals(0,calls.size) }
        compose.onNodeWithTag("scheduled_confirm_date").performTextReplacement("2026-10-17")
        compose.onNodeWithTag("scheduled_confirm_amount").performTextReplacement("32.50")
        compose.onNodeWithTag("scheduled_confirm_action").performClick()
        compose.runOnIdle { assertEquals(listOf(Triple("bill","2026-10-17",32.5)),calls) }
    }

    @Test fun confirmedPaymentCanChooseDifferentAccountFromOldScheduledSource() {
        val calls=mutableListOf<ScheduledCompletionRequest>()
        compose.setContent { MyFinHubTheme {
            CanonicalScheduledManagementScreen(data,onBack={},
                onComplete={calls+=it},onClose={_,_->})
        } }
        compose.onNodeWithTag("scheduled_complete_bill").performClick()
        compose.onNodeWithTag("scheduled_confirm_source").performClick()
        compose.onNodeWithText("Αποταμίευση").performClick()
        compose.onNodeWithTag("scheduled_confirm_date").performTextReplacement("2026-10-17")
        compose.onNodeWithTag("scheduled_confirm_amount").performTextReplacement("32.5")
        compose.onNodeWithTag("scheduled_confirm_action").performClick()
        compose.runOnIdle {
            assertEquals(1,calls.size)
            assertEquals("bill",calls.single().id)
            assertEquals("save",calls.single().accountId)
            assertEquals(32.5,calls.single().amount,0.001)
        }
    }

    @Test fun skipIsExplicitAndBlockedStateDisablesMutationActions() {
        val calls=mutableListOf<Pair<String,String>>()
        compose.setContent { MyFinHubTheme {
            CanonicalScheduledManagementScreen(data,onBack={},
                onComplete={_->},onClose={id,status->calls+=id to status})
        } }
        compose.onNodeWithTag("scheduled_skip_bill").performClick()
        compose.runOnIdle { assertEquals(0,calls.size) }
        compose.onNodeWithTag("scheduled_confirm_action").performClick()
        compose.runOnIdle { assertEquals(listOf("bill" to "skipped"),calls) }
    }

    @Test fun creatingScheduledExpenseRequiresExplicitDetailsAndConfirmation() {
        val requests=mutableListOf<ScheduledCreateRequest>()
        val state=data.copy(scheduledAccounts=listOf(
            ScheduledAccountOption("bank","Μισθοδοσία"),
            ScheduledAccountOption("save","Αποταμίευση"),
        ))
        compose.setContent { MyFinHubTheme {
            CanonicalScheduledManagementScreen(state,onBack={},
                onCreate={requests+=it},onComplete={_->},onClose={_,_->})
        } }
        compose.onNodeWithTag("scheduled_create_open").performClick()
        compose.runOnIdle {assertEquals(0,requests.size)}
        compose.onNodeWithTag("scheduled_new_date").performTextReplacement("2026-11-15")
        compose.onNodeWithTag("scheduled_new_amount").performTextReplacement("40.5")
        compose.onNodeWithTag("scheduled_new_note").performTextReplacement("Κινητό")
        compose.onNodeWithTag("scheduled_new_confirm").performClick()
        compose.runOnIdle {
            assertEquals(1,requests.size)
            assertEquals("expense",requests.single().kind)
            assertEquals("bank",requests.single().accountId)
            assertEquals(40.5,requests.single().amount,0.001)
            assertEquals("2026-11-15",requests.single().dueDate)
        }
    }

    @Test fun scheduledTransferRequiresTwoDifferentAccountsAndNeverPostsImmediately() {
        val calls=mutableListOf<ScheduledCreateRequest>()
        val state=data.copy(scheduledAccounts=listOf(
            ScheduledAccountOption("bank","Μισθοδοσία"),
            ScheduledAccountOption("save","Αποταμίευση"),
        ))
        compose.setContent { MyFinHubTheme {
            CanonicalScheduledManagementScreen(state,onBack={},
                onCreate={calls+=it},onComplete={_->},onClose={_,_->})
        } }
        compose.onNodeWithTag("scheduled_create_open").performClick()
        compose.onNodeWithTag("scheduled_new_kind_transfer").performClick()
        compose.onNodeWithTag("scheduled_new_amount").performTextReplacement("50")
        compose.onNodeWithTag("scheduled_new_date").performTextReplacement("2026-11-03")
        compose.onNodeWithTag("scheduled_new_confirm").performClick()
        compose.runOnIdle {
            assertEquals(1,calls.size)
            val request=calls.single()
            assertEquals("transfer",request.kind)
            assertEquals("bank",request.fromAccountId)
            assertEquals("save",request.toAccountId)
            assertEquals(null,request.accountId)
        }
    }

    @Test fun blockedMutationCannotPostOrCloseScheduledRecord() {
        compose.setContent { MyFinHubTheme {
            CanonicalScheduledManagementScreen(data,onBack={},
                onComplete={_->error("Blocked")},
                onClose={_,_->error("Blocked")},mutationBlocked=true)
        } }
        compose.onNodeWithTag("scheduled_complete_bill").assertIsNotEnabled()
        compose.onNodeWithTag("scheduled_skip_bill").assertIsNotEnabled()
        compose.onNodeWithTag("scheduled_cancel_bill").assertIsNotEnabled()
    }
}
