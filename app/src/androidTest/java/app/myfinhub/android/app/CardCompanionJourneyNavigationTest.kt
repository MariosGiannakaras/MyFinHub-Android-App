package app.myfinhub.android.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.home.syntheticHomeUiState
import app.myfinhub.android.feature.money.MoneyCard
import app.myfinhub.android.feature.money.MoneyUiState
import app.myfinhub.android.feature.money.VaultState
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import app.myfinhub.android.feature.quickentry.reduceQuickEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Actual native navigation: selected card -> detail -> purchase draft -> guarded Back -> Cards. */
class CardCompanionJourneyNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedCreditCardPurchasePreservesContextAndToolbarBackGuardsDraft() {
        val debit = MoneyCard(
            id = "debit", nickname = "Χρεωστική καθημερινή", last4 = "4242",
            kind = "Χρεωστική", currentBalance = 0.0, vaultState = VaultState.LOCKED,
            canonicalKind = "debit",
        )
        val credit = MoneyCard(
            id = "credit", nickname = "Πιστωτική ταξιδιών", last4 = "1881",
            kind = "Πιστωτική", currentBalance = 120.0, limit = 1000.0,
            vaultState = VaultState.LOCKED, canonicalKind = "credit",
        )
        var draft by mutableStateOf(QuickEntryUiState())
        compose.setContent {
            MyFinHubTheme {
                MyFinHubAppContent(
                    homeState = syntheticHomeUiState(),
                    onHomeAction = {},
                    moneyState = MoneyUiState(cards = listOf(debit, credit)),
                    quickEntryState = draft,
                    onQuickEntryAction = { draft = reduceQuickEntry(draft, it) },
                )
            }
        }

        compose.onNodeWithText("Κάρτες").performClick()
        compose.onNodeWithTag("credit_card_dot_credit").performClick()
        compose.onNodeWithTag("credit_card_credit").performClick()
        compose.onNodeWithText("Πιστωτική ταξιδιών").assertIsDisplayed()
        compose.onNodeWithTag("card_switcher").assertDoesNotExist()
        compose.onNodeWithText("Αλλαγή κάρτας").assertDoesNotExist()

        compose.onNodeWithText("Καταχώριση αγοράς").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(QuickEntryKind.CARD_PURCHASE, draft.kind)
            assertEquals("credit", draft.cardId)
        }
        compose.onNodeWithContentDescription("Πίσω").performClick()
        compose.onNodeWithText("Απόρριψη νέας κίνησης;").assertIsDisplayed()
        compose.onNodeWithText("Συνέχεια").performClick()
        compose.runOnIdle { assertEquals("credit", draft.cardId) }
        compose.onNodeWithContentDescription("Πίσω").performClick()
        compose.onNodeWithText("Απόρριψη").performClick()

        compose.onNodeWithText("Πιστωτική ταξιδιών").assertIsDisplayed()
        compose.onNodeWithContentDescription("Πίσω").performClick()
        compose.onNodeWithTag("card_companion_summary").assertExists()
        compose.onNodeWithText("Πιστωτική ταξιδιών").performScrollTo().assertIsDisplayed()
    }
}
