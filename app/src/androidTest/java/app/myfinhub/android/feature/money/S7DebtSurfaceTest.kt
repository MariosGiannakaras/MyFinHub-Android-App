package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
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
    fun settledLendingHistoryRemainsVisibleWithoutAnUnsafeRepaymentAction() {
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalLendingScreen(
                    state = MoneyUiState(
                        lendingReceivable = 0.0,
                        lendingHistory = listOf(
                            LendingHistoryItem("same", "Νίκος", "2026-10-01", "lent", 40.0,
                                "Ιστορικό δανεικών", origin = "legacy", runningOutstanding = 40.0),
                            LendingHistoryItem("same", "Νίκος", "2026-10-10", "repaid", 40.0,
                                "Ιστορικό επιστροφής", origin = "legacy", runningOutstanding = 0.0),
                        ),
                    ),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Ιστορικό δανεικών").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Επιστροφή").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Καταχώριση επιστροφής").assertDoesNotExist()
        // Both imported rows have the same historic identifier; Compose must
        // use independent row keys rather than dropping one or crashing.
        composeRule.onNodeWithText("Δανεισμός").performScrollTo().assertExists()
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
