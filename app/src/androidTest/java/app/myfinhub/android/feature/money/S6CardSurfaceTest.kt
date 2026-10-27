package app.myfinhub.android.feature.money

import androidx.compose.runtime.getValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
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
        // Ensure the small visible pagination dot has a full accessible touch target.
        val dotBounds = composeRule.onNodeWithTag("credit_card_dot_credit").fetchSemanticsNode().boundsInRoot
        val minTouchPx = with(composeRule.density) { 48.dp.toPx() }
        assertTrue("Card selector needs >=48dp width", dotBounds.width >= minTouchPx - 0.5f)
        assertTrue("Card selector needs >=48dp height", dotBounds.height >= minTouchPx - 0.5f)
        composeRule.onNodeWithTag("credit_card_dot_credit").performClick()
        composeRule.onNodeWithTag("credit_card_credit").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("credit", opened) }
    }

    @Test
    fun verticalCardSwipesRotateOneMixedDebitCreditStackWithoutNavigationArrows() {
        var activeCard: String? = null
        var detailOpened: String? = null
        composeRule.setContent {
            MyFinHubTheme {
                CreditCardStack(
                    cards = listOf(debit, credit),
                    secretState = CardSecretUiState.Hidden(),
                    onActiveCardChanged = { activeCard = it },
                    onOpenCard = { detailOpened = it },
                    onDeleteCard = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals("debit", activeCard) }
        composeRule.onNodeWithTag("credit_card_debit").performTouchInput {
            val inset = (bottom - top) * .18f
            swipeUp(startY = bottom - inset, endY = top + inset, durationMillis = 400L)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals("credit", activeCard)
            assertEquals(null, detailOpened)
        }
        composeRule.onNodeWithTag("credit_card_credit").performTouchInput {
            val inset = (bottom - top) * .18f
            swipeDown(startY = top + inset, endY = bottom - inset, durationMillis = 400L)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals("debit", activeCard) }
    }

    @Test
    fun longCreditHistoryExpandsInlineWithoutLeavingSelectedCard() {
        val activities = (1..19).map { index ->
            MoneyCardActivity(
                id = "event-$index",
                dateLabel = "2026-10-${index.toString().padStart(2, '0')}",
                title = "Αγορά $index",
                amount = index.toDouble(),
                kind = MoneyCardActivityKind.PURCHASE,
            )
        }
        var selectedEvent: String? = null
        composeRule.setContent {
            MyFinHubTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InlineCreditCardWorkspace(
                        card = credit.copy(activity = activities),
                        statements = emptyList(),
                        amountsVisible = false,
                        onPayCard = {},
                        onAddPurchase = {},
                        onOpenActivity = { selectedEvent = it },
                        onOpenDetails = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("inline_credit_activity_event-8").assertExists()
        assertTrue(composeRule.onAllNodesWithTag("inline_credit_activity_event-9").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("inline_credit_load_more").performScrollTo().performClick()
        composeRule.onNodeWithTag("inline_credit_activity_event-9").assertExists()
        composeRule.onNodeWithTag("inline_credit_load_more").performScrollTo().performClick()
        composeRule.onNodeWithTag("inline_credit_activity_event-17").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("event-17", selectedEvent) }
        assertTrue(composeRule.onAllNodesWithText("17,00", substring = true).fetchSemanticsNodes().isEmpty())
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
    fun cardDetail_routesToProtectedSurface_andUsesExplicitArchiveLanguage() {
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
        composeRule.onNodeWithText("Αρχειοθέτηση κάρτας").performScrollTo().performClick()
        composeRule.onNodeWithText("Δεν ακυρώνεται στην τράπεζα.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("μπορεί να επανέλθει", substring = true).assertIsDisplayed()
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
    fun secureEditor_dropsSensitiveDraftsImmediatelyWhenVaultAuthorityIsRevoked() {
        var state by mutableStateOf<CardSecretUiState>(
            CardSecretUiState.Revealed(cardId = "credit", pan = null, expiry = null, cvv = null),
        )
        var savedPan: String? = null
        var savedExpiry: String? = null
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalCardSecureDetailsScreen(
                    cardId = "credit",
                    card = credit,
                    secretState = state,
                    onReveal = {},
                    onSaveServerSecrets = { pan, expiry ->
                        savedPan = String(pan)
                        savedExpiry = String(expiry)
                        pan.fill('\u0000')
                        expiry.fill('\u0000')
                    },
                    onSaveCvv = { it.fill('\u0000') },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Αλλαγή αριθμού / λήξης").performClick()
        composeRule.onNodeWithTag("card_secure_pan_editor").performTextInput("4000000000000002")
        composeRule.onNodeWithTag("card_secure_expiry_editor").performTextInput("11/31")

        composeRule.runOnIdle { state = CardSecretUiState.AuthRejected }
        composeRule.onNodeWithTag("card_secure_pan_editor").assertDoesNotExist()
        composeRule.onNodeWithTag("card_secure_expiry_editor").assertDoesNotExist()
        composeRule.onNodeWithText("Αποθήκευση αριθμού / λήξης").assertDoesNotExist()
        composeRule.onNodeWithText("Η ασφαλής συνεδρία έληξε. Χρειάζεται νέα σύνδεση.").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(null, savedPan)
            assertEquals(null, savedExpiry)
            state = CardSecretUiState.Revealed(cardId = "credit", pan = null, expiry = null, cvv = null)
        }

        // New owner-authorized read may open a fresh editor, never the prior draft.
        composeRule.onNodeWithText("Αλλαγή αριθμού / λήξης").performClick()
        composeRule.onNodeWithTag("card_secure_pan_editor").performTextInput("4111111111111111")
        composeRule.onNodeWithTag("card_secure_expiry_editor").performTextInput("12/32")
        composeRule.onNodeWithText("Αποθήκευση αριθμού / λήξης").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("4111111111111111", savedPan)
            assertEquals("12/32", savedExpiry)
        }
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
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Αριθμός. Πλήρης τιμή εμφανίζεται.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Λήξη. Πλήρης τιμή εμφανίζεται.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("CVV. Πλήρης τιμή εμφανίζεται.").assertIsDisplayed()
        assertTrue(
            runCatching { composeRule.onNodeWithText("Αποκάλυψη στοιχείων").fetchSemanticsNode() }.isFailure,
        )
        composeRule.runOnIdle { assertEquals(1, loadCalls) }
    }
}
