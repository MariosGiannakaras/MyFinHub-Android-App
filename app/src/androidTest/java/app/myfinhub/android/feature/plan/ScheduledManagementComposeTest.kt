package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.runtime.mutableStateOf
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

    @Test fun emptyScheduledHistoryAllowsFirstNewSchedule() {
        val calls = mutableListOf<ScheduledCreateRequest>()
        compose.setContent { MyFinHubTheme {
            CanonicalScheduledManagementScreen(data.copy(scheduledManageItems=emptyList()),onBack={},
                onCreate={calls+=it},onComplete={_->},onClose={_,_->})
        } }
        compose.onNodeWithText("Δεν υπάρχουν προγραμματισμένες κινήσεις.").assertIsDisplayed()
        compose.onNodeWithTag("scheduled_create_open").performClick()
        compose.onNodeWithTag("scheduled_new_amount").performTextReplacement("20")
        compose.onNodeWithTag("scheduled_new_date").performTextReplacement("2026-11-10")
        compose.onNodeWithTag("scheduled_new_confirm").performClick()
        compose.runOnIdle {
            assertEquals(1,calls.size)
            assertEquals("expense",calls.single().kind)
        }
    }

    @Test fun unknownImportedKindCannotDispatchMoneyMutation() {
        val imported = data.copy(scheduledManageItems=listOf(
            ScheduledManageItem("odd","Άγνωστη","unsupported","bad-date",Double.NaN,"due"),
        ))
        compose.setContent { MyFinHubTheme {
            CanonicalScheduledManagementScreen(imported,onBack={},
                onComplete={_->error("Unsupported completion")},
                onClose={_,_->error("Unsupported close")})
        } }
        compose.onNodeWithTag("scheduled_complete_odd").assertIsNotEnabled()
        compose.onNodeWithTag("scheduled_skip_odd").assertIsNotEnabled()
        compose.onNodeWithText("Μη έγκυρο προγραμματισμένο ποσό").assertIsDisplayed()
        compose.onNodeWithText("Μη έγκυρη ημερομηνία προγραμματισμένης κίνησης")
            .assertIsDisplayed()
        compose.onNodeWithText("bad-date · Εκκρεμεί").assertIsDisplayed()
    }

    @Test fun malformedDuePaymentCannotCompleteOrCloseAndStillExplainsWhy() {
        val rows = data.copy(scheduledManageItems = listOf(
            ScheduledManageItem(
                id = "bad-amount", title = "Λανθασμένο ποσό", kind = "expense",
                dueDateIso = "2026-10-12", amount = Double.POSITIVE_INFINITY,
                status = "due", accountId = "bank",
            ),
            ScheduledManageItem(
                id = "bad-date", title = "Λανθασμένη ημερομηνία", kind = "expense",
                dueDateIso = "not-a-date", amount = 30.0,
                status = "due", accountId = "bank",
            ),
        ))
        compose.setContent { MyFinHubTheme {
            CanonicalScheduledManagementScreen(
                rows, onBack = {},
                onComplete = { error("Malformed completion must not dispatch") },
                onClose = { _, _ -> error("Malformed close must not dispatch") },
            )
        } }
        for (id in listOf("bad-amount", "bad-date")) {
            compose.onNodeWithTag("scheduled_complete_" + id).assertIsNotEnabled()
            compose.onNodeWithTag("scheduled_skip_" + id).assertIsNotEnabled()
            compose.onNodeWithTag("scheduled_cancel_" + id).assertIsNotEnabled()
        }
        compose.onNodeWithText("Μη έγκυρο προγραμματισμένο ποσό").assertIsDisplayed()
        compose.onNodeWithText("Μη έγκυρη ημερομηνία προγραμματισμένης κίνησης")
            .assertIsDisplayed()
    }

    @Test fun pendingConfirmationCannotPostStaleSourceAfterSync() {
        val source = mutableStateOf(data)
        compose.setContent { MyFinHubTheme {
            CanonicalScheduledManagementScreen(source.value,onBack={},
                onComplete={_->error("Stale completion")},onClose={_,_->})
        } }
        compose.onNodeWithTag("scheduled_complete_bill").performClick()
        compose.runOnIdle {
            source.value=source.value.copy(scheduledManageItems=source.value.scheduledManageItems.map {
                if (it.id=="bill") it.copy(status="completed") else it
            })
        }
        compose.onNodeWithTag("scheduled_confirm_action").assertIsNotEnabled()
    }

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
    @Test fun duplicateScheduledRowsStayVisibleButAllAmbiguousActionsDisabled() {
        val rows=data.copy(scheduledManageItems=listOf(
            ScheduledManageItem("duplicate","Παλαιό Α","expense","2026-10-10",30.0,"due",accountId="bank"),
            ScheduledManageItem("duplicate","Παλαιό Β","expense","2026-10-12",40.0,"upcoming",accountId="bank"),
            ScheduledManageItem("unique","Μοναδικό","expense","2026-10-11",50.0,"due",accountId="bank"),
        ))
        compose.setContent { MyFinHubTheme {
            CanonicalScheduledManagementScreen(rows,onBack={},
                onComplete={_->error("Ambiguous completion")},
                onClose={_,_->error("Ambiguous close")})
        } }
        compose.onNodeWithTag("scheduled_item_duplicate_0").assertIsDisplayed()
        compose.onNodeWithTag("scheduled_item_duplicate_1").assertIsDisplayed()
        compose.onNodeWithTag("scheduled_complete_duplicate_0").assertIsNotEnabled()
        compose.onNodeWithTag("scheduled_complete_duplicate_1").assertIsNotEnabled()
        compose.onNodeWithTag("scheduled_skip_duplicate_0").assertIsNotEnabled()
        compose.onNodeWithTag("scheduled_cancel_duplicate_1").assertIsNotEnabled()
        compose.onNodeWithTag("scheduled_complete_unique").assertIsDisplayed()
    }


}
