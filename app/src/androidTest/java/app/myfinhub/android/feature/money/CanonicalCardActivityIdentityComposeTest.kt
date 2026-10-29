package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Corrupt legacy event IDs must never navigate to a different physical finance event. */
class CanonicalCardActivityIdentityComposeTest {
    @get:Rule val rule = createComposeRule()

    private fun entry(id: String, title: String) = MoneyCardActivity(
        id = id, dateLabel = "2026-10-29", title = title, amount = 30.0,
        kind = MoneyCardActivityKind.PURCHASE,
    )

    private val credit = MoneyCard(
        id = "credit-integrity", nickname = "Πιστωτική", last4 = "1234",
        kind = "Πιστωτική", canonicalKind = "credit",
        currentBalance = 100.0, limit = 1000.0, vaultState = VaultState.LOCKED,
        activity = listOf(
            entry("duplicate", "Αγορά πρώτη"),
            entry("duplicate", "Αγορά δεύτερη"),
            entry("", "Χωρίς ID"),
            entry("safe-purchase", "Αγορά με έγκυρο ID"),
        ),
    )

    @Test fun inlineCreditKeepsAmbiguousRowsVisibleButOpensOnlyUniqueSource() {
        var opened: String? = null
        rule.setContent {
            MyFinHubTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InlineCreditCardWorkspace(
                        card = credit, statements = emptyList(), amountsVisible = false,
                        onPayCard = {}, onAddPurchase = {}, onOpenDetails = {},
                        onOpenActivity = { opened = it },
                    )
                }
            }
        }
        rule.onNodeWithTag("inline_credit_activity_unavailable_0")
            .performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("inline_credit_activity_unavailable_1").assertIsNotEnabled()
        rule.onNodeWithTag("inline_credit_activity_unavailable_2").assertIsNotEnabled()
        rule.onNodeWithTag("inline_credit_activity_safe-purchase")
            .performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals("safe-purchase", opened) }
    }

    @Test fun fullCardDetailFollowsSameFailClosedActivityIdentityPolicy() {
        var opened: String? = null
        rule.setContent {
            MyFinHubTheme {
                CanonicalCardDetailScreen(
                    card = credit, amountsVisibleOverride = false, onBack = {},
                    onOpenActivity = { opened = it },
                )
            }
        }
        rule.onNodeWithTag("card_activity_unavailable_0").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("card_activity_unavailable_1").assertIsNotEnabled()
        rule.onNodeWithTag("card_activity_unavailable_2").assertIsNotEnabled()
        rule.onNodeWithTag("card_activity_safe-purchase")
            .performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals("safe-purchase", opened) }
    }
}
