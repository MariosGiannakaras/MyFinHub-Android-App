package app.myfinhub.android.feature.money

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android 14+ clipboard reads are focus-gated in hosted instrumented emulators.
 * Exercise the real sensitive controller, Compose screen and main-looper timer
 * with an observable in-memory clipboard port; never relax ownership assertions.
 */
@RunWith(AndroidJUnit4::class)
class SensitiveCardClipboardLifecycleTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private class InMemorySensitiveClipboard : SensitiveClipboardPort {
        @Volatile var clip: Pair<String, String>? = null
        @Volatile var clearAttempts: Int = 0
        override fun copy(marker: String, value: String): Boolean {
            clip = marker to value
            return true
        }
        override fun clearIfUnchanged(marker: String, value: String) {
            clearAttempts += 1
            if (clip == (marker to value)) clip = null
        }
        fun otherAppCopy(value: String) {
            clip = "external-app" to value
        }
    }

    @Test fun expiryRemainsScheduledWhenNoComposeCoroutineSurvives() {
        val store = InMemorySensitiveClipboard()
        val controller = SensitiveCardClipboard(context, expiryMillis = 120L, port = store)
        rule.setContent { MyFinHubTheme { androidx.compose.material3.Text("Clipboard timer") } }
        rule.runOnIdle {
            assertTrue(controller.copy("321"))
            assertNotNull(store.clip)
            assertTrue(store.clip!!.first.startsWith("MyFinHub:sensitive:"))
            assertEquals("321", store.clip!!.second)
        }
        rule.waitUntil(4_000) { store.clip == null }
        rule.runOnIdle { assertNull(store.clip) }
    }

    @Test fun screenDisposalImmediatelyClearsCopiedPan() {
        val store = InMemorySensitiveClipboard()
        val controller = SensitiveCardClipboard(context, port = store)
        val visible = mutableStateOf(true)
        val revealed = CardSecretUiState.Revealed("card-1", "4242424242424242", "12/30", "321")
        rule.setContent { MyFinHubTheme {
            CompositionLocalProvider(LocalSensitiveClipboardController provides controller) {
                if (visible.value) {
                    CanonicalCardSecureDetailsScreen(
                        cardId = "card-1", card = null, secretState = revealed,
                        onReveal = {}, onSaveServerSecrets = { _, _ -> },
                        onSaveCvv = {}, onBack = {},
                    )
                } else androidx.compose.material3.Text("Closed")
            }
        } }
        rule.onAllNodesWithText("Αντιγραφή")[0].performScrollTo().performClick()
        rule.runOnIdle {
            assertNotNull(store.clip)
            assertEquals("4242424242424242", store.clip!!.second)
            assertFalse(store.clip!!.first.contains("card-1"))
        }
        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        rule.runOnIdle { assertNull(store.clip) }
    }

    @Test fun lossOfAal2RevealImmediatelyRevokesClipboardCopy() {
        val store = InMemorySensitiveClipboard()
        val controller = SensitiveCardClipboard(context, port = store)
        val state = mutableStateOf<CardSecretUiState>(
            CardSecretUiState.Revealed("card-1", "4242424242424242", "12/30", "321")
        )
        rule.setContent { MyFinHubTheme {
            CompositionLocalProvider(LocalSensitiveClipboardController provides controller) {
                CanonicalCardSecureDetailsScreen(
                    cardId = "card-1", card = null, secretState = state.value,
                    onReveal = {}, onSaveServerSecrets = { _, _ -> }, onSaveCvv = {},
                    onBack = {},
                )
            }
        } }
        rule.onAllNodesWithText("Αντιγραφή")[0].performScrollTo().performClick()
        rule.runOnIdle { assertNotNull(store.clip) }
        rule.runOnIdle { state.value = CardSecretUiState.AuthRejected }
        rule.waitForIdle()
        rule.runOnIdle { assertNull(store.clip) }
    }

    @Test fun expirationCannotClearAnotherAppsNewerClipboardValue() {
        val store = InMemorySensitiveClipboard()
        val controller = SensitiveCardClipboard(context, expiryMillis = 120L, port = store)
        rule.setContent { MyFinHubTheme { androidx.compose.material3.Text("Clipboard ownership") } }
        rule.runOnIdle {
            assertTrue(controller.copy("321"))
            assertNotNull(store.clip)
            store.otherAppCopy("their text")
        }
        rule.waitUntil(4_000) { store.clearAttempts >= 1 }
        rule.runOnIdle {
            assertEquals("external-app" to "their text", store.clip)
            controller.clear()
            assertEquals("external-app" to "their text", store.clip)
        }
    }

}
