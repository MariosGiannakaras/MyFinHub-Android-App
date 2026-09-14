package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class S7DebtSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aggregateOnlyDebt_remainsVisibleWithoutInventingEmptyDetail() {
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalWalletScreen(
                    state = MoneyUiState(
                        loanOutstanding = 4_240.0,
                        lendingReceivable = 310.0,
                        aggregateCreditOutstanding = 875.0,
                    ),
                    initiallyShowDebts = true,
                    onOpenAccount = {},
                    onOpenNetPosition = {},
                    onOpenCard = {},
                    onAddCard = {},
                    onOpenLoans = {},
                    onOpenLending = {},
                    amountsVisibleOverride = true,
                )
            }
        }
        composeRule.onNodeWithText("Πιστωτικό χρέος").assertIsDisplayed()
        composeRule.onNodeWithText("Δάνεια").assertIsDisplayed()
        composeRule.onNodeWithText("Απαιτήσεις").assertIsDisplayed()
        composeRule.onNodeWithText("Συνολικό υπόλοιπο · χωρίς αναλυτικές εγγραφές").assertIsDisplayed()
    }

    @Test
    fun availableLendingItem_recordsRepaymentThroughExplicitAction() {
        var recorded: LendingItem? = null
        val item = LendingItem("lend-1", "Νίκος", 80.0, "20 Σεπ", "Καφές")
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalLendingScreen(
                    state = MoneyUiState(lendingReceivable = 80.0, lendingItems = listOf(item)),
                    onBack = {},
                    onRecordRepayment = { recorded = it },
                )
            }
        }
        composeRule.onNodeWithText("Καταχώριση επιστροφής").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("lend-1", recorded?.id) }
    }
}
