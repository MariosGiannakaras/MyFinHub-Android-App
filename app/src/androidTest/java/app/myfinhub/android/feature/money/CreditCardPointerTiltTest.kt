package app.myfinhub.android.feature.money

import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
        val stack = composeRule.onNodeWithTag("credit_card_stack")
        card.performMouseInput {
            moveTo(Offset(width * .88f, height * .12f))
        }
        composeRule.waitForIdle()
        val topRight = stack.captureToImage()

        card.performMouseInput {
            moveTo(Offset(width * .12f, height * .88f))
        }
        composeRule.waitForIdle()
        val bottomLeft = stack.captureToImage()

        assertTrue(
            "Moving an already-hovering mouse across the card must alter the pointer-follow transform.",
            changedPixelCount(topRight, bottomLeft) > 100,
        )
    }

    @Test
    fun mouseMove_doesNotTiltWhenReducedMotionIsEnabled() {
        setAnimatorDurationScale("0")
        renderStack()

        val card = composeRule.onNodeWithTag("credit_card_card-a")
        val stack = composeRule.onNodeWithTag("credit_card_stack")
        card.performMouseInput {
            moveTo(Offset(width * .88f, height * .12f))
        }
        composeRule.waitForIdle()
        val topRight = stack.captureToImage()

        card.performMouseInput {
            moveTo(Offset(width * .12f, height * .88f))
        }
        composeRule.waitForIdle()
        val bottomLeft = stack.captureToImage()

        // Both captures are already in the same hovered state. Any material pixel delta would
        // therefore come from pointer-position-dependent tilt, which reduced motion must disable.
        assertTrue(
            "Reduced motion must keep pointer-position-dependent tilt disabled.",
            changedPixelCount(topRight, bottomLeft) == 0,
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
        val expected = value.toFloat()
        runShell("settings put global animator_duration_scale $value")

        repeat(20) {
            val actual = runShell("settings get global animator_duration_scale").toFloatOrNull()
            if (actual == expected) return
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
}
