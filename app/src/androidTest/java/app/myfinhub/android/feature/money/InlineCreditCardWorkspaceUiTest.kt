package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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

    @Test fun invalidImportedCreditAndHistoryAmountsFailClosedWithoutFakeNumbers() {
        composeRule.setContent {
            MyFinHubTheme {
                InlineCreditCardWorkspace(
                    card = credit.copy(
                        currentBalance = Double.NaN,
                        limit = Double.POSITIVE_INFINITY,
                        activity = listOf(credit.activity.first().copy(amount = Double.NEGATIVE_INFINITY)),
                    ),
                    statements = listOf(statement.copy(
                        remaining = Double.NaN,
                        purchaseTotal = Double.POSITIVE_INFINITY,
                        paymentTotal = Double.NEGATIVE_INFINITY,
                    )),
                    amountsVisible = true,
                    onPayCard = {}, onAddPurchase = {}, onOpenActivity = {}, onOpenDetails = {},
                )
            }
        }
        composeRule.onAllNodesWithText("Μη έγκυρο ποσό", substring = true)[0].assertExists()
        composeRule.onNodeWithTag("inline_credit_invalid_limit").assertExists()
        composeRule.onNodeWithTag("credit_utilization_indicator").assertDoesNotExist()
        composeRule.onNodeWithText("Πληρωμή κάρτας").assertIsNotEnabled()
        composeRule.onNodeWithText("NaN", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("∞", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Infinity", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Αγορές και πληρωμές").assertExists()
        composeRule.onNodeWithText("Καφές").assertExists()
    }

    @Test fun invalidAmountsRemainMaskedWhenPrivacyIsEnabled() {
        composeRule.setContent {
            MyFinHubTheme {
                InlineCreditCardWorkspace(
                    card = credit.copy(
                        currentBalance = Double.NaN,
                        limit = Double.POSITIVE_INFINITY,
                        activity = listOf(credit.activity.first().copy(amount = Double.NaN)),
                    ),
                    statements = listOf(statement.copy(remaining = Double.NaN)),
                    amountsVisible = false,
                    onPayCard = {}, onAddPurchase = {}, onOpenActivity = {}, onOpenDetails = {},
                )
            }
        }
        composeRule.onAllNodesWithText("•••• €", substring = true)[0].assertExists()
        composeRule.onNodeWithText("Μη έγκυρο ποσό", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("inline_credit_invalid_limit").assertDoesNotExist()
        composeRule.onNodeWithTag("credit_utilization_indicator").assertDoesNotExist()
        composeRule.onNodeWithText("Πληρωμή κάρτας").assertIsNotEnabled()
    }

    @Test fun walletPaySpecificStatementWithoutOpeningDetailsAndKeepHistoricPrivacy() {
        val second = statement.copy(id = "settled", remaining = 0.0, status = "paid")
        val invalid = statement.copy(id = "invalid", remaining = Double.NaN)
        var selected: Pair<String, Double>? = null
        composeRule.setContent {
            MyFinHubTheme {
                InlineCreditCardWorkspace(
                    card = credit, statements = listOf(statement, second, invalid),
                    amountsVisible = false, onPayCard = {}, onAddPurchase = {},
                    onOpenActivity = {}, onOpenDetails = {},
                    onPayStatement = { id, amount -> selected = id to amount },
                    fullStatementIdCounts = mapOf(statement.id to 1, "settled" to 1, "invalid" to 1),
                )
            }
        }
        composeRule.onNodeWithTag("inline_credit_filter_payable").performClick()
        composeRule.onNodeWithTag("inline_credit_pay_statement_0").performClick()
        assertEquals(statement.id to 80.0, selected)
        composeRule.onNodeWithTag("inline_credit_pay_statement_1").assertDoesNotExist()
        composeRule.onNodeWithTag("inline_credit_filter_all").performClick()
        composeRule.onNodeWithText("NaN", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("80,00", substring = true).assertDoesNotExist()
    }

    @Test fun duplicateSourceStatementIdNeverOpensWalletPayment() {
        var selected = 0
        composeRule.setContent {
            MyFinHubTheme {
                InlineCreditCardWorkspace(
                    card = credit, statements = listOf(statement),
                    amountsVisible = true, onPayCard = {}, onAddPurchase = {},
                    onOpenActivity = {}, onOpenDetails = {},
                    onPayStatement = { _, _ -> selected++ },
                    fullStatementIdCounts = mapOf(statement.id to 2),
                )
            }
        }
        composeRule.onNodeWithTag("inline_credit_statement_identity_warning_0").assertExists()
        composeRule.onNodeWithTag("inline_credit_pay_statement_0").assertDoesNotExist()
        assertEquals(0, selected)
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
    @Test fun inlineCreditNeverRendersAnotherCardsStatement() {
        val foreign = statement.copy(
            id = "foreign-statement", cardId = "another-credit-card",
            openDate = "2099-01-01", remaining = 93521.0,
        )
        composeRule.setContent {
            MyFinHubTheme {
                InlineCreditCardWorkspace(
                    card = credit, statements = listOf(foreign), amountsVisible = true,
                    onPayCard = {}, onAddPurchase = {}, onOpenActivity = {}, onOpenDetails = {},
                )
            }
        }
        composeRule.onNodeWithText("Δεν υπάρχουν συνδεδεμένες καταστάσεις.", substring = true)
            .assertExists()
        composeRule.onNodeWithText("2099-01-01", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("93.521", substring = true).assertDoesNotExist()
    }

}
