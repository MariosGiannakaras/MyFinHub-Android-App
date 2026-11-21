package app.myfinhub.android.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.home.syntheticHomeUiState
import app.myfinhub.android.feature.money.MoneyAccount
import app.myfinhub.android.feature.money.MoneyUiState
import org.junit.Rule
import org.junit.Test

class AccountRouteIdentityTest {
    @get:Rule val composeRule = createComposeRule()

    private val bank = MoneyAccount(
        id = "revolut-main",
        name = "Καθημερινός",
        balance = 250.0,
        kind = "Τράπεζα",
        canonicalKind = "bank",
    )

    @Test
    fun openedAccountBecomesUnavailableOnSourceCollisionWithoutShowingActionsOrIban() {
        val money = mutableStateOf(MoneyUiState(
            accounts = listOf(bank),
            accountSourceIdCounts = mapOf("revolut-main" to 1),
        ))
        composeRule.setContent {
            MyFinHubTheme {
                MyFinHubAppContent(
                    homeState = syntheticHomeUiState(),
                    moneyState = money.value,
                    accountMetadataLoaded = true,
                    onSaveAccountIban = { _, _ -> error("Stale account must never write IBAN") },
                )
            }
        }

        composeRule.onNodeWithTag("home_list").performScrollToNode(
            hasContentDescription("Καθημερινός", substring = true),
        )
        composeRule.onNode(
            hasContentDescription("Καθημερινός", substring = true) and hasClickAction(),
        ).performClick()
        composeRule.onNodeWithText("Νέα κίνηση από αυτόν τον λογαριασμό")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Προσθήκη IBAN").assertIsDisplayed()

        // The route stays open while the canonical account catalog refreshes.
        composeRule.runOnIdle {
            money.value = money.value.copy(
                accountSourceIdCounts = mapOf("revolut-main" to 2),
            )
        }
        composeRule.onNodeWithText("Ο λογαριασμός δεν είναι πλέον διαθέσιμος.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Νέα κίνηση από αυτόν τον λογαριασμό")
            .assertDoesNotExist()
        composeRule.onNodeWithText("Προσθήκη IBAN").assertDoesNotExist()
    }

    @Test
    fun ambiguousAccountCannotBeOpenedFromHome() {
        val money = MoneyUiState(
            accounts = listOf(bank),
            accountSourceIdCounts = mapOf("revolut-main" to 2),
        )
        composeRule.setContent {
            MyFinHubTheme {
                MyFinHubAppContent(
                    homeState = syntheticHomeUiState(),
                    moneyState = money,
                )
            }
        }
        composeRule.onNodeWithTag("home_list").performScrollToNode(
            hasContentDescription("Καθημερινός", substring = true),
        )
        composeRule.onNode(
            hasContentDescription("Καθημερινός", substring = true) and hasClickAction(),
        ).performClick()
        composeRule.onNodeWithText("Κύριοι λογαριασμοί").assertIsDisplayed()
        composeRule.onNodeWithText("Νέα κίνηση από αυτόν τον λογαριασμό")
            .assertDoesNotExist()
    }
}
