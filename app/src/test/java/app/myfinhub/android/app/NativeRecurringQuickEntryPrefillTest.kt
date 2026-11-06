package app.myfinhub.android.app

import app.myfinhub.android.feature.plan.RecurringManageItem
import app.myfinhub.android.feature.plan.PlanUiState
import app.myfinhub.android.feature.plan.PlannedItem
import app.myfinhub.android.feature.plan.PlannedFlow
import app.myfinhub.android.feature.plan.PlannedKind
import app.myfinhub.android.feature.quickentry.QuickEntryAccountOption
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import app.myfinhub.android.feature.quickentry.reduceQuickEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRecurringQuickEntryPrefillTest {
    private val cash = QuickEntryAccountOption("cash", "Μετρητά", "cash")
    private val credit = QuickEntryAccountOption("credit", "Πιστωτική", "credit")
    private val source = QuickEntryUiState(
        accounts = listOf(cash, credit),
        accountId = "credit",
        amountText = "999.99",
        recurringId = "old-source",
        defaultExpenseAccountId = "cash",
    )
    private fun item() = RecurringManageItem(
        id = "electric", title = "Ρεύμα", amount = 52.34,
        status = "active", accountId = "cash", category = "Σπίτι",
    )

    @Test fun validFixedRecurringOpensUnpersistedExpenseWithExactLinkedIdentityAndPayer() {
        val actions = recurringPaymentQuickEntryPrefillActions(item(), source)
        assertTrue(actions.isNotEmpty())
        assertFalse(actions.any { it == QuickEntryAction.Save })
        val draft = actions.fold(source, ::reduceQuickEntry)
        assertEquals(QuickEntryKind.EXPENSE, draft.kind)
        assertEquals("electric", draft.recurringId)
        assertEquals("cash", draft.accountId)
        assertEquals("Σπίτι", draft.category)
        assertEquals("Ρεύμα", draft.note)
        assertEquals(52.34, draft.amount ?: -1.0, 0.001)
        assertFalse(draft.persisted)
        assertFalse(draft.pendingSync)
    }

    @Test fun variableEstimateNeverPrepopulatesAnActualPaymentAndClearsStaleDraft() {
        val variable = item().copy(amountMode = "variable", amount = 125.0,
            estimateMin = 80.0, estimateMax = 140.0)
        val actions = recurringPaymentQuickEntryPrefillActions(variable, source)
        assertFalse(actions.any { it is QuickEntryAction.AmountChanged })
        val draft = actions.fold(source, ::reduceQuickEntry)
        assertEquals("", draft.amountText)
        assertEquals("electric", draft.recurringId)
        assertEquals("cash", draft.accountId)
        assertEquals("Ρεύμα", draft.note)
    }

    @Test fun staleMissingAmbiguousOrCreditPayerAndInactiveRowsFailClosed() {
        assertTrue(recurringPaymentQuickEntryPrefillActions(
            item().copy(accountId = "missing"), source).isEmpty())
        assertTrue(recurringPaymentQuickEntryPrefillActions(
            item().copy(accountId = "credit"), source).isEmpty())
        assertTrue(recurringPaymentQuickEntryPrefillActions(
            item(), source.copy(accounts = listOf(cash, cash))).isEmpty())
        assertTrue(recurringPaymentQuickEntryPrefillActions(
            item().copy(status = "paused"), source).isEmpty())
        assertTrue(recurringPaymentQuickEntryPrefillActions(
            item().copy(status = "stopped"), source).isEmpty())
        assertTrue(recurringPaymentQuickEntryPrefillActions(
            item().copy(id = ""), source).isEmpty())
        assertTrue(recurringPaymentQuickEntryPrefillActions(
            item().copy(id = " electric "), source).isEmpty())
        assertTrue(recurringPaymentQuickEntryPrefillActions(
            item(), source.copy(accounts = listOf(cash, credit.copy(id = "cash")))).isEmpty())
    }

    @Test fun malformedFixedMoneyCannotBecomeSilentlyAcceptedActualAmount() {
        for (amount in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                -10.0, 0.0, 0.001)) {
            val actions = recurringPaymentQuickEntryPrefillActions(
                item().copy(amount = amount), source)
            assertFalse(actions.any { it is QuickEntryAction.AmountChanged })
            assertEquals("", actions.fold(source, ::reduceQuickEntry).amountText)
        }
        val invalid = recurringPaymentQuickEntryPrefillActions(
            item().copy(amountValidationError = "Imported invalid amount"), source)
        assertFalse(invalid.any { it is QuickEntryAction.AmountChanged })
    }
    private fun planned() = PlannedItem(
        id = "electric", title = "Ρεύμα", dueLabel = "30 Νοε", amount = 125.0,
        kind = PlannedKind.RECURRING, flow = PlannedFlow.OBLIGATION,
        dueDateIso = "2026-11-30",
    )
    @Test fun forecastMustResolveCurrentUniqueRecurringOwnerRatherThanItsEstimatedProjection() {
        val variable = item().copy(amountMode = "variable", amount = 125.0,
            estimateMin = 80.0, estimateMax = 150.0)
        val state = PlanUiState(
            items = listOf(planned()),
            recurringManageItems = listOf(variable),
        )
        val actions = forecastRecurringPaymentQuickEntryActions(planned(), state, source)
        val draft = actions.fold(source, ::reduceQuickEntry)
        assertTrue(actions.isNotEmpty())
        assertEquals("electric", draft.recurringId)
        assertEquals("cash", draft.accountId)
        assertEquals("", draft.amountText)
        assertFalse(actions.contains(QuickEntryAction.Save))
        assertFalse(draft.persisted)
        // Forecast row amount is not a payment authorization: current source wins.
        val mismatched = planned().copy(amount = 9999.0)
        assertEquals(actions,
            forecastRecurringPaymentQuickEntryActions(mismatched,state,source))
    }

    @Test fun forecastFinancialActionFailsClosedForMissingDuplicateOrInactiveSource() {
        val base = PlanUiState(
            items = listOf(planned()), recurringManageItems = listOf(item()),
        )
        assertTrue(forecastRecurringPaymentQuickEntryActions(
            planned(), base.copy(recurringManageItems = emptyList()), source).isEmpty())
        assertTrue(forecastRecurringPaymentQuickEntryActions(
            planned(), base.copy(recurringManageItems = listOf(item(),item())), source).isEmpty())
        assertTrue(forecastRecurringPaymentQuickEntryActions(
            planned(), base.copy(items = listOf(planned(), planned())), source).isEmpty())
        assertTrue(forecastRecurringPaymentQuickEntryActions(
            planned(), base.copy(recurringManageItems=listOf(item().copy(status="paused"))),source).isEmpty())
        assertTrue(forecastRecurringPaymentQuickEntryActions(
            planned(), base, source.copy(accounts=listOf(cash,cash))).isEmpty())
        assertTrue(forecastRecurringPaymentQuickEntryActions(
            planned(), base, source.copy(accounts=listOf(credit))).isEmpty())
        assertTrue(forecastRecurringPaymentQuickEntryActions(
            planned().copy(kind=PlannedKind.SCHEDULED),base,source).isEmpty())
    }


}
