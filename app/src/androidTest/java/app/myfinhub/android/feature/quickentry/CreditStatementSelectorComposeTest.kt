package app.myfinhub.android.feature.quickentry

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real native credit statement picker, not a synthetic stand-in dialog. */
@RunWith(AndroidJUnit4::class)
class CreditStatementSelectorComposeTest {
    @get:Rule val rule = createComposeRule()

    private val statement = QuickEntryStatementOption(
        id = "statement-oct",
        cardId = "card-credit",
        label = "2026-10-25 · 35,00 €",
        remaining = 35.0,
    )

    @Test fun selectedStatementIsVisibleAndUserCanChooseUnlinkedPayment() {
        var action: QuickEntryAction? = null
        rule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(
                    state = QuickEntryUiState(
                        kind = QuickEntryKind.CARD_PAYMENT,
                        amountText = "15,00",
                        dateText = "2026-10-15",
                        cardId = "card-credit",
                        statementId = statement.id,
                        creditStatements = listOf(statement),
                        dirty = true,
                    ),
                    onAction = { action = it },
                    onBack = {},
                )
            }
        }
        rule.onNodeWithText("Δήλωση πιστωτικής (προαιρετικά)").fetchSemanticsNode()
        rule.onNodeWithText(statement.label).performScrollTo().performClick()
        rule.onNodeWithText("Χωρίς σύνδεση με δήλωση").performClick()
        rule.runOnIdle {
            assertTrue(action is QuickEntryAction.StatementChanged)
            assertEquals("", (action as QuickEntryAction.StatementChanged).id)
        }
    }

    @Test fun noStatementOptionsDoesNotShowDeadEndSelector() {
        rule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(
                    state = QuickEntryUiState(
                        kind = QuickEntryKind.CARD_PAYMENT,
                        amountText = "15,00",
                        dateText = "2026-10-15",
                        creditStatements = listOf(
                            QuickEntryStatementOption(
                                id = "other-card", cardId = "another-card",
                                label = "Foreign card statement", remaining = 90.0,
                            ),
                            QuickEntryStatementOption(
                                id = "already-paid", cardId = "card-credit",
                                label = "Already paid", remaining = 0.0,
                            ),
                        ),
                        dirty = true,
                    ),
                    onAction = {},
                    onBack = {},
                )
            }
        }
        assertTrue(rule.onAllNodes(hasText("Δήλωση πιστωτικής (προαιρετικά)"))
            .fetchSemanticsNodes().isEmpty())
    }
}
