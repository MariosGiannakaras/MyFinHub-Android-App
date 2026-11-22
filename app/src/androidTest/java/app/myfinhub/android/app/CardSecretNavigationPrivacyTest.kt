package app.myfinhub.android.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.home.syntheticHomeUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CardSecretNavigationPrivacyTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun leavingCardsPrimaryDestinationRevokesVaultPresentationOnce() {
        var redactions = 0
        composeRule.setContent {
            val navigationOwner = rememberNavigationEventDispatcherOwner(parent = null)
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides navigationOwner,
            ) {
                MyFinHubTheme {
                    MyFinHubAppContent(
                        homeState = syntheticHomeUiState(),
                        onHomeAction = {},
                        legacyTestInitialDestination = TopLevelDestination.MONEY,
                        onHideCardSecrets = { redactions += 1 },
                    )
                }
            }
        }

        composeRule.runOnIdle { assertEquals(0, redactions) }
        composeRule.onNodeWithContentDescription("Αρχική").performClick()
        composeRule.runOnIdle { assertEquals(1, redactions) }

        // Returning to the wallet is not a revoke; it may initiate an ordinary
        // authorized card-face read. Other roots may not retain its PAN/CVV.
        composeRule.onNodeWithContentDescription("Κάρτες").performClick()
        composeRule.runOnIdle { assertEquals(1, redactions) }
        composeRule.onNodeWithContentDescription("Κινήσεις").performClick()
        composeRule.runOnIdle { assertEquals(2, redactions) }
    }
}
