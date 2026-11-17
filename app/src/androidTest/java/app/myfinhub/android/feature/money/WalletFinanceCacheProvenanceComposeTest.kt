package app.myfinhub.android.feature.money

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

class WalletFinanceCacheProvenanceComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun cardsDiscloseUnverifiedCachedDebtAndPendingWritesWithoutBlockingCards() {
        val cached = mutableStateOf(true)
        val pending = mutableIntStateOf(2)
        rule.setContent {
            MyFinHubTheme {
                CanonicalWalletScreen(
                    state = syntheticMoneyUiState(),
                    cardsOnly = true, companionMode = true,
                    onOpenAccount = {}, onOpenNetPosition = {}, onOpenCard = {},
                    onAddCard = {}, onOpenLoans = {}, onOpenLending = {},
                    financeCacheMode = cached.value,
                    pendingFinanceChanges = pending.intValue,
                )
            }
        }
        rule.onNodeWithTag("wallet_finance_cache_notice").assertExists()
        rule.onNodeWithText("Οι συναλλαγές και οι οφειλές μπορεί να μην είναι ενημερωμένες.", substring = true)
            .assertExists()
        rule.onNodeWithText("Εκκρεμούν 2 τοπικές αλλαγές για συγχρονισμό.").assertExists()

        rule.runOnIdle { pending.intValue = 0 }
        rule.onNodeWithText("Εκκρεμούν 2 τοπικές αλλαγές για συγχρονισμό.").assertDoesNotExist()
        rule.onNodeWithTag("wallet_finance_cache_notice").assertExists()

        rule.runOnIdle { cached.value = false }
        rule.onNodeWithTag("wallet_finance_cache_notice").assertDoesNotExist()
    }
}
