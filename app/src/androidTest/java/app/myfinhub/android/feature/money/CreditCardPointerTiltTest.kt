package app.myfinhub.android.feature.money

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreditCardPointerTiltTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun restoreRunnerMotionPreference() {
        setAnimatorDurationScale("0")
    }

    @Test
    fun mouseMove_tiltsFrontCard_onlyWhenMotionIsEnabled() {
        setAnimatorDurationScale("1")
        renderStack()

        val card = composeRule.onNodeWithTag("credit_card_card-a")
        val before = card.captureToImage()

        card.performMouseInput {
            moveTo(Offset(width * .88f, height * .12f))
        }
        composeRule.waitForIdle()

        val after = card.captureToImage()
        assertTrue(
            "A real Compose mouse-move event must alter the rendered front-card transform.",
            changedPixelCount(before, after) > 100,
        )
    }

    @Test
    fun mouseMove_doesNotTiltWhenReducedMotionIsEnabled() {
        setAnimatorDurationScale("0")
        renderStack()

        val card = composeRule.onNodeWithTag("credit_card_card-a")
        val before = card.captureToImage()

        card.performMouseInput {
            moveTo(Offset(width * .88f, height * .12f))
        }
        composeRule.waitForIdle()

        val after = card.captureToImage()
        assertTrue(
            "Reduced motion must keep pointer-follow tilt disabled.",
            changedPixelCount(before, after) == 0,
        )
    }

    private fun renderStack() {
        composeRule.setContent {
            MyFinHubTheme {
                CreditCardStack(
                    cards = listOf(
                        MoneyCard(
                            id = "card-a",
                            nickname = "Pointer Test",
                            last4 = "1111",
                            kind = "Χρεωστική",
                            currentBalance = 0.0,
                            limit = null,
                            vaultState = VaultState.AVAILABLE,
                            network = "VISA",
                            bankId = "piraeus",
                        ),
                    ),
                    secretState = CardSecretUiState.Hidden("card-a"),
                    onActiveCardChanged = {},
                    onRevealSecrets = {},
                    onHideSecrets = {},
                    onOpenCard = {},
                    onDeleteCard = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun changedPixelCount(before: ImageBitmap, after: ImageBitmap): Int {
        require(before.width == after.width && before.height == after.height)
        val beforePixels = IntArray(before.width * before.height)
        val afterPixels = IntArray(after.width * after.height)
        before.readPixels(beforePixels)
        after.readPixels(afterPixels)
        return beforePixels.indices.count { beforePixels[it] != afterPixels[it] }
    }

    private fun setAnimatorDurationScale(value: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put global animator_duration_scale $value")
            .close()
    }
}
