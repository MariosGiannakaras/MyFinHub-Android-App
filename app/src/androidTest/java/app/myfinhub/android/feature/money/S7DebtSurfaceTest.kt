package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        composeRule.onNodeWithTag("lending_select_person").performScrollTo().performClick()
        composeRule.onNodeWithTag("lending_record_repayment").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("lend-1", recorded?.id) }
    }

    @Test
    fun settledHistoryDoesNotShowMissingDetailsOrOfferNewRepayment() {
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalLendingScreen(
                    state = MoneyUiState(
                        lendingReceivable = 0.0,
                        lendingHistory = listOf(
                            LendingHistoryItem("settled", "Νίκος", "2026-10-11",
                                "repaid", 40.0, "Ιστορικό επιστροφής", 0.0),
                        ),
                    ),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Ιστορικό δανεικών").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ιστορικό επιστροφής").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Καταχώριση επιστροφής").assertDoesNotExist()
        composeRule.onNodeWithText("Δεν υπάρχουν αναλυτικά στοιχεία απαιτήσεων").assertDoesNotExist()
    }

    @Test
    fun lendingHistoryRendersDistinctLegacyRowsAndKeepsRepaymentActionOnPerson() {
        val current = LendingItem("person-anna", "Άννα", 65.0, "")
        val history = listOf(
            LendingHistoryItem("historical-shared", "Άννα", "2026-10-11",
                "repaid", 25.0, "Επιστροφή λογαριασμού", 65.0),
            LendingHistoryItem("historical-shared", "Άννα", "2026-10-09",
                "forgiven", 10.0, "Ιστορική διαγραφή", 90.0),
        )
        var selected: LendingItem? = null
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalLendingScreen(
                    state = MoneyUiState(
                        lendingReceivable = 65.0,
                        lendingItems = listOf(current),
                        lendingHistory = history,
                    ),
                    onBack = {},
                    onRecordRepayment = { selected = it },
                )
            }
        }
        composeRule.onNodeWithTag("lending_select_person").performScrollTo().performClick()
        composeRule.onNodeWithTag("lending_record_repayment").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("person-anna", selected?.id) }
        composeRule.onNodeWithText("Ιστορικό δανεικών").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Επιστροφή λογαριασμού").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ιστορική διαγραφή").performScrollTo().assertIsDisplayed()
    }
}
