package app.myfinhub.android.app

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
}
