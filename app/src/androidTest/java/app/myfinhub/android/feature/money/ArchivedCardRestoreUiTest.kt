package app.myfinhub.android.feature.money

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchivedCardRestoreUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun archivedCardIsRestorableWithoutRevealingSecretsOrOpeningDetails() {
        var restored = ""
        val archived = MoneyCard(
            id = "archived-credit", nickname = "Old Mastercard",
            last4 = "8241", kind = "Πιστωτική",
            currentBalance = 0.0, limit = 1000.0,
            vaultState = VaultState.LOCKED,
            canonicalKind = "credit", archivedAt = "2026-10-01T00:00:00Z",
        )
        val cards = mutableStateOf(MoneyUiState(archivedCards = listOf(archived)))
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalWalletScreen(
                    state = cards.value,
                    initiallyShowCards = true,
                    onOpenAccount = {}, onOpenNetPosition = {},
                    onOpenCard = {}, onAddCard = {},
                    onOpenLoans = {}, onOpenLending = {},
                    onRestoreCard = { restored = it },
                    amountsVisibleOverride = false,
                )
            }
        }
        composeRule.onNodeWithTag("wallet_list")
            .performScrollToNode(hasText("Αρχειοθετημένες κάρτες"))
        composeRule.onNodeWithText("Αρχειοθετημένες κάρτες").assertExists()
        composeRule.onNodeWithTag("wallet_list")
            .performScrollToNode(hasText("Επαναφορά"))
        val restore = composeRule.onNodeWithTag("restore_archived_card_archived-credit")
        restore.assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals("archived-credit", restored)
            cards.value = cards.value.copy(cards = listOf(archived.copy(archivedAt = null)))
        }
        composeRule.onNodeWithTag("wallet_list")
            .performScrollToNode(hasText("Επαναφορά"))
        restore.assertIsNotEnabled()
        composeRule.onNodeWithText("4242 4242", substring = true).assertDoesNotExist()
    }
}
