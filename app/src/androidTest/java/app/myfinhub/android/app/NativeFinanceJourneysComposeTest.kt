package app.myfinhub.android.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.core.data.CanonicalCreditStatementView
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.activity.ActivityAction
import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityKind
import app.myfinhub.android.feature.activity.ActivityUiState
import app.myfinhub.android.feature.home.syntheticHomeUiState
import app.myfinhub.android.feature.money.syntheticMoneyCards
import app.myfinhub.android.feature.money.syntheticMoneyUiState
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryCardOption
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryStatementOption
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import app.myfinhub.android.feature.quickentry.reduceQuickEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Real top-level navigation + production screens, not stand-alone callback mocks.
 * These read-only fixture journeys never persist or mutate financial records.
 */
class NativeFinanceJourneysComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun cardsWalletPaySelectedStatementOpensActualQuickEntryWithExactCanonicalPrefill() {
        val credit = syntheticMoneyCards().last().copy(canonicalKind = "credit")
        val statement = CanonicalCreditStatementView(
            id = "statement-real", cardId = credit.id,
            openDate = "2026-09-01", closeDate = "2026-09-30",
            dueDate = "2026-10-25", boundaryRule = "include-closing-day",
            purchaseIds = emptyList(), paymentIds = emptyList(),
            purchaseTotal = 80.0, paymentTotal = 0.0,
            remaining = 80.0, status = "due",
        )
        val wallet = syntheticMoneyUiState().copy(
            cards = listOf(credit),
            creditStatementsByCard = mapOf(credit.id to listOf(statement)),
            creditStatementSourceIdCounts = mapOf(statement.id to 1),
        )
        var entry by mutableStateOf(
            QuickEntryUiState(
                creditCards = listOf(QuickEntryCardOption(credit.id, "Πιστωτική • 1881")),
                creditStatements = listOf(QuickEntryStatementOption(
                    statement.id, credit.id, "25/10 · 80,00 €", 80.0)),
            ),
        )
        rule.setContent {
            MyFinHubTheme {
                MyFinHubAppContent(
                    homeState = syntheticHomeUiState(),
                    onHomeAction = {},
                    moneyState = wallet,
                    quickEntryState = entry,
                    onQuickEntryAction = { entry = reduceQuickEntry(entry, it) },
                )
            }
        }

        rule.onNodeWithContentDescription("Κάρτες", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("wallet_list")
            .performScrollToNode(hasTestTag("inline_credit_card_workspace"))
        rule.onNodeWithTag("inline_credit_pay_statement_0").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(QuickEntryKind.CARD_PAYMENT, entry.kind)
            assertEquals(credit.id, entry.cardId)
            assertEquals(statement.id, entry.statementId)
            assertEquals(80.0, entry.amount ?: -1.0, 0.001)
        }
        rule.onNodeWithText("Πιστωτική κάρτα").assertExists()
    }

    @Test fun activityLedgerToEditorAndExplicitDeleteConfirmationUsesCurrentSourceId() {
        val row = ActivityItem(
            id = "event-journey", dateLabel = "30 Οκτ",
            kind = ActivityKind.EXPENSE, title = "Σούπερ μάρκετ",
            subtitle = "Αγορές", amount = -35.0,
            accountLabel = "Μετρητά", category = "Τρόφιμα",
            rawDate = "2026-10-30",
        )
        var action: ActivityAction? = null
        rule.setContent {
            MyFinHubTheme {
                MyFinHubAppContent(
                    homeState = syntheticHomeUiState(),
                    onHomeAction = {},
                    activityState = ActivityUiState(items = listOf(row)),
                    onActivityAction = { action = it },
                )
            }
        }
        rule.onNodeWithContentDescription("Κινήσεις", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Σούπερ μάρκετ").performClick()
        rule.onNodeWithText("Λεπτομέρειες κίνησης").assertIsDisplayed()
        rule.onNodeWithText("Επεξεργασία").performClick()
        rule.onNodeWithText("Επεξεργασία κίνησης").assertIsDisplayed()
        rule.onNodeWithContentDescription("Πίσω").performClick()
        rule.onNodeWithText("Λεπτομέρειες κίνησης").assertIsDisplayed()

        rule.onNodeWithText("Περισσότερα").performClick()
        rule.onNodeWithText("Διαγραφή κίνησης").performClick()
        rule.onNodeWithText("Διαγραφή κίνησης;").assertIsDisplayed()
        rule.runOnIdle { assertNull(action) }
        rule.onNodeWithText("Ακύρωση").performClick()
        rule.runOnIdle { assertNull(action) }
        rule.onNodeWithText("Περισσότερα").performClick()
        rule.onNodeWithText("Διαγραφή κίνησης").performClick()
        rule.onNodeWithText("Διαγραφή").performClick()
        rule.runOnIdle { assertEquals(ActivityAction.Delete(row.id), action) }
    }
}
