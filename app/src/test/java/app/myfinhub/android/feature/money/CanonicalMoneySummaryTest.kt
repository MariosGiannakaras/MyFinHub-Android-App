package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalMoneySummaryTest {
    @Test
    fun netPositionIncludesCreditCardDebtWithoutDoubleCountingSavings() {
        val state = MoneyUiState(
            accounts = listOf(
                MoneyAccount("main", "Κύριος", 600.0, "Τράπεζα"),
                MoneyAccount("save", "Αποταμίευση", 400.0, "Αποταμίευση"),
            ),
            cards = listOf(
                MoneyCard(
                    id = "credit",
                    nickname = "Πιστωτική",
                    last4 = "1881",
                    kind = "Πιστωτική",
                    currentBalance = 300.0,
                    limit = 2_000.0,
                    vaultState = VaultState.LOCKED,
                    canonicalKind = "credit",
                ),
                MoneyCard(
                    id = "debit",
                    nickname = "Χρεωστική",
                    last4 = "4242",
                    kind = "Χρεωστική",
                    currentBalance = 999.0,
                    limit = null,
                    vaultState = VaultState.LOCKED,
                    canonicalKind = "debit",
                ),
            ),
            savingsCurrent = 400.0,
            loanOutstanding = 200.0,
            lendingReceivable = 100.0,
        )

        assertEquals(300.0, canonicalCreditOutstanding(state), 0.0)
        assertEquals(600.0, canonicalNetPosition(state), 0.0)
    }

    @Test
    fun localizedCreditLabelIsStillRecognizedWhenCanonicalKindIsUnavailable() {
        val state = MoneyUiState(
            cards = listOf(
                MoneyCard(
                    id = "legacy-credit",
                    nickname = "Κάρτα",
                    last4 = "0000",
                    kind = "Πιστωτική κάρτα",
                    currentBalance = 125.0,
                    limit = 1_000.0,
                    vaultState = VaultState.LOCKED,
                ),
            ),
        )

        assertEquals(125.0, canonicalCreditOutstanding(state), 0.0)
    }
}
