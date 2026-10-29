package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
        rule.onNodeWithTag("card_detail_payment_action").assertIsNotEnabled()
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
        rule.onNodeWithTag("card_detail_payment_action").assertIsNotEnabled()
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

    @Test fun archiveDialogExplainsProtectedSecretsAreRetainedAndArchivesOnlyChosenCard() {
        var archivedId: String? = null
        rule.setContent { MyFinHubTheme {
            CanonicalCardDetailScreen(
                card = credit(), onBack = {}, amountsVisibleOverride = false,
                onRemoveCard = { archivedId = it },
            )
        } }
        rule.onNodeWithText("Αρχειοθέτηση κάρτας").performScrollTo().performClick()
        rule.onNodeWithText("Η αρχειοθέτηση δεν διαγράφει τα κρυπτογραφημένα στοιχεία", substring = true)
            .assertExists()
        rule.onNodeWithText("θα χρειαστεί νέα καταχώριση", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("confirm_remove_card").performClick()
        rule.runOnIdle { assertEquals("credit-integrity", archivedId) }
    }
}
