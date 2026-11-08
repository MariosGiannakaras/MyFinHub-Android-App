package app.myfinhub.android.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.home.syntheticHomeUiState
import app.myfinhub.android.feature.plan.PlanUiState
import app.myfinhub.android.feature.plan.PlannedItem
import app.myfinhub.android.feature.plan.PlannedFlow
import app.myfinhub.android.feature.plan.PlannedKind
import app.myfinhub.android.feature.plan.RecurringCreateRequest
import app.myfinhub.android.feature.plan.RecurringManageItem
import app.myfinhub.android.feature.plan.ScheduledAccountOption
import app.myfinhub.android.feature.quickentry.QuickEntryAccountOption
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import app.myfinhub.android.feature.quickentry.reduceQuickEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Production Android Plan routing, source-bound actions, no real finance write. */
class NativeRecurringManagementJourneyComposeTest {
    @get:Rule val rule = createComposeRule()
    private val cash = QuickEntryAccountOption("cash", "Μετρητά", "cash")
    private val bill = RecurringManageItem(
        id = "bill", title = "Ρεύμα", amount = 45.5, status = "active",
        accountId = "cash", category = "Σπίτι", nextDueDateIso = "2026-11-30",
    )
    private fun plan(item: RecurringManageItem = bill) = PlanUiState(
        recurringManageItems = listOf(item),
        scheduledAccounts = listOf(ScheduledAccountOption("cash", "Μετρητά")),
    )
    private fun enterManagement() {
        rule.onNodeWithContentDescription("Πλάνο", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("s7_plan_root")
            .performScrollToNode(hasTestTag("s7_recurring_management_link"))
        rule.onNodeWithTag("s7_recurring_management_link").performClick()
        rule.onNodeWithTag("mobile_recurring_overview").assertExists()
        rule.onNodeWithTag("mobile_recurring_manage_open").performScrollTo().performClick()
        rule.onNodeWithTag("recurring_management_root").assertExists()
    }

    @Test fun livePlanEnablesExistingNativeRecurringEditLifecycleAndSafeReturnToHistory() {
        val statuses = mutableListOf<Pair<String,String>>()
        val edits = mutableListOf<Pair<String,RecurringCreateRequest>>()
        rule.setContent { MyFinHubTheme {
            MyFinHubAppContent(
                homeState = syntheticHomeUiState(), onHomeAction = {},
                planState = plan(),
                quickEntryState = QuickEntryUiState(accounts = listOf(cash)),
                onSetRecurringStatus = { id,status -> statuses += id to status },
                onEditRecurring = { id,value -> edits += id to value },
            )
        } }
        enterManagement()
        rule.onNodeWithTag("recurring_edit_bill").performScrollTo().performClick()
        rule.onNodeWithTag("recurring_create_name").assertExists()
        rule.onNodeWithText("Ακύρωση").performClick()
        rule.onNodeWithTag("recurring_toggle_bill").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(listOf("bill" to "paused"), statuses)
            assertEquals(0, edits.size)
        }
        rule.onNodeWithContentDescription("Πίσω").performClick()
        rule.onNodeWithTag("mobile_recurring_overview").assertExists()
        rule.onNodeWithTag("mobile_recurring_card_0").assertExists()
    }

    @Test fun liveRecurringPaymentOpensEditableLinkedDraftWithoutPostingMoney() {
        var quick by mutableStateOf(QuickEntryUiState(
            accounts = listOf(cash), accountId = "cash",
            defaultExpenseAccountId = "cash",
        ))
        val actions = mutableListOf<QuickEntryAction>()
        rule.setContent { MyFinHubTheme {
            MyFinHubAppContent(
                homeState = syntheticHomeUiState(), onHomeAction = {},
                planState = plan(), quickEntryState = quick,
                onQuickEntryAction = { action ->
                    actions += action
                    quick = reduceQuickEntry(quick,action)
                },
            )
        } }
        enterManagement()
        rule.onNodeWithTag("recurring_record_payment_bill").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(QuickEntryKind.EXPENSE, quick.kind)
            assertEquals("bill", quick.recurringId)
            assertEquals("cash", quick.accountId)
            assertEquals("Ρεύμα", quick.note)
            assertEquals(45.5, quick.amount ?: 0.0, 0.001)
            assertFalse(actions.any { it == QuickEntryAction.Save })
            assertFalse(quick.persisted)
        }
        // The actual Quick Entry screen opens; no financial event was saved.
        rule.onNodeWithContentDescription("Πίσω").assertExists()
    }

    @Test fun missingOrAmbiguousPayerDisablesLiveFinancialAction() {
        rule.setContent { MyFinHubTheme {
            MyFinHubAppContent(
                homeState = syntheticHomeUiState(), onHomeAction = {},
                planState = plan(bill.copy(accountId = "deleted-account")),
                quickEntryState = QuickEntryUiState(accounts = listOf(cash)),
                onQuickEntryAction = { error("A missing recurring payer must never prefill Quick Entry") },
            )
        } }
        enterManagement()
        rule.onNodeWithTag("recurring_invalid_payment_account_bill").performScrollTo().assertExists()
        rule.onNodeWithTag("recurring_record_payment_bill").assertIsNotEnabled()
    }
    @Test fun productionRouteCreatesRecurringOnlyAfterExplicitFormSave() {
        val created = mutableListOf<RecurringCreateRequest>()
        rule.setContent { MyFinHubTheme {
            MyFinHubAppContent(
                homeState = syntheticHomeUiState(), onHomeAction = {},
                planState = plan(), quickEntryState = QuickEntryUiState(accounts = listOf(cash)),
                onCreateRecurring = { created += it },
            )
        } }
        enterManagement()
        rule.onNodeWithTag("recurring_create_open").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(0, created.size) }
        rule.onNodeWithTag("recurring_create_name").performScrollTo()
            .performTextInput("Ασφάλεια κατοικίας")
        rule.onNodeWithTag("recurring_create_amount").performScrollTo()
            .performTextInput("85")
        rule.onNodeWithTag("recurring_create_save").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0, created.size) }
        rule.onNodeWithTag("recurring_create_account").performScrollTo().performClick()
        rule.onNodeWithText("Μετρητά").performClick()
        rule.onNodeWithTag("recurring_create_save").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(1, created.size)
            assertEquals("Ασφάλεια κατοικίας", created.single().name)
            assertEquals(85.0, created.single().amount, 0.001)
            assertEquals("cash", created.single().accountId)
        }
    }


    @Test fun forecastRecurringVariablePaymentUsesExactCurrentSourceWithoutBookingEstimate() {
        val projected = PlannedItem(
            id = "bill", title = "Ρεύμα", dueLabel = "30 Νοε",
            amount = 200.0, kind = PlannedKind.RECURRING,
            flow = PlannedFlow.OBLIGATION, dueDateIso = "2026-11-30",
        )
        val variable = bill.copy(
            amount = 125.0, amountMode = "variable",
            estimateMin = 80.0, estimateMax = 150.0,
        )
        val today = "2026-11-06"
        val ui = plan(variable).copy(
            items = listOf(projected),
            forecastStartDateIso = today,
            forecastEndDateIso = "2026-12-06",
            forecastHorizonDays = 30,
        )
        var quick by mutableStateOf(QuickEntryUiState(
            accounts = listOf(cash), accountId = "cash",
            defaultExpenseAccountId = "cash", amountText = "999",
        ))
        val changes = mutableListOf<QuickEntryAction>()
        rule.setContent { MyFinHubTheme {
            MyFinHubAppContent(
                homeState = syntheticHomeUiState(), onHomeAction = {},
                planState = ui, quickEntryState = quick,
                onQuickEntryAction = { action ->
                    changes += action
                    quick = reduceQuickEntry(quick, action)
                },
            )
        } }
        rule.onNodeWithContentDescription("Πλάνο", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("s7_plan_root")
            .performScrollToNode(hasTestTag("s7_forecast_link"))
        rule.onNodeWithTag("s7_forecast_link").performClick()
        rule.onNodeWithTag("s7_forecast_expand").performClick()
        rule.onNodeWithTag("s7_forecast_record_RECURRING:bill").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(QuickEntryKind.EXPENSE, quick.kind)
            assertEquals("bill", quick.recurringId)
            assertEquals("cash", quick.accountId)
            assertEquals("", quick.amountText)
            assertEquals("Ρεύμα", quick.note)
            assertFalse(changes.contains(QuickEntryAction.Save))
            assertFalse(quick.persisted)
        }
    }

    @Test fun forecastPaymentDisabledWhenHistoricalRecurringPayerIsMissing() {
        val projected = PlannedItem(
            id = "bill", title = "Ρεύμα", dueLabel = "30 Νοε",
            amount = 45.5, kind = PlannedKind.RECURRING,
            flow = PlannedFlow.OBLIGATION, dueDateIso = "2026-11-30",
        )
        val ui = plan(bill.copy(accountId = "deleted")).copy(
            items = listOf(projected),
            forecastStartDateIso = "2026-11-06",
            forecastEndDateIso = "2026-12-06",
            forecastHorizonDays = 30,
        )
        rule.setContent { MyFinHubTheme {
            MyFinHubAppContent(
                homeState = syntheticHomeUiState(), onHomeAction = {},
                planState = ui,
                quickEntryState = QuickEntryUiState(accounts = listOf(cash)),
                onQuickEntryAction = { error("Orphaned recurring Forecast payer must never post") },
            )
        } }
        rule.onNodeWithContentDescription("Πλάνο", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("s7_plan_root")
            .performScrollToNode(hasTestTag("s7_forecast_link"))
        rule.onNodeWithTag("s7_forecast_link").performClick()
        rule.onNodeWithTag("s7_forecast_expand").performClick()
        rule.onNodeWithTag("s7_forecast_record_RECURRING:bill").performScrollTo()
            .assertIsNotEnabled()
    }


}
