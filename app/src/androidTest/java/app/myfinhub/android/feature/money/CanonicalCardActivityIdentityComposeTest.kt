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
    @Test fun crossLedgerCollisionDisablesInlineCardRouteButKeepsUniqueRowClickable() {
        val selected = credit.copy(activity = listOf(
            entry("cross-card", "Υπάρχει και σε άλλη κάρτα"),
            entry("valid-card", "Έγκυρη κίνηση"),
        ))
        var opened: String? = null
        rule.setContent {
            MyFinHubTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InlineCreditCardWorkspace(
                        card = selected, statements = emptyList(), amountsVisible = false,
                        onPayCard = {}, onAddPurchase = {}, onOpenDetails = {},
                        onOpenActivity = { opened = it },
                        fullActivityIdCounts = mapOf("cross-card" to 2, "valid-card" to 1),
                    )
                }
            }
        }
        rule.onNodeWithTag("inline_credit_activity_unavailable_0")
            .performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("inline_credit_activity_valid-card")
            .performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals("valid-card", opened) }
    }

    @Test fun fullCardDetailDisablesForeignDuplicateAndMissingGlobalEvent() {
        val selected = credit.copy(activity = listOf(
            entry("cross-card", "Σύγκρουση"), entry("missing", "Χωρίς canonical εγγραφή"),
            entry("valid-card", "Έγκυρη κίνηση"),
        ))
        var opened: String? = null
        rule.setContent {
            MyFinHubTheme {
                CanonicalCardDetailScreen(
                    card = selected, amountsVisibleOverride = false, onBack = {},
                    fullActivityIdCounts = mapOf("cross-card" to 2, "valid-card" to 1),
                    onOpenActivity = { opened = it },
                )
            }
        }
        rule.onNodeWithTag("card_activity_unavailable_0").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("card_activity_unavailable_1").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("card_activity_valid-card")
            .performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals("valid-card", opened) }
    }

}
