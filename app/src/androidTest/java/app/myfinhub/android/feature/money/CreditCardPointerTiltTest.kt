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

private const val MATERIAL_TILT_PIXEL_DELTA_THRESHOLD = 100
private const val CENTRAL_REGION_START_FRACTION = .25f
private const val CENTRAL_REGION_END_FRACTION = .75f

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

        val changedPixels = changedCentralPixelCount(topRight, bottomLeft)
        assertTrue(
            "Moving an already-hovering mouse across the card must alter the pointer-follow transform; " +
                "centralChangedPixels=$changedPixels.",
            changedPixels > MATERIAL_TILT_PIXEL_DELTA_THRESHOLD,
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

        // Compare only the card's central surface. Pointer positions and transformed outer edges
        // remain outside this region, so renderer cursor/edge noise cannot look like card tilt.
        val changedPixels = changedCentralPixelCount(topRight, bottomLeft)
        assertTrue(
            "Reduced motion must keep pointer-position-dependent tilt disabled; " +
                "centralChangedPixels=$changedPixels.",
            changedPixels <= MATERIAL_TILT_PIXEL_DELTA_THRESHOLD,
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

    private fun changedCentralPixelCount(before: ImageBitmap, after: ImageBitmap): Int {
        require(before.width == after.width && before.height == after.height)
        val beforePixels = IntArray(before.width * before.height)
        val afterPixels = IntArray(after.width * after.height)
        before.readPixels(beforePixels)
        after.readPixels(afterPixels)

        val startX = (before.width * CENTRAL_REGION_START_FRACTION).toInt()
        val endX = (before.width * CENTRAL_REGION_END_FRACTION).toInt()
        val startY = (before.height * CENTRAL_REGION_START_FRACTION).toInt()
        val endY = (before.height * CENTRAL_REGION_END_FRACTION).toInt()

        var changed = 0
        for (y in startY until endY) {
            val rowOffset = y * before.width
            for (x in startX until endX) {
                val index = rowOffset + x
                if (beforePixels[index] != afterPixels[index]) changed++
            }
        }
        return changed
    }

    private fun setAnimatorDurationScale(value: String) {
        runShellCommand("settings put global animator_duration_scale $value")
        val expected = value.toFloat()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runShellCommand("settings get global animator_duration_scale").trim().toFloatOrNull() == expected
        }
    }

    private fun runShellCommand(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }
}
