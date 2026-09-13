package app.myfinhub.android.feature.quickentry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.designsystem.MyFinHubTheme
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
                    composeRule.onNodeWithText("Συνολικό ποσό").fetchSemanticsNode()
                    composeRule.onNodeWithText("Κατανομή ποσού").fetchSemanticsNode()
                }
                else -> composeRule.onNodeWithText("Πόσο ${kind.label.lowercase()};").fetchSemanticsNode()
            }
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

        composeRule.onNodeWithText("Αποθήκευση…").assertIsNotEnabled()
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
