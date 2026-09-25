package app.myfinhub.android.feature.money

import android.os.ParcelFileDescriptor
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
        originalAnimatorDurationScale = readAnimatorDurationScale()
        setAnimatorDurationScale(0f)
    }

    @After
    fun restoreMotionPreference() {
        originalAnimatorDurationScale?.let(::setAnimatorDurationScale)
    }

    @Test
    fun fullValuesAreVisibleWithoutReveal_andVerticalSwipeRestacksByStableId() {
        val secretState = mutableStateOf<CardSecretUiState>(CardSecretUiState.Hidden())
        var activeCardId: String? = null
        val cards = testCards(3)

        composeRule.setContent {
            MyFinHubTheme {
                CreditCardStack(
                    cards = cards,
                    secretState = secretState.value,
                    onActiveCardChanged = { cardId ->
                        activeCardId = cardId
                        secretState.value = when (cardId) {
                            "card-a" -> CardSecretUiState.Revealed(cardId, "4242424242421111", "06/30", "418")
                            "card-b" -> CardSecretUiState.Revealed(cardId, "5555444433332222", "07/31", "729")
                            else -> CardSecretUiState.Revealed(requireNotNull(cardId), "4000000000003333", "08/32", "610")
                        }
                    },
                    onOpenCard = {},
                    onDeleteCard = {},
                )
            }
        }

        composeRule.waitUntil { activeCardId == "card-a" }
        composeRule.onNodeWithText("4242 4242 4242 1111").assertIsDisplayed()
        composeRule.onNodeWithText("06/30").assertIsDisplayed()
        composeRule.onNodeWithText("418").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Εμφάνιση στοιχείων").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Απόκρυψη στοιχείων").assertDoesNotExist()

        composeRule.onNodeWithTag("credit_card_card-a").performTouchInput {
            val verticalInset = (bottom - top) * .18f
            swipeUp(startY = bottom - verticalInset, endY = top + verticalInset, durationMillis = 400L)
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = TimeUnit.SECONDS.toMillis(10)) { activeCardId == "card-b" }

        composeRule.onNodeWithText("5555 4444 3333 2222").assertIsDisplayed()
        composeRule.onNodeWithText("07/31").assertIsDisplayed()
        composeRule.onNodeWithText("729").assertIsDisplayed()
    }

    @Test
    fun paginationDot_selectsCardByStableId() {
        var activeCardId: String? = null
        composeRule.setContent {
            MyFinHubTheme {
                CreditCardStack(
                    cards = testCards(3),
                    secretState = CardSecretUiState.Revealed("card-a", "4242424242421111", "06/30", "418"),
                    onActiveCardChanged = { activeCardId = it },
                    onOpenCard = {},
                    onDeleteCard = {},
                )
            }
        }

        composeRule.waitUntil { activeCardId == "card-a" }
        composeRule.onNodeWithTag("credit_card_dot_card-c").performClick()
        composeRule.waitUntil(timeoutMillis = TimeUnit.SECONDS.toMillis(5)) { activeCardId == "card-c" }
        composeRule.onNodeWithContentDescription("Κάρτα 3 από 3: Bonus Visa Gold, ενεργή").assertIsDisplayed()
    }

    @Test
    fun deleteCancel_restoresNormalCardState() {
        composeRule.setContent {
            MyFinHubTheme {
                CreditCardStack(
                    cards = testCards(1),
                    secretState = CardSecretUiState.Revealed("card-a", "4242424242421111", "06/30", "418"),
                    onActiveCardChanged = {},
                    onOpenCard = {},
                    onDeleteCard = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Διαγραφή κάρτας").performClick()
        composeRule.onNodeWithTag("card_delete_slider").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ακύρωση").performClick()
        composeRule.onNodeWithTag("card_delete_slider").assertDoesNotExist()
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
                    secretState = CardSecretUiState.Revealed("card-a", "4242424242421111", "06/30", "418"),
                    onActiveCardChanged = {},
                    onOpenCard = {},
                    onDeleteCard = { id ->
                        deleted += id
                        cards.value = cards.value.filterNot { it.id == id }
                    },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Διαγραφή κάρτας").performClick()
        val slider = composeRule.onNodeWithTag("card_delete_slider")
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(.91f) }
        slider.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        slider.performKeyInput { pressKey(Key.Enter) }

        composeRule.waitUntil(timeoutMillis = TimeUnit.SECONDS.toMillis(5)) {
            deleted == listOf("card-a") && cards.value.isEmpty()
        }
        composeRule.onNodeWithTag("credit_card_stack_empty").assertIsDisplayed()
    }

    private fun readAnimatorDurationScale(): Float =
        runShellCommand("settings get global animator_duration_scale").trim().toFloatOrNull() ?: 1f

    private fun setAnimatorDurationScale(value: Float) {
        runShellCommand("settings put global animator_duration_scale $value")
        composeRule.waitUntil(timeoutMillis = 5_000) { readAnimatorDurationScale() == value }
    }

    private fun runShellCommand(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }

    private fun testCards(count: Int): List<MoneyCard> = listOf(
        MoneyCard("card-a", "Visa Classic", "1111", "Χρεωστική", 0.0, null, VaultState.AVAILABLE, "VISA", "piraeus"),
        MoneyCard("card-b", "Premium Midnight", "2222", "Χρεωστική", 0.0, null, VaultState.AVAILABLE, "VISA", "revolut"),
        MoneyCard("card-c", "Bonus Visa Gold", "3333", "Πιστωτική", 0.0, 2_000.0, VaultState.AVAILABLE, "VISA", "alpha"),
    ).take(count)
}
