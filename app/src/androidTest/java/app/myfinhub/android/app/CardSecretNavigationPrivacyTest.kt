package app.myfinhub.android.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performClick
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.home.syntheticHomeUiState
import app.myfinhub.android.feature.money.CardSecretUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CardSecretNavigationPrivacyTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun leavingCardsRevokesRevealedAndPendingVaultReadsIncludingLateSameRouteResponses() {
        var redactions = 0
        var secrets by mutableStateOf<CardSecretUiState>(CardSecretUiState.Revealed(
            cardId = "card-1",
            pan = "4242424242424242",
            expiry = "12/30",
            cvv = "123",
        ))
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
                        cardSecretState = secrets,
                        onHideCardSecrets = {
                            redactions += 1
                            secrets = CardSecretUiState.Hidden()
                        },
                    )
                }
            }
        }

        composeRule.runOnIdle { assertEquals(0, redactions) }
        composeRule.onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription("Αρχική")),
            useUnmergedTree = true,
        ).performClick()
        composeRule.runOnIdle {
            assertEquals(1, redactions)
            assertEquals(CardSecretUiState.Hidden(), secrets)
        }

        // A token refresh may start a late GET without changing the current
        // Home route. It must be revoked again rather than left in memory.
        composeRule.runOnIdle {
            secrets = CardSecretUiState.Revealed("card-1", "4242424242424242", "12/30", "123")
        }
        composeRule.runOnIdle {
            assertEquals(2, redactions)
            assertEquals(CardSecretUiState.Hidden(), secrets)
        }

        composeRule.onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription("Κάρτες")),
            useUnmergedTree = true,
        ).performClick()
        composeRule.runOnIdle { assertEquals(2, redactions) }
        composeRule.runOnIdle { secrets = CardSecretUiState.Loading("card-1") }
        composeRule.onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription("Κινήσεις")),
            useUnmergedTree = true,
        ).performClick()
        composeRule.runOnIdle {
            assertEquals(3, redactions)
            assertEquals(CardSecretUiState.Hidden(), secrets)
        }
    }
}
