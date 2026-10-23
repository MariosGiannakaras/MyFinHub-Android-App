package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
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

    @Test fun createRecurringRequiresValidAmountAndSeparatesEstimatesFromEvents() {
        val calls = mutableListOf<RecurringCreateRequest>()
        rule.setContent { MyFinHubTheme {
            CanonicalRecurringManagementScreen(
                state.copy(scheduledAccounts=listOf(ScheduledAccountOption("cash","Μετρητά"))),
                onBack={},onStatusChanged={_,_->},onCreate={ calls += it },
            )
        } }
        rule.onNodeWithTag("recurring_create_open").performClick()
        rule.onNodeWithTag("recurring_create_save").assertIsNotEnabled()
        rule.onNodeWithTag("recurring_create_name").performScrollTo().performTextInput("Ρεύμα")
        rule.onNodeWithTag("recurring_create_amount").performScrollTo().performTextInput("80")
        rule.onNodeWithTag("recurring_create_mode").performScrollTo().performClick()
        rule.onNodeWithTag("recurring_create_min").performScrollTo().performTextInput("60")
        rule.onNodeWithTag("recurring_create_max").performScrollTo().performTextInput("100")
        rule.onNodeWithTag("recurring_create_save").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(1,calls.size)
            assertEquals("variable",calls.single().amountMode)
            assertEquals(60.0,calls.single().estimateMin!!,0.001)
            assertEquals(100.0,calls.single().estimateMax!!,0.001)
            assertEquals("cash",calls.single().accountId)
        }
    }

    @Test fun existingRecurringEditorEmitsIdentityScopedUpdateAndPreservesCadence() {
        val edits=mutableListOf<Pair<String,RecurringCreateRequest>>()
        val existing=state.copy(recurringManageItems=listOf(
            RecurringManageItem("energy","Ρεύμα",80.0,"paused",
                category="Πάγια",accountId="cash",amountMode="variable",
                estimateMin=60.0,estimateMax=100.0,
                recurrenceUnit="year",recurrenceInterval=2,
                firstExpectedDateIso="2026-11-23"),
        ),scheduledAccounts=listOf(ScheduledAccountOption("cash","Μετρητά")))
        rule.setContent { MyFinHubTheme {
            CanonicalRecurringManagementScreen(existing,onBack={},
                onStatusChanged={_,_->},onEdit={ id,request -> edits+=id to request })
        } }
        rule.onNodeWithTag("recurring_edit_energy").performScrollTo().performClick()
        rule.onNodeWithTag("recurring_create_name").performScrollTo()
            .performTextReplacement("Ρεύμα και θέρμανση")
        rule.onNodeWithTag("recurring_create_save").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(1,edits.size)
            assertEquals("energy",edits.single().first)
            assertEquals("Ρεύμα και θέρμανση",edits.single().second.name)
            assertEquals("variable",edits.single().second.amountMode)
            assertEquals("year",edits.single().second.recurrenceUnit)
            assertEquals(2,edits.single().second.recurrenceInterval)
            assertEquals(80.0,edits.single().second.amount,0.001)
        }
    }

    @Test fun nativeButtonsEmitCorrectCanonicalLifecycleIntent() {
        val calls=mutableListOf<Pair<String,String>>()
        rule.setContent { MyFinHubTheme {
            CanonicalRecurringManagementScreen(state, onBack={},
                onStatusChanged={id,status -> calls += id to status})
        } }
        rule.onNodeWithText("Παύση").performClick()
        rule.onAllNodesWithText("Διακοπή")[0].performClick()
        rule.onAllNodesWithText("Επανενεργοποίηση")[0].performClick()
        rule.runOnIdle {
            assertEquals("active" to "paused",calls[0])
            assertEquals("active" to "stopped",calls[1])
            assertEquals("paused" to "active",calls[2])
        }
    }

    @Test fun duplicateHistoricalIdsRenderWithoutKeyCollisionAndDisableOnlyAmbiguousActions() {
        val duplicates=state.copy(recurringManageItems=listOf(
            RecurringManageItem("same","Πάγιο Α",20.0,"active","2026-10-21"),
            RecurringManageItem("same","Πάγιο Β",30.0,"active","2026-10-22"),
        ))
        rule.setContent { MyFinHubTheme {
            CanonicalRecurringManagementScreen(duplicates,onBack={},
                onStatusChanged={_,_->error("Duplicate unsafe mutation")},
                onRecordPayment={ error("Duplicate unsafe payment") })
        } }
        rule.onAllNodesWithText("Ιστορικό διπλό ID",substring=true).fetchSemanticsNodes().let {
            assertEquals(2,it.size)
        }
        rule.onAllNodesWithText("Καταχώριση πληρωμής")[0].assertIsNotEnabled()
        rule.onAllNodesWithText("Καταχώριση πληρωμής")[1].assertIsNotEnabled()
    }

    @Test fun historicalPaymentCountLastDateAndServiceEndAreReadOnlyMetadata() {
        val item=RecurringManageItem(
            id="history",title="Ρεύμα",amount=65.0,status="active",
            nextDueDateIso="2026-11-08",paymentCount=3,
            lastPaymentDateIso="2026-10-08",typicalPaymentDay=8,
            endDateIso="2027-01-08",
        )
        rule.setContent { MyFinHubTheme {
            CanonicalRecurringManagementScreen(
                PlanUiState(recurringManageItems=listOf(item)),onBack={},
                onStatusChanged={_,_->error("read-only metadata mutation")},
            )
        } }
        rule.onAllNodesWithText("Καταγεγραμμένες πληρωμές: 3", substring=true)
            .fetchSemanticsNodes().let { assertEquals(1,it.size) }
        rule.onAllNodesWithText("Τελευταία: 2026-10-08", substring=true)
            .fetchSemanticsNodes().let { assertEquals(1,it.size) }
        rule.onAllNodesWithText("Λήξη/ανανέωση: 2027-01-08").fetchSemanticsNodes()
            .let { assertEquals(1,it.size) }
    }

    @Test fun onlyActiveRecurringEntriesCanOpenPaymentForm() {
        rule.setContent { MyFinHubTheme {
            CanonicalRecurringManagementScreen(
                state, onBack={},
                onStatusChanged={_,_->error("unexpected lifecycle mutation")},
                onRecordPayment={error("Do not trigger payment during eligibility assertions")},
            )
        } }
        rule.onAllNodesWithText("Καταχώριση πληρωμής")[0].assertIsEnabled()
        rule.onAllNodesWithText("Καταχώριση πληρωμής")[1].assertIsNotEnabled()
        rule.onAllNodesWithText("Καταχώριση πληρωμής")[2].assertIsNotEnabled()
    }

    @Test fun noEligibleNonCreditAccountDisablesPaymentAction() {
        rule.setContent { MyFinHubTheme {
            CanonicalRecurringManagementScreen(
                state.copy(recurringManageItems=state.recurringManageItems.take(1)),
                onBack={},onStatusChanged={_,_->error("unexpected")},
                paymentAccountAvailable=false,
                onRecordPayment={error("No eligible account")},
            )
        } }
        rule.onNodeWithText("Καταχώριση πληρωμής").assertIsNotEnabled()
    }

    @Test fun blockedMutationCannotEmitStatusChanges() {
        rule.setContent { MyFinHubTheme {
            CanonicalRecurringManagementScreen(state,onBack={},
                onStatusChanged={_,_->error("Blocked operation")},mutationBlocked=true)
        } }
        rule.onNodeWithText("Παύση").assertIsNotEnabled()
    }
}
