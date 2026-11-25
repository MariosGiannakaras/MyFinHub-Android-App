package app.myfinhub.android.feature.quickentry

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun pendingSubmissionBlocksToolbarAndSystemBackThenAllowsDiscardAfterFailure() {
        var state by mutableStateOf(validState(QuickEntryKind.EXPENSE))
        var saving by mutableStateOf(false)
        var exits = 0
        var resets = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            val owner = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
            SideEffect { dispatcher = owner.onBackPressedDispatcher }
            MyFinHubTheme {
                QuickEntryBackGuard(
                    state = state,
                    mutationInFlight = saving,
                    onAction = { if (it == QuickEntryAction.Reset) resets++ },
                    onExit = { exits++ },
                ) { requestExit ->
                    ProductionQuickEntryScreen(
                        state = state, onAction = {}, onBack = requestExit,
                        mutationInFlight = saving,
                    )
                }
            }
        }

        // A discard confirmation opened just before submission must disappear.
        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.onNodeWithText("Απόρριψη νέας κίνησης;").assertExists()
        composeRule.runOnIdle { saving = true }
        composeRule.onNodeWithText("Απόρριψη νέας κίνησης;").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        composeRule.onNodeWithText("Απόρριψη νέας κίνησης;").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(0, exits)
            assertEquals(0, resets)
            saving = false
            state = state.copy(validationMessage = "Αποτυχία αποθήκευσης")
        }
        composeRule.onNodeWithText("Απόρριψη νέας κίνησης;").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.onNodeWithText("Απόρριψη νέας κίνησης;").assertExists()
        composeRule.onNodeWithText("Απόρριψη αλλαγών").performClick()
        composeRule.runOnIdle {
            assertEquals(1, exits)
            assertEquals(1, resets)
        }
    }

    @Test
    fun durableSaveStillAutomaticallyExitsEvenIfSavingFlagHasNotCleared() {
        var state by mutableStateOf(validState(QuickEntryKind.EXPENSE))
        var exits = 0
        var resets = 0
        composeRule.setContent {
            MyFinHubTheme {
                QuickEntryBackGuard(
                    state = state,
                    mutationInFlight = true,
                    onAction = { if (it == QuickEntryAction.Reset) resets++ },
                    onExit = { exits++ },
                ) { requestExit ->
                    ProductionQuickEntryScreen(
                        state = state, onAction = {}, onBack = requestExit,
                        mutationInFlight = true,
                    )
                }
            }
        }
        composeRule.runOnIdle {
            assertEquals(0, exits)
            state = state.copy(persisted = true, dirty = false)
        }
        composeRule.runOnIdle {
            assertEquals(1, exits)
            assertEquals(0, resets)
        }
    }

    @Test
    fun cardPaymentOptimisticZeroDebtShowsPendingInsteadOfFalseRejection() {
        var state by mutableStateOf(validState(QuickEntryKind.CARD_PAYMENT).copy(
            amountText = "2", cardPaymentDebt = 2.0,
        ))
        var saving by mutableStateOf(false)
        composeRule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(
                    state = state, onAction = {}, onBack = {}, mutationInFlight = saving,
                )
            }
        }

        composeRule.onNodeWithText("Οφειλή κάρτας:", substring = true).assertExists()
        composeRule.runOnIdle {
            // Exactly the optimistic projection after a 2€ repayment.
            state = state.copy(cardPaymentDebt = 0.0)
            saving = true
        }
        composeRule.onNodeWithText("Καταχώριση…").assertIsNotEnabled()
        composeRule.onNodeWithText("Η πληρωμή καταχωρίζεται…").assertExists()
        composeRule.onNodeWithText(
            "Δεν υπάρχει καταγεγραμμένη οφειλή για την επιλεγμένη ημερομηνία.",
        ).assertDoesNotExist()
        composeRule.runOnIdle { saving = false }
        // Zero debt still blocks a NEW attempt after the pending write ends.
        composeRule.onNodeWithText(
            "Δεν υπάρχει καταγεγραμμένη οφειλή για την επιλεγμένη ημερομηνία.",
        ).assertExists()
        composeRule.onNodeWithText("Καταχώριση").assertIsNotEnabled()
    }

    @Test
    fun blockingSyncIssueDisablesSaveWithGuidanceButKeepsDraftEditable() {
        var state by mutableStateOf(validState(QuickEntryKind.EXPENSE))
        var blocked by mutableStateOf(true)
        composeRule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(
                    state = state,
                    onAction = { state = reduceQuickEntry(state, it) },
                    onBack = {},
                    mutationBlocked = blocked,
                )
            }
        }

        composeRule.onNodeWithTag("quick_entry_mutation_blocked").assertExists()
        composeRule.onNodeWithText("Καταχώριση").assertIsNotEnabled()
        // Changing draft kind is still allowed. It must not turn Save on while
        // the unresolved issue blocks canonical finance writes.
        composeRule.onNodeWithTag("quick_entry_credit_purchase_shortcut").performClick()
        composeRule.onNodeWithText("Αγορά με κάρτα · Αλλαγή τύπου").assertIsEnabled()
        composeRule.onNodeWithText("Καταχώριση").assertIsNotEnabled()
        composeRule.runOnIdle { blocked = false }
        composeRule.onNodeWithText("Καταχώριση").assertIsEnabled()
    }

    @Test
    fun lendingRepaymentShowsAsOfDebtAndBlocksOverpaymentBeforeSave() {
        var state by mutableStateOf(
            validState(QuickEntryKind.REPAYMENT).copy(
                person = "Άννα", lendingOutstanding = 10.0,
            ),
        )
        composeRule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(state = state, onAction = {}, onBack = {})
            }
        }

        composeRule.onNodeWithTag("quick_entry_lending_debt").assertExists()
        composeRule.onNodeWithText("Καταχώριση").assertIsNotEnabled()
        composeRule.runOnIdle { state = state.copy(amountText = "10,00") }
        composeRule.onNodeWithText("Καταχώριση").assertIsEnabled()
        composeRule.runOnIdle { state = state.copy(lendingOutstanding = 0.0) }
        composeRule.onNodeWithText("Καταχώριση").assertIsNotEnabled()
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
    fun purchaseShortcutIsContextualAndDoesNotPolluteIncomeOrTransfer() {
        var state by mutableStateOf(validState(QuickEntryKind.EXPENSE))
        composeRule.setContent {
            MyFinHubTheme {
                ProductionQuickEntryScreen(state = state, onAction = {}, onBack = {})
            }
        }
        composeRule.onNodeWithTag("quick_entry_credit_purchase_shortcut").assertExists()
        composeRule.runOnIdle { state = validState(QuickEntryKind.INCOME) }
        composeRule.onNodeWithTag("quick_entry_credit_purchase_shortcut").assertDoesNotExist()
        composeRule.runOnIdle { state = validState(QuickEntryKind.TRANSFER) }
        composeRule.onNodeWithTag("quick_entry_credit_purchase_shortcut").assertDoesNotExist()
        composeRule.runOnIdle { state = validState(QuickEntryKind.EXPENSE).copy(creditCards = emptyList()) }
        composeRule.onNodeWithTag("quick_entry_credit_purchase_shortcut").assertDoesNotExist()
    }

    @Test
    fun duplicateCardOrAccountDisablesTheStickyTransactionSaveAction() {
        var state by mutableStateOf(validState(QuickEntryKind.CARD_PURCHASE))
        composeRule.setContent { MyFinHubTheme {
            ProductionQuickEntryScreen(state = state, onAction = {}, onBack = {})
        } }
        composeRule.onNodeWithText("Καταχώριση").assertIsEnabled()
        composeRule.runOnIdle {
            val card = state.creditCards.single()
            state = state.copy(creditCards = listOf(card, card.copy(label = "Διπλή κάρτα")))
        }
        composeRule.onNodeWithText("Καταχώριση").assertIsNotEnabled()
        composeRule.runOnIdle {
            val source = validState(QuickEntryKind.EXPENSE)
            state = source.copy(accounts = source.accounts + source.accounts.first())
        }
        composeRule.onNodeWithText("Καταχώριση").assertIsNotEnabled()
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
