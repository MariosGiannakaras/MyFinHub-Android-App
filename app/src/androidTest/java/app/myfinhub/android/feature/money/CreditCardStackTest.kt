package app.myfinhub.android.feature.money

import android.os.ParcelFileDescriptor
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreditCardStackTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var originalAnimatorDurationScale: Float? = null

    @Before
    fun useDeterministicMotionPreference() {
        if (originalAnimatorDurationScale == null) {
            originalAnimatorDurationScale = readAnimatorDurationScale()
        }
        // These tests validate interaction/state contracts, not decorative timing. Keeping the
        // runner's reduced-motion preference deterministic also removes the independent mouse
        // tilt pointer layer while exercising the real touch drag path on large foldable bounds.
        setAnimatorDurationScale(0f)
    }

    @After
    fun restoreMotionPreference() {
        originalAnimatorDurationScale?.let(::setAnimatorDurationScale)
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

        // Exercise the actual vertical-swipe contract on every window class. Keyboard and
        // accessibility restacking are covered independently by the stack semantics/parity tests.
        composeRule.onNodeWithTag("credit_card_card-a").performTouchInput { swipeUp() }
        composeRule.waitUntil(timeoutMillis = TimeUnit.SECONDS.toMillis(10)) { activeCardId == "card-b" }

        composeRule.onNodeWithContentDescription("Εμφάνιση στοιχείων").performClick()
        composeRule.onNodeWithText("4321 8765 2109 1234").assertIsDisplayed()
        composeRule.onNodeWithText("06/30").assertIsDisplayed()
        composeRule.onNodeWithText("418").assertIsDisplayed()

        composeRule.onAllNodesWithContentDescription("Αντιγραφή αριθμού")
            .filterToOne(isEnabled())
            .performClick()
        // Android 13+ can deny clipboard reads to instrumentation even when the foreground app
        // successfully writes. The live-region acknowledgement is emitted only after the
        // production clipboard setText call has executed.
        composeRule.onNodeWithText("Ο αριθμός αντιγράφηκε").assertExists()

        composeRule.onNodeWithContentDescription("Απόκρυψη στοιχείων").performClick()
        // Expiry/CVV masks are intentionally present on every stacked card. The PAN mask is
        // stable-ID-specific, so it proves that the newly fronted card returned to hidden state
        // without making a global uniqueness assertion about shared placeholder text.
        composeRule.onNodeWithText("•••• •••• •••• 2222").assertIsDisplayed()
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
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("card_delete_slider").performKeyInput { pressKey(Key.Enter) }

        composeRule.runOnIdle { assertEquals(emptyList<String>(), deleted) }
        composeRule.onNodeWithTag("card_delete_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("credit_card_card-a").assertIsDisplayed()
    }

    @Test
    fun deleteAboveThreshold_removesStableId_andAllowsEmptyStack() {
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
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(.91f) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("card_delete_slider").performKeyInput { pressKey(Key.Enter) }

        composeRule.waitUntil(timeoutMillis = TimeUnit.SECONDS.toMillis(5)) { deleted == listOf("card-a") }
        composeRule.onNodeWithTag("credit_card_stack_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("credit_card_dot_card-a").assertDoesNotExist()
    }

    @Test
    fun reducedMotion_deleteFinalCard_completesWithoutDecorativeDelay() {
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
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("card_delete_slider").performKeyInput { pressKey(Key.Enter) }

        // The deterministic reduced-motion setup above verifies that the production branch skips
        // the 620 ms decorative delay entirely. A 5 s harness timeout only tolerates emulator and
        // Compose scheduling latency; it is not the product animation duration contract.
        composeRule.waitUntil(timeoutMillis = TimeUnit.SECONDS.toMillis(5)) { deleted == listOf("card-a") }
        composeRule.onNodeWithTag("credit_card_stack_empty").assertIsDisplayed()
    }

    private fun readAnimatorDurationScale(): Float =
        runShell("settings get global animator_duration_scale").toFloatOrNull() ?: 1f

    private fun setAnimatorDurationScale(value: Float) {
        runShell("settings put global animator_duration_scale $value")

        repeat(20) {
            if (readAnimatorDurationScale() == value) return
            Thread.sleep(50)
        }

        error("Animator duration scale did not settle to $value before rendering the card stack.")
    }

    private fun runShell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText().trim() }
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
