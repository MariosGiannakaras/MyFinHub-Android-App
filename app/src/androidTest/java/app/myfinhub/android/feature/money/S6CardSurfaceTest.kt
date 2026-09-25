package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class S6CardSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val debit = MoneyCard(
        id = "debit",
        nickname = "Καθημερινή",
        last4 = "4242",
        kind = "Χρεωστική",
        currentBalance = 0.0,
        limit = null,
        vaultState = VaultState.AVAILABLE,
        network = "VISA",
        bankId = "piraeus",
        canonicalKind = "debit",
    )

    private val credit = MoneyCard(
        id = "credit",
        nickname = "Πιστωτική ταξιδιών",
        last4 = "1881",
        kind = "Πιστωτική",
        currentBalance = 312.20,
        limit = 2_000.0,
        vaultState = VaultState.LOCKED,
        network = "MASTERCARD",
        bankId = "revolut",
        canonicalKind = "credit",
    )

    @Test
    fun walletCards_useStackedPresentation_andRouteByStableId() {
        var opened: String? = null
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalWalletScreen(
                    state = MoneyUiState(cards = listOf(debit, credit)),
                    onOpenAccount = {},
                    onOpenNetPosition = {},
                    onOpenCard = { opened = it },
                    onAddCard = {},
                    cardSecretState = CardSecretUiState.Revealed(
                        cardId = "debit",
                        pan = "4242424242424242",
                        expiry = "12/30",
                        cvv = "123",
                    ),
                    onOpenLoans = {},
                    onOpenLending = {},
                    amountsVisibleOverride = true,
                )
            }
        }

        composeRule.onNode(hasText("Κάρτες") and hasClickAction()).performClick()
        composeRule.onNodeWithTag("credit_card_stack").assertIsDisplayed()
        composeRule.onNodeWithTag("credit_card_debit").assertIsDisplayed()
        composeRule.onNodeWithTag("credit_card_dot_credit").performClick()
        composeRule.onNodeWithTag("credit_card_credit").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("credit", opened) }
    }

    @Test
    fun creditDetail_exposesCreditSemantics_primaryPay_andStableSwitcher() {
        var selected: String? = null
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalCardDetailScreen(
                    card = credit,
                    cards = listOf(credit, debit),
                    onSelectCard = { selected = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Τρέχουσα οφειλή").assertIsDisplayed()
        composeRule.onNodeWithText("Πιστωτικό όριο").assertIsDisplayed()
        composeRule.onNodeWithText("Διαθέσιμη πίστωση").assertIsDisplayed()
        composeRule.onNodeWithText("Πληρωμή κάρτας").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Καταχώριση αγοράς").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("card_switcher").performScrollTo().performClick()
        composeRule.onNodeWithTag("card_picker_debit").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("debit", selected) }
    }

    @Test
    fun cardCreation_usesSelectorSheets_andCreditFieldsRemainConditional() {
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalCardCreateScreen(
                    cards = emptyList(),
                    onCreate = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("card_create_provider").performScrollTo().performClick()
        composeRule.onNodeWithTag("card_create_provider_revolut").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Revolut").assertIsDisplayed()

        composeRule.onNodeWithTag("card_create_kind").performScrollTo().performClick()
        composeRule.onNodeWithTag("card_create_kind_credit").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Πιστωτικό όριο (προαιρετικό)").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("card_create_network").performScrollTo().performClick()
        composeRule.onNodeWithTag("card_create_network_mastercard").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Mastercard").assertIsDisplayed()
        composeRule.onNodeWithTag("card_create_pan").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("card_create_expiry").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("card_create_cvv").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cardDetail_routesToProtectedSurface_andUsesExplicitRemovalLanguage() {
        var secureCardId: String? = null
        var removedCardId: String? = null
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalCardDetailScreen(
                    card = credit,
                    onOpenSecureDetails = { secureCardId = it },
                    onRemoveCard = { removedCardId = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("card_secure_details").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("credit", secureCardId) }
        composeRule.onNodeWithText("Αφαίρεση από το MyFinHub").performScrollTo().performClick()
        composeRule.onNodeWithText("Δεν ακυρώνεται στην τράπεζα.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_remove_card").performClick()
        composeRule.runOnIdle { assertEquals("credit", removedCardId) }
    }

    @Test
    fun partialSecretCleanup_offersRetryWithoutRepeatingCardRemoval() {
        var retryCardId: String? = null
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalCardDetailScreen(
                    cardId = "credit",
                    card = null,
                    cleanupState = CardSecretCleanupUiState.Failure(
                        cardId = "credit",
                        serverCleanupPending = false,
                        localCleanupPending = true,
                    ),
                    onRetryCleanup = { retryCardId = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Εκκρεμεί: τοπικά στοιχεία κάρτας.").assertIsDisplayed()
        composeRule.onNodeWithText("Δοκιμή καθαρισμού ξανά").performClick()
        composeRule.runOnIdle { assertEquals("credit", retryCardId) }
    }

    @Test
    fun cardDetails_showFullValues_withoutRevealGate() {
        var loadCalls = 0
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalCardSecureDetailsScreen(
                    cardId = "credit",
                    card = credit,
                    secretState = CardSecretUiState.Revealed(
                        cardId = "credit",
                        pan = "5555444433331881",
                        expiry = "09/31",
                        cvv = "731",
                    ),
                    onReveal = { loadCalls += 1 },
                    onSaveServerSecrets = { pan, expiry -> pan.fill('\u0000'); expiry.fill('\u0000') },
                    onSaveCvv = { it.fill('\u0000') },
                    onDeleteCvv = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("5555444433331881").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("09/31").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("731").performScrollTo().assertIsDisplayed()
        assertTrue(
            runCatching { composeRule.onNodeWithText("Αποκάλυψη στοιχείων").fetchSemanticsNode() }.isFailure,
        )
        composeRule.runOnIdle { assertEquals(1, loadCalls) }
    }
}
