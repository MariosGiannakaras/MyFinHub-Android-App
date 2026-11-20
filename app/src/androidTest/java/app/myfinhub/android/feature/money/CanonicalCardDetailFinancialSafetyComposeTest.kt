package app.myfinhub.android.feature.money

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The full detail must enforce the same source money + privacy rules as inline Wallet. */
class CanonicalCardDetailFinancialSafetyComposeTest {
    @get:Rule val rule = createComposeRule()

    private fun credit(
        debt: Double = 420.0,
        limit: Double? = 1000.0,
        activityAmount: Double = -12.0,
    ) = MoneyCard(
        id = "credit-integrity", nickname = "Visa Credit", last4 = "1234",
        kind = "Πιστωτική", canonicalKind = "credit",
        currentBalance = debt, limit = limit, vaultState = VaultState.LOCKED,
        activity = listOf(
            MoneyCardActivity(
                id = "purchase-source", dateLabel = "2026-10-29", title = "Αγορά",
                amount = activityAmount, kind = MoneyCardActivityKind.PURCHASE,
            ),
        ),
    )

    @Test fun invalidDebtLimitAndHistoryNeverDisplayNaNOrEnablePayment() {
        var pays = 0
        rule.setContent { MyFinHubTheme {
            CanonicalCardDetailScreen(
                card = credit(Double.NaN, Double.POSITIVE_INFINITY, Double.NaN),
                onBack = {}, onPayCard = { pays++ }, amountsVisibleOverride = true,
            )
        } }
        rule.onAllNodesWithText("Μη έγκυρο ποσό", substring = true)[0].assertExists()
        rule.onNodeWithTag("card_detail_invalid_limit").assertExists()
        rule.onNodeWithTag("card_detail_payment_action").assertDoesNotExist()
        rule.onNodeWithText("NaN", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Infinity", substring = true).assertDoesNotExist()
        rule.onNodeWithText("∞", substring = true).assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, pays) }
    }

    @Test fun hideAmountsMasksInvalidAndValidCardHistoryWithoutInvalidSourceWarning() {
        rule.setContent { MyFinHubTheme {
            CanonicalCardDetailScreen(
                card = credit(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY),
                onBack = {}, amountsVisibleOverride = false,
            )
        } }
        rule.onAllNodesWithText("•••• €", substring = true)[0].assertExists()
        rule.onNodeWithTag("card_detail_invalid_limit").assertDoesNotExist()
        rule.onNodeWithText("Μη έγκυρο ποσό", substring = true).assertDoesNotExist()
        rule.onNodeWithText("NaN", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("card_detail_payment_action").assertDoesNotExist()
    }

    @Test fun validDebtRepaymentAndCreditMetricsStayAccessible() {
        var pays = 0
        rule.setContent { MyFinHubTheme {
            CanonicalCardDetailScreen(
                card = credit(), onBack = {}, onPayCard = { pays++ },
                amountsVisibleOverride = true,
            )
        } }
        rule.onNodeWithTag("card_detail_payment_action").assertIsEnabled().performClick()
        rule.onNodeWithTag("card_detail_invalid_limit").assertDoesNotExist()
        rule.onNodeWithText("Τρέχουσα οφειλή").assertExists()
        rule.onNodeWithText("Πιστωτικό όριο").assertExists()
        rule.onNodeWithText("Διαθέσιμη πίστωση").assertExists()
        rule.runOnIdle { assertEquals(1, pays) }
    }

    @Test fun zeroDebtHighlightsAvailablePurchaseAndDoesNotEnablePayment() {
        var purchases = 0
        var payments = 0
        rule.setContent { MyFinHubTheme {
            CanonicalCardDetailScreen(
                card = credit(debt = 0.0), onBack = {},
                onAddPurchase = { purchases++ }, onPayCard = { payments++ },
                amountsVisibleOverride = true,
            )
        } }
        rule.onNodeWithTag("card_detail_purchase_primary").assertIsEnabled().performClick()
        rule.onNodeWithTag("card_detail_payment_action").assertDoesNotExist()
        rule.onNodeWithTag("card_detail_payment_unavailable_reason").assertExists()
        rule.runOnIdle {
            assertEquals(1, purchases)
            assertEquals(0, payments)
        }
    }

    @Test fun malformedImportedCardLastFourNeverAppearsAndDetailCannotSwitchCards() {
        val imported = credit().copy(last4 = "4242424242424242")
        rule.setContent { MyFinHubTheme {
            CanonicalCardDetailScreen(
                card = imported, onBack = {}, amountsVisibleOverride = true,
            )
        } }
        rule.onNodeWithText("4242424242424242", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("card_switcher").assertDoesNotExist()
        rule.onNodeWithText("Αλλαγή κάρτας").assertDoesNotExist()
    }

    @Test fun nativeCardDeleteDialogNeverExposesMalformedFullPanSuffix() {
        rule.setContent { MyFinHubTheme {
            CanonicalCardDeleteDialog(
                card = credit().copy(last4 = "4242424242424242"),
                onConfirm = {}, onDismiss = {},
            )
        } }
        rule.onNodeWithText("4242424242424242", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Η Visa Credit •••• θα αφαιρεθεί από τις ενεργές κάρτες.", substring = true).assertExists()
    }

    @Test fun archiveDialogExplainsProtectedSecretsAreRetainedAndArchivesOnlyChosenCard() {
        var archivedId: String? = null
        rule.setContent { MyFinHubTheme {
            CanonicalCardDetailScreen(
                card = credit(), onBack = {}, amountsVisibleOverride = false,
                onRemoveCard = { archivedId = it },
            )
        } }
        rule.onNodeWithText("Αφαίρεση από το MyFinHub").performScrollTo().performClick()
        rule.onNodeWithText("Η αρχειοθέτηση δεν διαγράφει τα κρυπτογραφημένα στοιχεία", substring = true)
            .assertExists()
        rule.onNodeWithText("θα χρειαστεί νέα καταχώριση", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("confirm_remove_card").performClick()
        rule.runOnIdle { assertEquals("credit-integrity", archivedId) }
    }
    @Test fun noDebtRemovesPointlessDisabledPaymentActionFromCardsTab() {
        var payments = 0
        rule.setContent { MyFinHubTheme {
            CanonicalWalletScreen(
                state = MoneyUiState(cards = listOf(credit(debt = 0.0))),
                cardsOnly = true, companionMode = true,
                onOpenAccount = {}, onOpenNetPosition = {}, onOpenCard = {},
                onAddCard = {}, onOpenLoans = {}, onOpenLending = {},
                onPayCreditCard = { payments++ },
                amountsVisibleOverride = true,
            )
        } }
        rule.onNodeWithTag("card_companion_payment").assertDoesNotExist()
        rule.onNodeWithTag("card_companion_payment_unavailable_reason").assertExists()
        rule.onNodeWithTag("card_companion_purchase").assertExists()
        rule.runOnIdle { assertEquals(0, payments) }
    }

    @Test fun invalidStatementCannotOfferPaymentInDetailOrInlineWallet() {
        val bad = app.myfinhub.android.core.data.CanonicalCreditStatementView(
            id = "imported-corrupt", cardId = "credit-integrity",
            openDate = "2026-09-26", closeDate = "2026-10-25",
            dueDate = "2026-11-05", boundaryRule = "next-cycle",
            purchaseIds = listOf("purchase-source"), paymentIds = emptyList(),
            purchaseTotal = Double.NaN, paymentTotal = Double.NaN,
            remaining = Double.NaN, status = "invalid", sourceIntegrityValid = false,
        )
        var payments = 0
        val showDetail = mutableStateOf(true)
        rule.setContent { MyFinHubTheme {
            if (showDetail.value) {
                CanonicalCardDetailScreen(
                    card = credit(), statements = listOf(bad), onBack = {},
                    amountsVisibleOverride = true, onPayStatement = { _, _ -> payments++ },
                    fullStatementIdCounts = mapOf(bad.id to 1),
                )
            } else {
                InlineCreditCardWorkspace(
                    card = credit(), statements = listOf(bad), amountsVisible = true,
                    onPayCard = {}, onAddPurchase = {}, onOpenActivity = {},
                    onOpenDetails = {}, onPayStatement = { _, _ -> payments++ },
                    fullStatementIdCounts = mapOf(bad.id to 1),
                )
            }
        } }
        rule.onNodeWithTag("card_statement_history").assertExists()
        rule.onNodeWithTag("card_statement_pay_0").assertDoesNotExist()
        rule.onNodeWithText("Χρειάζεται έλεγχος", substring = true).assertExists()
        rule.onNodeWithText("Ασυνεπή ιστορικά στοιχεία", substring = true).assertExists()
        rule.onNodeWithText("NaN", substring = true).assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, payments) }

        rule.runOnIdle { showDetail.value = false }
        rule.onNodeWithTag("inline_credit_pay_statement_0").assertDoesNotExist()
        rule.onNodeWithTag("inline_credit_statement_identity_warning_0").assertExists()
        rule.onNodeWithText("NaN", substring = true).assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, payments) }
    }

}
