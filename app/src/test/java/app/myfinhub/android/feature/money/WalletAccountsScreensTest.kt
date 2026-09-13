package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletAccountsScreensTest {
    @Test
    fun accountGrouping_keepsSavingsSeparateAndCashDaily() {
        val state = MoneyUiState(
            accounts = listOf(
                MoneyAccount("cash", "Μετρητά", 100.0, "Μετρητά", canonicalKind = "cash"),
                MoneyAccount("bank", "Μισθοδοσία", 900.0, "Τράπεζα", "Τράπεζα Πειραιώς", canonicalKind = "bank"),
                MoneyAccount("reserve", "Αποθεματικό", 500.0, "Τράπεζα", canonicalKind = "bank", excludeFromAvailable = true),
                MoneyAccount("save", "Αποταμίευση", 2000.0, "Αποταμίευση", canonicalKind = "savings", excludeFromAvailable = true),
                MoneyAccount("other", "Broker", 300.0, "Επένδυση", canonicalKind = "investment"),
            ),
        )

        assertEquals(listOf("cash", "bank"), walletAccountsInGroup(state, WalletAccountGroup.DAILY).map { it.id })
        assertEquals(listOf("save"), walletAccountsInGroup(state, WalletAccountGroup.SAVINGS).map { it.id })
        assertEquals(listOf("reserve", "other"), walletAccountsInGroup(state, WalletAccountGroup.OTHER).map { it.id })
        assertEquals(1000.0, walletAvailableTotal(state), 0.001)
    }

    @Test
    fun netPosition_usesAggregateCreditDebtEvenWithoutVisibleCards() {
        val state = MoneyUiState(
            accounts = listOf(MoneyAccount("bank", "Κύριος", 1000.0, "Τράπεζα")),
            cards = emptyList(),
            loanOutstanding = 100.0,
            lendingReceivable = 50.0,
            aggregateCreditOutstanding = 300.0,
        )

        assertEquals(650.0, canonicalNetPosition(state), 0.001)
        assertEquals(300.0, canonicalCreditOutstanding(state), 0.001)
    }
}
