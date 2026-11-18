package app.myfinhub.android.app

import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.home.syntheticHomeUiState
import app.myfinhub.android.feature.money.CardSecretUiState
import app.myfinhub.android.feature.money.findSecureCardHostActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Verifies the real app-level capture guard, independent of the outgoing Wallet subtree. */
class CardSecretsNavigationProtectionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun revealedVaultKeepsWindowSecureAcrossNavigationAndBriefHideTransition() {
        var secrets by mutableStateOf<CardSecretUiState>(CardSecretUiState.Hidden())
        var hostWindow: Window? = null
        composeRule.setContent {
            val host = LocalContext.current.findSecureCardHostActivity()
            SideEffect { hostWindow = host?.window }
            MyFinHubTheme {
                MyFinHubAppContent(
                    homeState = syntheticHomeUiState(),
                    onHomeAction = {},
                    cardSecretState = secrets,
                )
            }
        }
        composeRule.runOnIdle { assertNotNull(hostWindow) }
        val initiallySecure = composeRule.runOnIdle { isSecure(hostWindow) }

        // The Home route is still active: security cannot depend on an active Card leaf.
        composeRule.runOnIdle {
            secrets = CardSecretUiState.Revealed(
                cardId = "synthetic-card",
                pan = "4242424242424242",
                expiry = "12/30",
                cvv = "123",
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { isSecure(hostWindow) }
        composeRule.runOnIdle { secrets = CardSecretUiState.Hidden() }
        // The old card scene may still be visible during the exit animation.
        composeRule.runOnIdle { assertTrue(isSecure(hostWindow)) }
        composeRule.waitUntil(timeoutMillis = 5_000) { isSecure(hostWindow) == initiallySecure }
        composeRule.runOnIdle { assertEquals(initiallySecure, isSecure(hostWindow)) }
    }

    private fun isSecure(window: Window?): Boolean =
        window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
}
