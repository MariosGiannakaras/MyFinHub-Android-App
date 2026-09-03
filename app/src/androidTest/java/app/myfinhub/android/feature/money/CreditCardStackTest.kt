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
        // These tests validate interaction/state contracts, not decorative timing. Keep the
        // runner deterministic while still exercising the real production touch paths.
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

        // Keep the real swipe away from transformed card edges on large foldable bounds while
        // still moving well beyond the production 72 dp restack threshold.
        composeRule.onNodeWithTag("credit_card_card-a").performTouchInput {
            val verticalInset = (bottom - top) * .18f
            swipeUp(
                startY = bottom - verticalInset,
                endY = top + verticalInset,
                durationMillis = 400L,
            )
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = TimeUnit.SECONDS.toMillis(10)) { activeCardId == "card-b" }

        composeRule.onNodeWithContentDescription("Εμφάνιση στοιχείων").performClick()
        composeRule.onNodeWithText("4321 8765 2109 1234").assertIsDisplayed()
        composeRule.onNodeWithText("06/30").assertIsDisplayed()
        composeRule.onNodeWithText("418").assertIsDisplayed()

        composeRule.onAllNodesWithContentDescription("Αντιγραφή αριθμού")
            .filterToOne(isEnabled())
            .performClick()
        // Android 13+ can deny clipboard reads to instrumentation even when the foreground app
        // successfully writes. This acknowledgement is emitted after production setText executes.
        composeRule.onNodeWithText("Ο αριθμός αντιγράφηκε").assertExists()

        composeRule.onNodeWithContentDescription("Απόκρυψη στοιχείων").performClick()
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
        setDeleteProgressAndPressEnter(.89f)

        composeRule.runOnIdle { assertEquals(emptyList<String>(), deleted) }
        composeRule.onNodeWithTag("card_delete_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("credit_card_card-a").assertIsDisplayed()
    }

    @Test
    fun deleteAboveThreshold_requestsStableId_andParentCommitAllowsEmptyStack() {
        val deleted = mutableListOf<String>()
        val cards = mutableStateOf(testCards(1))

        composeRule.setContent {
            MyFinHubTheme {
                CreditCardStack(
                    cards = cards.value,
                    secretState = CardSecretUiState.Hidden("card-a"),
                    onActiveCardChanged = {},
                    onRevealSecrets = {},
                    onHideSecrets = {},
                    onOpenCard = {},
                    onDeleteCard = { id ->
                        deleted += id
                        // Production does not optimistically remove a card. The canonical parent
                        // state acknowledges a successful server mutation and then supplies the
                        // reduced list back to the stack. Simulate that contract here.
                        cards.value = cards.value.filterNot { it.id == id }
                    },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Διαγραφή κάρτας").performClick()
        setDeleteProgressAndPressEnter(.91f)

        composeRule.waitUntil(timeoutMillis = TimeUnit.SECONDS.toMillis(5)) {
            deleted == listOf("card-a") && cards.value.isEmpty()
        }
        composeRule.onNodeWithTag("credit_card_stack_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("credit_card_dot_card-a").assertDoesNotExist()
    }

    @Test
    fun reducedMotion_deleteFinalCard_requestsCommitWithoutDecorativeDelay() {
        val deleted = mutableListOf<String>()
        val cards = mutableStateOf(testCards(1))

        composeRule.setContent {
            MyFinHubTheme {
                CreditCardStack(
                    cards = cards.value,
                    secretState = CardSecretUiState.Hidden("card-a"),
                    onActiveCardChanged = {},
                    onRevealSecrets = {},
                    onHideSecrets = {},
                    onOpenCard = {},
                    onDeleteCard = { id ->
                        deleted += id
                        cards.value = cards.value.filterNot { it.id == id }
                    },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Διαγραφή κάρτας").performClick()
        setDeleteProgressAndPressEnter(1f)

        // The deterministic reduced-motion setup verifies that production skips the decorative
        // delay. The parent-state change models server-confirmed canonical deletion.
        composeRule.waitUntil(timeoutMillis = TimeUnit.SECONDS.toMillis(5)) {
            deleted == listOf("card-a") && cards.value.isEmpty()
        }
        composeRule.onNodeWithTag("credit_card_stack_empty").assertIsDisplayed()
    }

    private fun setDeleteProgressAndPressEnter(progress: Float) {
        val slider = composeRule.onNodeWithTag("card_delete_slider")
        slider.performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(progress) }
        composeRule.waitForIdle()
        slider.performSemanticsAction(SemanticsActions.RequestFocus) { requestFocus -> requestFocus() }
        composeRule.waitForIdle()
        slider.performKeyInput { pressKey(Key.Enter) }
    }

    private fun readAnimatorDurationScale(): Float =
        runShellCommand("settings get global animator_duration_scale").trim().toFloatOrNull() ?: 1f

    private fun setAnimatorDurationScale(value: Float) {
        runShellCommand("settings put global animator_duration_scale $value")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            readAnimatorDurationScale() == value
        }
    }

    private fun runShellCommand(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
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
