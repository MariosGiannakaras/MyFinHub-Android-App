package app.myfinhub.android.feature.quickentry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionQuickEntryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun oneEditor_exposesTheRequiredFieldsForEveryCanonicalKind() {
        var state by mutableStateOf(validState(QuickEntryKind.EXPENSE))
        composeRule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(
                    state = state,
                    onAction = {},
                    onBack = {},
                )
            }
        }

        QuickEntryKind.entries.forEach { kind ->
            composeRule.runOnIdle { state = validState(kind) }
            composeRule.waitForIdle()

            composeRule.onNodeWithText(kind.description).fetchSemanticsNode()
            when (kind) {
                QuickEntryKind.TRANSFER,
                QuickEntryKind.WITHDRAWAL,
                QuickEntryKind.SAVING -> {
                    composeRule.onNodeWithText("Από λογαριασμό").fetchSemanticsNode()
                    composeRule.onNodeWithText("Προς λογαριασμό").fetchSemanticsNode()
                }
                QuickEntryKind.CARD_PURCHASE -> {
                    composeRule.onNodeWithText("Πιστωτική κάρτα").fetchSemanticsNode()
                }
                QuickEntryKind.CARD_PAYMENT -> {
                    composeRule.onNodeWithText("Από λογαριασμό").fetchSemanticsNode()
                    composeRule.onNodeWithText("Πιστωτική κάρτα").fetchSemanticsNode()
                }
                QuickEntryKind.LENDING,
                QuickEntryKind.REPAYMENT -> composeRule.onNodeWithText("Πρόσωπο").fetchSemanticsNode()
                QuickEntryKind.RECONCILIATION -> {
                    composeRule.onNodeWithText(
                        "Καταχωρίζεται η διαφορά από το υπολογισμένο υπόλοιπο, όχι νέο έσοδο ή έξοδο.",
                    ).fetchSemanticsNode()
                }
                QuickEntryKind.SPLIT -> {
                    composeRule.onNodeWithText("Κατανομή ποσού").fetchSemanticsNode()
                }
                else -> Unit
            }
            val amountLabels = composeRule
                .onAllNodes(hasText(kind.amountHeading))
                .fetchSemanticsNodes()
            assertTrue(
                "${kind.name} should expose its type-specific amount field label",
                amountLabels.isNotEmpty(),
            )
        }
    }

    @Test
    fun mutationInFlight_disablesStickySaveAgainstDuplicateSubmission() {
        composeRule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(
                    state = validState(QuickEntryKind.EXPENSE),
                    onAction = {},
                    onBack = {},
                    mutationInFlight = true,
                )
            }
        }

        composeRule.onNodeWithText("Καταχώριση…").assertIsNotEnabled()
    }

    @Test
    fun requiredAmountsAndLendingPersonGateSubmissionButValidZeroReconciliationIsAccepted() {
        var state by mutableStateOf(validState(QuickEntryKind.EXPENSE).copy(amountText = ""))
        composeRule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(state = state, onAction = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText("Καταχώριση").assertIsNotEnabled()
        composeRule.runOnIdle { state = state.copy(amountText = "0") }
        composeRule.onNodeWithText("Καταχώριση").assertIsNotEnabled()
        composeRule.runOnIdle { state = state.copy(amountText = "15,00") }
        composeRule.onNodeWithText("Καταχώριση").assertIsEnabled()
        composeRule.runOnIdle { state = validState(QuickEntryKind.LENDING).copy(person = "") }
        composeRule.onNodeWithText("Καταχώριση").assertIsNotEnabled()
        composeRule.runOnIdle { state = validState(QuickEntryKind.RECONCILIATION).copy(actualBalanceText = "") }
        composeRule.onNodeWithText("Καταχώριση").assertIsNotEnabled()
        composeRule.runOnIdle { state = state.copy(actualBalanceText = "0") }
        composeRule.onNodeWithText("Καταχώριση").assertIsEnabled()
    }

    @Test
    fun creditPurchaseShortcutSelectsCanonicalCardPurchaseInsteadOfSalaryExpense() {
        var state by mutableStateOf(validState(QuickEntryKind.EXPENSE))
        composeRule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(
                    state = state,
                    onAction = { state = reduceQuickEntry(state, it) },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("quick_entry_credit_purchase_shortcut").performClick()
        composeRule.runOnIdle {
            assertEquals(QuickEntryKind.CARD_PURCHASE, state.kind)
            assertEquals("card-credit", state.cardId)
        }
        composeRule.onNodeWithText("Πιστωτική κάρτα").fetchSemanticsNode()
    }

    @Test
    fun fullDraftValidationGatesDateCardAndTransferSelections() {
        var state by mutableStateOf(validState(QuickEntryKind.EXPENSE))
        composeRule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(state = state, onAction = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText("Καταχώριση").assertIsEnabled()
        composeRule.runOnIdle { state = state.copy(dateText = "invalid-date") }
        composeRule.onNodeWithText("Καταχώριση").assertIsNotEnabled()
        composeRule.runOnIdle { state = validState(QuickEntryKind.CARD_PURCHASE).copy(cardId = "missing") }
        composeRule.onNodeWithText("Καταχώριση").assertIsNotEnabled()
        composeRule.runOnIdle { state = validState(QuickEntryKind.TRANSFER).copy(toAccountId = "acc-main") }
        composeRule.onNodeWithText("Καταχώριση").assertIsNotEnabled()
    }

    @Test
    fun specialKindShowsOneActiveChoiceAndKeepsTheFullPickerAvailable() {
        composeRule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(
                    state = validState(QuickEntryKind.CARD_PURCHASE),
                    onAction = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Αγορά με κάρτα · Αλλαγή τύπου").assertIsEnabled()
        composeRule.onNodeWithText("Έξοδο").assertDoesNotExist()
    }

    private fun validState(kind: QuickEntryKind): QuickEntryUiState = QuickEntryUiState(
        kind = kind,
        amountText = if (kind == QuickEntryKind.RECONCILIATION) "" else "15,00",
        category = if (kind == QuickEntryKind.INCOME) "Μισθός" else "Τρόφιμα",
        fromAccountId = "acc-main",
        toAccountId = when (kind) {
            QuickEntryKind.WITHDRAWAL -> "acc-cash"
            else -> "acc-save"
        },
        person = "Άννα",
        actualBalanceText = "1250,40",
        splitParts = listOf(
            QuickEntrySplitPartDraft("p1", category = "Τρόφιμα", amountText = "10,10"),
            QuickEntrySplitPartDraft("p2", category = "Μετακίνηση", amountText = "4,90"),
        ),
        dirty = true,
    )
}
