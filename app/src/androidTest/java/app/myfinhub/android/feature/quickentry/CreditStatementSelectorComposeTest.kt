package app.myfinhub.android.feature.quickentry

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.app.projectQuickEntryState
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
    @Test fun ambiguousAndNonFiniteStatementsCannotPopulateTheProductionPicker() {
        val options = listOf(
            QuickEntryStatementOption("same", "card-credit", "Διπλή κατάσταση", 40.0),
            QuickEntryStatementOption("same", "another-card", "Ξένη διπλή", 35.0),
            QuickEntryStatementOption("nan", "card-credit", "Μη έγκυρο ποσό", Double.NaN),
            QuickEntryStatementOption("paid", "card-credit", "Εξοφλημένη", 0.0),
        )
        rule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(
                    state = QuickEntryUiState(
                        kind = QuickEntryKind.CARD_PAYMENT,
                        amountText = "15", cardId = "card-credit",
                        creditStatements = options, dirty = true,
                    ),
                    onAction = {}, onBack = {},
                )
            }
        }
        assertTrue(rule.onAllNodes(hasText("Δήλωση πιστωτικής (προαιρετικά)"))
            .fetchSemanticsNodes().isEmpty())
    }

    @Test fun globallyUniquePayableStatementRemainsSelectableWhileDuplicatesAreHidden() {
        var action: QuickEntryAction? = null
        val options = listOf(
            QuickEntryStatementOption("duplicate", "card-credit", "Πρώτη διπλή", 40.0),
            QuickEntryStatementOption("duplicate", "other-card", "Δεύτερη διπλή", 40.0),
            QuickEntryStatementOption("unique", "card-credit", "Ασφαλής κατάσταση", 35.0),
        )
        rule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(
                    state = QuickEntryUiState(
                        kind = QuickEntryKind.CARD_PAYMENT,
                        amountText = "15", cardId = "card-credit",
                        creditStatements = options, dirty = true,
                    ),
                    onAction = { action = it }, onBack = {},
                )
            }
        }
        rule.onNodeWithText("Δήλωση πιστωτικής (προαιρετικά)").fetchSemanticsNode()
        rule.onNodeWithText("Χωρίς σύνδεση με δήλωση").performScrollTo().performClick()
        rule.onNodeWithText("Ασφαλής κατάσταση").performClick()
        rule.runOnIdle {
            assertTrue(action is QuickEntryAction.StatementChanged)
            assertEquals("unique", (action as QuickEntryAction.StatementChanged).id)
        }
    }

    @Test fun productionPickerUsesFullCanonicalStatementSourceNotJustSelectedCard() {
        val doc = CanonicalFinanceDocument(Json.parseToJsonElement(
            """
            {"seed":{"accounts":[{"id":"payroll","kind":"bank","name":"Μισθοδοσία"}]},
             "state":{"cards":[
                {"id":"c1","kind":"credit","active":true},
                {"id":"c2","kind":"credit","active":true}
             ],"creditStatements":[
                {"id":"collision","cardId":"c1","openDate":"2026-08-26",
                 "closeDate":"2026-09-25","dueDate":"2026-10-05"},
                {"id":"collision","cardId":"c2","openDate":"2026-08-26",
                 "closeDate":"2026-09-25","dueDate":"2026-10-05"},
                {"id":"safe","cardId":"c1","openDate":"2026-08-26",
                 "closeDate":"2026-09-25","dueDate":"2026-10-05"}
             ],"events":[
                {"id":"buy-collision","kind":"card_purchase","date":"2026-09-20",
                 "amount":20,"cardId":"c1","statementId":"collision"},
                {"id":"buy-safe","kind":"card_purchase","date":"2026-09-20",
                 "amount":35,"cardId":"c1","statementId":"safe"}
             ],"settings":{}}}
            """.trimIndent(),
        ).jsonObject)
        val projected = projectQuickEntryState(
            doc, LocalDate.parse("2026-10-15"), QuickEntryUiState(
                kind = QuickEntryKind.CARD_PAYMENT, amountText = "10",
                cardId = "c1", dateText = "2026-10-15",
                statementId = "collision", dirty = true,
            ),
        )
        assertEquals(listOf("safe"), projected.creditStatements.map { it.id })
        assertEquals("", projected.statementId)
        var selected: QuickEntryAction? = null
        rule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(
                    state = projected,
                    onAction = { selected = it },
                    onBack = {},
                )
            }
        }
        rule.onNodeWithText("Χωρίς σύνδεση με δήλωση").performScrollTo().performClick()
        rule.onNodeWithText(projected.creditStatements.single().label).performClick()
        rule.runOnIdle {
            assertTrue(selected is QuickEntryAction.StatementChanged)
            assertEquals("safe", (selected as QuickEntryAction.StatementChanged).id)
        }
    }

}
