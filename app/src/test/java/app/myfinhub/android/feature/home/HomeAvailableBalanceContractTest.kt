package app.myfinhub.android.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Owner-locked daily balance contract: only the primary Cash and Payroll roles
 * contribute. Savings, other LIQUID banks and cash reserves must never leak in.
 */
class HomeAvailableBalanceContractTest {
    @Test
    fun dailyHeadlineUsesOnlyCashAndPayrollEvenIfOtherBanksAndSavingsHoldMore() {
        val state = syntheticHomeUiState().copy(accounts = listOf(
            account("cash", -90.0, HomePrimaryRole.CASH, HomeAccountGroup.LIQUID),
            account("salary", 44.50, HomePrimaryRole.PAYROLL, HomeAccountGroup.LIQUID),
            account("savings", 9_000.0, HomePrimaryRole.SAVINGS, HomeAccountGroup.SAVINGS),
            account("other-bank", 15_000.0, null, HomeAccountGroup.LIQUID),
            account("reserve", 600.0, null, HomeAccountGroup.SAVINGS),
        ))
        assertEquals(-45.50, state.liquidTotal, 0.001)
        assertEquals(-45.50, reduceHomeState(state, HomeAction.ToggleAmounts).liquidTotal, 0.001)
    }

    @Test
    fun onlySavingsAndSecondaryAccountsNeverInventAvailableDailyFunds() {
        val state = syntheticHomeUiState().copy(accounts = listOf(
            account("savings", 5_000.0, HomePrimaryRole.SAVINGS, HomeAccountGroup.SAVINGS),
            account("other", 2_000.0, null, HomeAccountGroup.LIQUID),
        ))
        assertEquals(0.0, state.liquidTotal, 0.001)
    }

    private fun account(
        id: String,
        balance: Double,
        primaryRole: HomePrimaryRole?,
        group: HomeAccountGroup,
    ) = HomeAccount(
        id = id,
        name = id,
        role = id,
        balance = balance,
        group = group,
        isPrimary = true,
        primaryRole = primaryRole,
    )
}
