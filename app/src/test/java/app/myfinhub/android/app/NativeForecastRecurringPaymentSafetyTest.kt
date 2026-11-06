package app.myfinhub.android.app

import app.myfinhub.android.feature.plan.PlanUiState
import app.myfinhub.android.feature.plan.PlannedFlow
import app.myfinhub.android.feature.plan.PlannedItem
import app.myfinhub.android.feature.plan.PlannedKind
import app.myfinhub.android.feature.plan.RecurringManageItem
import app.myfinhub.android.feature.quickentry.QuickEntryAccountOption
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import app.myfinhub.android.feature.quickentry.reduceQuickEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Forecast estimates are never an authoritative recurring payment identity. */
class NativeForecastRecurringPaymentSafetyTest {
    private val bank = QuickEntryAccountOption("bank", "Τράπεζα", "bank")
    private val quick = QuickEntryUiState(
        accounts = listOf(bank), defaultExpenseAccountId = "bank",
        accountId = "bank", amountText = "999",
    )
    private fun bill(mode: String = "fixed") = RecurringManageItem(
        id = "electric", title = "Ρεύμα", amount = 80.50, status = "active",
        accountId = "bank", category = "Σπίτι", amountMode = mode,
        estimateMin = if (mode == "variable") 50.0 else null,
        estimateMax = if (mode == "variable") 100.0 else null,
    )
    private fun forecastItem(id: String = "electric") = PlannedItem(
        id = id, title = "Electric forecast", dueLabel = "25 Νοε", amount = 250.0,
        kind = PlannedKind.RECURRING, flow = PlannedFlow.OBLIGATION,
        dueDateIso = "2026-11-25",
    )
    private fun plan(item: PlannedItem = forecastItem(),
                     recurring: List<RecurringManageItem> = listOf(bill())) =
        PlanUiState(items = listOf(item), recurringManageItems = recurring)

    @Test fun sourceWinsOverForecastEstimateAndPreservesUnsavedRecurringExpense() {
        val actions = forecastRecurringPaymentQuickEntryPrefillActions(forecastItem(), plan(), quick)
        assertFalse(actions.any { it == QuickEntryAction.Save })
        val draft = actions.fold(quick, ::reduceQuickEntry)
        assertEquals(QuickEntryKind.EXPENSE, draft.kind)
        assertEquals("electric", draft.recurringId)
        assertEquals("bank", draft.accountId)
        assertEquals("Σπίτι", draft.category)
        assertEquals("Ρεύμα", draft.note)
        assertEquals(80.50, draft.amount ?: -1.0, 0.001)
        assertFalse(draft.persisted)
    }

    @Test fun variableEstimateNeverFillsActualPaymentAmount() {
        val actions = forecastRecurringPaymentQuickEntryPrefillActions(
            forecastItem(), plan(recurring=listOf(bill("variable"))), quick)
        val draft = actions.fold(quick, ::reduceQuickEntry)
        assertEquals("", draft.amountText)
        assertEquals("electric", draft.recurringId)
        assertEquals("bank", draft.accountId)
    }

    @Test fun staleOrAmbiguousIdentityPayerAndNonexpenseFlowAllFailClosed() {
        assertTrue(forecastRecurringPaymentQuickEntryPrefillActions(
            forecastItem(), plan(recurring=emptyList()), quick).isEmpty())
        assertTrue(forecastRecurringPaymentQuickEntryPrefillActions(
            forecastItem(), plan(recurring=listOf(bill(), bill())), quick).isEmpty())
        assertTrue(forecastRecurringPaymentQuickEntryPrefillActions(
            forecastItem(), plan(recurring=listOf(bill().copy(status="paused"))), quick).isEmpty())
        assertTrue(forecastRecurringPaymentQuickEntryPrefillActions(
            forecastItem(), plan(recurring=listOf(bill().copy(accountId="missing"))), quick).isEmpty())
        assertTrue(forecastRecurringPaymentQuickEntryPrefillActions(
            forecastItem(), plan().copy(items=listOf(forecastItem(),forecastItem())), quick).isEmpty())
        assertTrue(forecastRecurringPaymentQuickEntryPrefillActions(
            forecastItem(" electric "), plan(forecastItem(" electric ")), quick).isEmpty())
        assertTrue(forecastRecurringPaymentQuickEntryPrefillActions(
            forecastItem().copy(kind=PlannedKind.SCHEDULED),plan(),quick).isEmpty())
        assertTrue(forecastRecurringPaymentQuickEntryPrefillActions(
            forecastItem().copy(flow=PlannedFlow.INCOME),plan(),quick).isEmpty())
    }
}
