package app.myfinhub.android.feature.plan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebScheduledCompletionAccountSelectionTest {
    private val ids=setOf("salary","cash","savings")

    @Test fun scheduledIncomeOrExpenseCanChooseAnyStillAvailableAccount() {
        assertTrue(scheduledCompletionAccountIsValid("expense",ids,"cash",null,null))
        assertTrue(scheduledCompletionAccountIsValid("income",ids,"savings",null,null))
        assertFalse(scheduledCompletionAccountIsValid("expense",ids,"deleted",null,null))
        assertFalse(scheduledCompletionAccountIsValid("income",ids,null,null,null))
    }

    @Test fun scheduledTransferRequiresTwoDistinctAvailableAccounts() {
        assertTrue(scheduledCompletionAccountIsValid("transfer",ids,null,"salary","cash"))
        assertFalse(scheduledCompletionAccountIsValid("transfer",ids,null,"salary","salary"))
        assertFalse(scheduledCompletionAccountIsValid("transfer",ids,null,"deleted","savings"))
        assertFalse(scheduledCompletionAccountIsValid("transfer",ids,null,"cash",null))
    }

    @Test fun completionRequestCarriesOnlyTheActualSelectedMoneyRoute() {
        val request=ScheduledCompletionRequest(
            id="old-rent",date="2026-10-17",amount=39.5,accountId="cash")
        assertTrue(scheduledCompletionAccountIsValid("expense",ids,request.accountId,
            request.fromAccountId,request.toAccountId))
        assertTrue(request.fromAccountId==null && request.toAccountId==null)
    }
}
