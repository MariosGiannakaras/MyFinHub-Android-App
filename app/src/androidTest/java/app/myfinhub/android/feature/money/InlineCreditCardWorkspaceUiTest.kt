package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.core.data.CanonicalCreditStatementView
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InlineCreditCardWorkspaceUiTest {
    @get:Rule val composeRule = createComposeRule()

    private val credit = MoneyCard(
        id = "credit-one", nickname = "Visa Credit", last4 = "1234",
        kind = "Πιστωτική", currentBalance = 420.0, limit = 1200.0,
        vaultState = VaultState.LOCKED, canonicalKind = "credit",
        activity = listOf(MoneyCardActivity(
            id = "purchase-id", dateLabel = "14/10/2026",
            title = "Καφές", amount = -12.0, kind = MoneyCardActivityKind.PURCHASE,
        )),
    )

    private val statement = CanonicalCreditStatementView(
        id = "credit-one:2026-10-25", cardId = "credit-one",
        openDate = "2026-09-26", closeDate = "2026-10-25",
        dueDate = "2026-11-05", boundaryRule = "next-cycle",
        purchaseIds = listOf("purchase-id"), paymentIds = emptyList(),
        purchaseTotal = 100.0, paymentTotal = 20.0, remaining = 80.0,
        status = "open",
    )

    @Test fun cardDebtAndLinkedStatementPurchasesAppearWithoutOpeningDetailPage() {
        composeRule.setContent {
            MyFinHubTheme {
                InlineCreditCardWorkspace(
                    card = credit, statements = listOf(statement), amountsVisible = true,
                    onPayCard = {}, onAddPurchase = {}, onOpenActivity = {}, onOpenDetails = {},
                )
            }
        }
        composeRule.onNodeWithTag("inline_credit_card_workspace").assertExists()
        composeRule.onNodeWithText("Τρέχουσα οφειλή").assertExists()
        composeRule.onNodeWithText("Καταστάσεις πιστωτικής").assertExists()
        composeRule.onNodeWithText("Αγορές και πληρωμές").assertExists()
        composeRule.onNodeWithText("Καφές").assertExists()
        composeRule.onNodeWithText("Πληρωμή κάρτας").assertExists()
        composeRule.onNodeWithText("Καταχώριση αγοράς").assertExists()
    }

    @Test fun privacyHidesFinancialAmountsAndActionsUseSelectedCard() {
        var pays = 0
        var purchases = 0
        composeRule.setContent {
            MyFinHubTheme {
                InlineCreditCardWorkspace(
                    card = credit, statements = listOf(statement), amountsVisible = false,
                    onPayCard = { pays++ }, onAddPurchase = { purchases++ },
                    onOpenActivity = {}, onOpenDetails = {},
                )
            }
        }
        composeRule.onNodeWithText("420,00", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("1.200,00", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("credit_utilization_indicator").assertDoesNotExist()
        composeRule.onNodeWithText("Χρήση ορίου:", substring = true).assertDoesNotExist()
        composeRule.onAllNodesWithText("•••• €", substring = true)[0].assertExists()
        composeRule.onNodeWithText("Πληρωμή κάρτας").performClick()
        composeRule.onNodeWithText("Καταχώριση αγοράς").performClick()
        assertEquals(1, pays)
        assertEquals(1, purchases)
    }

    @Test fun debitCardNeverOffersCreditRepaymentOrStatementManagement() {
        composeRule.setContent {
            MyFinHubTheme {
                InlineCreditCardWorkspace(
                    card = credit.copy(canonicalKind = "debit", kind = "Χρεωστική"),
                    statements = emptyList(), amountsVisible = true,
                    onPayCard = {}, onAddPurchase = {}, onOpenActivity = {}, onOpenDetails = {},
                )
            }
        }
        composeRule.onNodeWithText("Πληρωμή κάρτας").assertDoesNotExist()
        composeRule.onNodeWithText("Καταστάσεις πιστωτικής").assertDoesNotExist()
        composeRule.onNodeWithText("Στοιχεία / διαχείριση κάρτας").assertExists()
    }
}
