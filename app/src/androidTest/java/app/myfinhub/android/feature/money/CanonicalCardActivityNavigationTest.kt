package app.myfinhub.android.feature.money

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalCardActivityNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cardLedgerRow_forwardsCanonicalTransactionIdToSharedDetailRoute() {
        var openedId: String? = null
        val card = MoneyCard(
            id = "card-credit",
            nickname = "Πιστωτική",
            last4 = "1881",
            kind = "Πιστωτική",
            currentBalance = 24.0,
            limit = 2_000.0,
            vaultState = VaultState.LOCKED,
            canonicalKind = "credit",
            activity = listOf(
                MoneyCardActivity(
                    id = "event-card-purchase",
                    dateLabel = "12 Σεπ",
                    title = "Αγορά με κάρτα",
                    amount = -24.0,
                    kind = MoneyCardActivityKind.PURCHASE,
                ),
            ),
        )

        composeRule.setContent {
            MyFinHubTheme {
                CanonicalCardDetailScreen(
                    card = card,
                    onBack = {},
                    onOpenActivity = { openedId = it },
                )
            }
        }

        composeRule.onNodeWithTag("card_activity_event-card-purchase")
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertEquals("event-card-purchase", openedId)
        }
    }
}
