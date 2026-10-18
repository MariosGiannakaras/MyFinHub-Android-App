package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertIsNotEnabled
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

    @Test fun blockedMutationCannotEmitStatusChanges() {
        rule.setContent { MyFinHubTheme {
            CanonicalRecurringManagementScreen(state,onBack={},
                onStatusChanged={_,_->error("Blocked operation")},mutationBlocked=true)
        } }
        rule.onNodeWithText("Παύση").assertIsNotEnabled()
    }
}
