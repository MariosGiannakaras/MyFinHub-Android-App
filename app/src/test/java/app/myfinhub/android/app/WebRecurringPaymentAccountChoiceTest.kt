package app.myfinhub.android.app

import app.myfinhub.android.feature.plan.RecurringManageItem
import app.myfinhub.android.feature.quickentry.QuickEntryAccountOption
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Web recurringAccountChoice preserves preferences without stale/credit fallthrough. */
class WebRecurringPaymentAccountChoiceTest {
    private fun service(account: String)=RecurringManageItem(
        id="internet",title="Internet",amount=25.0,status="active",
        accountId=account,
    )
    private val eligible=listOf(
        QuickEntryAccountOption("cash","Μετρητά","cash"),
        QuickEntryAccountOption("payroll","Μισθοδοσία","bank"),
        QuickEntryAccountOption("credit","Πιστωτική","credit"),
    )
    @Test fun preferredCanonicalAccountWinsEvenWhenDefaultDiffers() {
        assertEquals("payroll",recurringQuickEntryAccountChoice(
            service("payroll"),QuickEntryUiState(
                accounts=eligible,defaultExpenseAccountId="cash",
            ),
        ))
    }
    @Test fun missingPreferredFallsBackToCurrentDefaultWhenAvailable() {
        assertEquals("payroll",recurringQuickEntryAccountChoice(
            service("deleted"),QuickEntryUiState(
                accounts=eligible,defaultExpenseAccountId="payroll",
            ),
        ))
    }
    @Test fun staleDefaultFallsBackToFirstEligibleWebAccount() {
        assertEquals("cash",recurringQuickEntryAccountChoice(
            service("deleted"),QuickEntryUiState(
                accounts=eligible,defaultExpenseAccountId="removed",
            ),
        ))
    }
    @Test fun creditAccountCannotBeSelectedEvenIfSavedOrDefault() {
        assertEquals("cash",recurringQuickEntryAccountChoice(
            service("credit"),QuickEntryUiState(
                accounts=eligible,defaultExpenseAccountId="credit",
            ),
        ))
    }
    @Test fun creditOnlyOrEmptyAccountListsFailClosed() {
        assertNull(recurringQuickEntryAccountChoice(
            service("credit"),QuickEntryUiState(
                accounts=listOf(eligible.last()),defaultExpenseAccountId="credit",
            ),
        ))
        assertNull(recurringQuickEntryAccountChoice(
            service("none"),QuickEntryUiState(
                accounts=emptyList(),defaultExpenseAccountId="removed",
            ),
        ))
    }
}
