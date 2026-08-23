package app.myfinhub.android.feature.money

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myfinhub.android.designsystem.MyFinHubTheme
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreditCardStackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun restoreAnimations() {
        setAnimatorDurationScale("1")
    }

    @Test
    fun verticalSwipe_reordersByStableId_revealAndCopyFollowFrontCard_andPaginationStaysStable() {
        val secretState = mutableStateOf<CardSecretUiState>(CardSecretUiState.Hidden())
        var activeCardId: String? = null
        val cards = testCards(3)

        composeRule.setContent {
            MyFinHubTheme {
                CreditCardStack(
                    cards = cards,
                    secretState = secretState.value,
                    onActiveCardChanged = { activeCardId = it },
                    onRevealSecrets = {
                        secretState.value = CardSecretUiState.Revealed(
                            cardId = requireNotNull(activeCardId),
                            pan = "4321 8765 2109 1234",
                            expiry = "06/30",
                            cvv = "418",
                        )
                    },
                    onHideSecrets = {
                        secretState.value = CardSecretUiState.Hidden(activeCardId)
                    },
                    onOpenCard = {},
                    onDeleteCard = {},
                )
            }
        }

        composeRule.waitUntil { activeCardId == "card-a" }
        composeRule.onNodeWithTag("credit_card_stack_dots").assertIsDisplayed()
        composeRule.onNodeWithTag("credit_card_dot_card-a").assertIsDisplayed()
        composeRule.onNodeWithTag("credit_card_dot_card-b").assertIsDisplayed()
        composeRule.onNodeWithTag("credit_card_dot_card-c").assertIsDisplayed()

        composeRule.onNodeWithTag("credit_card_card-a").performTouchInput { swipeUp() }
        composeRule.waitUntil(timeoutMillis = TimeUnit.SECONDS.toMillis(5)) { activeCardId == "card-b" }

        composeRule.onNodeWithContentDescription("Εμφάνιση στοιχείων").performClick()
        composeRule.onNodeWithText("4321 8765 2109 1234").assertIsDisplayed()
        composeRule.onNodeWithText("06/30").assertIsDisplayed()
        composeRule.onNodeWithText("418").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Αντιγραφή αριθμού").performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copiedPan = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        assertEquals("4321 8765 2109 1234", copiedPan)

        composeRule.onNodeWithContentDescription("Απόκρυψη στοιχείων").performClick()
        composeRule.onNodeWithText("•••• •••• •••• 2222").assertIsDisplayed()
        composeRule.onNodeWithText("••/••").assertIsDisplayed()
        composeRule.onNodeWithText("•••").assertIsDisplayed()
        composeRule.onNodeWithTag("credit_card_dot_card-a").assertIsDisplayed()
        composeRule.onNodeWithTag("credit_card_dot_card-b").assertIsDisplayed()
        composeRule.onNodeWithTag("credit_card_dot_card-c").assertIsDisplayed()
    }

    @Test
    fun deleteCancel_restoresNormalCardState() {
        val cards = testCards(1)

        composeRule.setContent {
            MyFinHubTheme {
                CreditCardStack(
                    cards = cards,
                    secretState = CardSecretUiState.Hidden("card-a"),
                    onActiveCardChanged = {},
                    onRevealSecrets = {},
                    onHideSecrets = {},
                    onOpenCard = {},
                    onDeleteCard = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Διαγραφή κάρτας").performClick()
        composeRule.onNodeWithTag("card_delete_slider").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ακύρωση").performClick()

        composeRule.onNodeWithTag("card_delete_slider").assertDoesNotExist()
        composeRule.onNodeWithTag("credit_card_stack_empty").assertDoesNotExist()
        composeRule.onNodeWithTag("credit_card_card-a").assertIsDisplayed()
    }

    @Test
    fun deleteBelowThreshold_doesNotCommit() {
        val deleted = mutableListOf<String>()

        composeRule.setContent {
            MyFinHubTheme {
                CreditCardStack(
                    cards = testCards(1),
                    secretState = CardSecretUiState.Hidden("card-a"),
                    onActiveCardChanged = {},
                    onRevealSecrets = {},
                    onHideSecrets = {},
                    onOpenCard = {},
                    onDeleteCard = deleted::add,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Διαγραφή κάρτας").performClick()
        composeRule.onNodeWithTag("card_delete_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(.89f) }
        composeRule.onNodeWithTag("card_delete_slider").performKeyInput { pressKey(Key.Enter) }

        composeRule.runOnIdle { assertEquals(emptyList<String>(), deleted) }
        composeRule.onNodeWithTag("card_delete_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("credit_card_card-a").assertIsDisplayed()
    }

    @Test
    fun deleteAtThreshold_removesStableId_andAllowsEmptyStack() {
        val deleted = mutableListOf<String>()
        val cards = testCards(1)

        composeRule.setContent {
            MyFinHubTheme {
                CreditCardStack(
                    cards = cards,
                    secretState = CardSecretUiState.Hidden("card-a"),
                    onActiveCardChanged = {},
                    onRevealSecrets = {},
                    onHideSecrets = {},
                    onOpenCard = {},
                    onDeleteCard = deleted::add,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Διαγραφή κάρτας").performClick()
        composeRule.onNodeWithTag("card_delete_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(.90f) }
        composeRule.onNodeWithTag("card_delete_slider").performKeyInput { pressKey(Key.Enter) }

        composeRule.waitUntil(timeoutMillis = TimeUnit.SECONDS.toMillis(5)) { deleted == listOf("card-a") }
        composeRule.onNodeWithTag("credit_card_stack_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("credit_card_dot_card-a").assertDoesNotExist()
    }

    @Test
    fun reducedMotion_deleteFinalCard_completesWithoutDecorativeDelay() {
        setAnimatorDurationScale("0")
        val deleted = mutableListOf<String>()

        composeRule.setContent {
            MyFinHubTheme {
                CreditCardStack(
                    cards = testCards(1),
                    secretState = CardSecretUiState.Hidden("card-a"),
                    onActiveCardChanged = {},
                    onRevealSecrets = {},
                    onHideSecrets = {},
                    onOpenCard = {},
                    onDeleteCard = deleted::add,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Διαγραφή κάρτας").performClick()
        composeRule.onNodeWithTag("card_delete_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(1f) }
        composeRule.onNodeWithTag("card_delete_slider").performKeyInput { pressKey(Key.Enter) }

        composeRule.waitUntil(timeoutMillis = TimeUnit.SECONDS.toMillis(2)) { deleted == listOf("card-a") }
        composeRule.onNodeWithTag("credit_card_stack_empty").assertIsDisplayed()
    }

    private fun setAnimatorDurationScale(value: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put global animator_duration_scale $value")
            .close()
    }

    private fun testCards(count: Int): List<MoneyCard> = listOf(
        MoneyCard(
            id = "card-a",
            nickname = "Visa Classic",
            last4 = "1111",
            kind = "Χρεωστική",
            currentBalance = 0.0,
            limit = null,
            vaultState = VaultState.AVAILABLE,
            network = "VISA",
            bankId = "piraeus",
        ),
        MoneyCard(
            id = "card-b",
            nickname = "Premium Midnight",
            last4 = "2222",
            kind = "Χρεωστική",
            currentBalance = 0.0,
            limit = null,
            vaultState = VaultState.AVAILABLE,
            network = "VISA",
            bankId = "revolut",
        ),
        MoneyCard(
            id = "card-c",
            nickname = "Bonus Visa Gold",
            last4 = "3333",
            kind = "Πιστωτική",
            currentBalance = 0.0,
            limit = 2_000.0,
            vaultState = VaultState.AVAILABLE,
            network = "VISA",
            bankId = "alpha",
        ),
    ).take(count)
}
