package app.myfinhub.android.feature.money

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android clipboard with the secure-details Compose screen in foreground. */
@RunWith(AndroidJUnit4::class)
class SensitiveCardClipboardLifecycleTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val clipboard get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private fun hasOurClip() =
        clipboard.primaryClip?.description?.label?.toString()?.startsWith("MyFinHub:sensitive:") == true

    @After fun cleanUp() {
        rule.runOnIdle {
            // Do not leave synthetic PAN/CVV on the emulator clipboard between tests.
            clipboard.setPrimaryClip(ClipData.newPlainText("test-finished", ""))
        }
    }

    @Test fun expiryRemainsScheduledWhenNoComposeCoroutineSurvives() {
        rule.setContent { MyFinHubTheme { androidx.compose.material3.Text("Clipboard test") } }
        rule.runOnIdle {
            assertTrue(SensitiveCardClipboard(context, expiryMillis = 80L).copy("321"))
            assertTrue(hasOurClip())
        }
        rule.waitUntil(4_000) { !hasOurClip() }
        assertFalse(hasOurClip())
    }

    @Test fun screenDisposalImmediatelyClearsCopiedPan() {
        val visible = mutableStateOf(true)
        val state = CardSecretUiState.Revealed("card-1", "4242424242424242", "12/30", "321")
        rule.setContent { MyFinHubTheme {
            if (visible.value) {
                CanonicalCardSecureDetailsScreen(
                    cardId = "card-1", card = null, secretState = state,
                    onReveal = {}, onSaveServerSecrets = { _, _ -> }, onSaveCvv = {},
                    onBack = {},
                )
            } else androidx.compose.material3.Text("Closed")
        } }
        rule.onAllNodesWithText("Αντιγραφή")[0].performClick()
        rule.runOnIdle { assertTrue(hasOurClip()) }
        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        rule.runOnIdle { assertFalse(hasOurClip()) }
    }

    @Test fun lossOfAal2RevealImmediatelyRevokesClipboardCopy() {
        val state = mutableStateOf<CardSecretUiState>(
            CardSecretUiState.Revealed("card-1", "4242424242424242", "12/30", "321")
        )
        rule.setContent { MyFinHubTheme {
            CanonicalCardSecureDetailsScreen(
                cardId = "card-1", card = null, secretState = state.value,
                onReveal = {}, onSaveServerSecrets = { _, _ -> }, onSaveCvv = {},
                onBack = {},
            )
        } }
        rule.onAllNodesWithText("Αντιγραφή")[0].performClick()
        rule.runOnIdle { assertTrue(hasOurClip()) }
        rule.runOnIdle { state.value = CardSecretUiState.AuthRejected }
        rule.waitForIdle()
        rule.runOnIdle { assertFalse(hasOurClip()) }
    }

    @Test fun expirationCannotClearAnotherAppsNewerClipboardValue() {
        rule.setContent { MyFinHubTheme { androidx.compose.material3.Text("Clipboard ownership test") } }
        rule.runOnIdle {
            assertTrue(SensitiveCardClipboard(context, expiryMillis = 70L).copy("321"))
            clipboard.setPrimaryClip(ClipData.newPlainText("external-app", "their text"))
        }
        Thread.sleep(240L)
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals("their text", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            assertEquals("external-app", clipboard.primaryClip?.description?.label?.toString())
        }
    }
}
