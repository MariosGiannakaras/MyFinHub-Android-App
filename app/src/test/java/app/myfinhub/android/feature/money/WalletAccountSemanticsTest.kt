package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletAccountSemanticsTest {
    private fun account(
        id: String,
        kind: String,
        bankCategory: String? = null,
        cashRole: String? = null,
        cashType: String? = null,
        excluded: Boolean = false,
    ) = MoneyAccount(
        id = id, name = id, balance = 100.0,
        kind = if (kind == "bank") "Τράπεζα" else "Μετρητά",
        canonicalKind = kind,
        bankAccountCategory = bankCategory,
        cashRole = cashRole,
        cashType = cashType,
        excludeFromAvailable = excluded,
    )

    @Test
    fun bankSavingsNeverFallsIntoDailyMoneyEvenWithGenericBankDisplayLabel() {
        val salary = account("salary", "bank", bankCategory = "payroll")
        val saving = account("saving", "bank", bankCategory = "savings")
        val term = account("term", "bank", bankCategory = "term")
        assertEquals(WalletAccountGroup.DAILY, walletAccountGroup(salary))
        assertEquals(WalletAccountGroup.SAVINGS, walletAccountGroup(saving))
        assertEquals(WalletAccountGroup.OTHER, walletAccountGroup(term))
        val state = MoneyUiState(accounts = listOf(salary, saving, term))
        assertEquals(100.0, walletAvailableTotal(state), 0.001)
        assertEquals(listOf("saving"), walletAccountsInGroup(state, WalletAccountGroup.SAVINGS).map { it.id })
    }

    @Test
    fun cashReserveAndExcludedAccountsCannotInflateDailyBalance() {
        val daily = account("cash", "cash", cashRole = "daily")
        val reserve = account("reserve", "cash", cashRole = "reserve")
        val extra = account("other", "cash", cashType = "reserve")
        val excluded = account("hidden", "bank", excluded = true)
        val state = MoneyUiState(accounts = listOf(daily, reserve, extra, excluded))
        assertEquals(100.0, walletAvailableTotal(state), 0.001)
        assertEquals(listOf("reserve", "other", "hidden"),
            walletAccountsInGroup(state, WalletAccountGroup.OTHER).map { it.id })
    }

    @Test
    fun currentAndPaymentAccountsRemainUsableDailySources() {
        val state = MoneyUiState(accounts = listOf(
            account("current", "bank", bankCategory = "current"),
            account("payroll", "bank", bankCategory = "payroll"),
            account("wallet", "bank", bankCategory = "payment"),
        ))
        assertEquals(300.0, walletAvailableTotal(state), 0.001)
    }
}
