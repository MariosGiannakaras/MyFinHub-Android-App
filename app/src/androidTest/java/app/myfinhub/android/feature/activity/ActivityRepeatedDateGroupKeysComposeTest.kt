package app.myfinhub.android.feature.activity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real ledger must render repeated noncontiguous day/month headers without duplicate LazyColumn keys. */
class ActivityRepeatedDateGroupKeysComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun realCardPurchaseAndPaymentDoNotDuplicateCardActivityOnGlobalLedger() {
        val source = ActivityUiState(items = listOf(
            ActivityItem("payment", "21 Οκτ", ActivityKind.CARD_PAYMENT,
                "Καρτοπληρωμή μόνο στην κάρτα", "", -15.0, "Μισθοδοσία", "Κάρτες",
                rawDate = "2026-10-21", canonicalKind = "card_payment",
                canonicalSourceOrdinal = 0),
            ActivityItem("purchase", "20 Οκτ", ActivityKind.EXPENSE,
                "Καρτοαγορά μόνο στην κάρτα", "", -20.0, "Πιστωτική", "Τρόφιμα",
                rawDate = "2026-10-20", canonicalKind = "card_purchase",
                canonicalSourceOrdinal = 1),
            ActivityItem("expense", "19 Οκτ", ActivityKind.EXPENSE,
                "Κανονική κίνηση μετρητών", "", -9.0, "Μετρητά", "Τρόφιμα",
                rawDate = "2026-10-19", canonicalSourceOrdinal = 2,
                note = "Κανονική κίνηση μετρητών"),
        ))
        assertEquals(listOf("expense"), source.visibleItems.map { it.id })
        rule.setContent {
            MyFinHubTheme {
                ActivityLedgerScreen(state = source, onAction = {},
                    onOpenDetail = {}, onOpenQuickEntry = {})
            }
        }
        rule.onNodeWithTag("activity_list")
            .performScrollToNode(hasText("Κανονική κίνηση μετρητών"))
        rule.onNodeWithText("Κανονική κίνηση μετρητών").assertIsDisplayed()
        rule.onNodeWithText("Καρτοπληρωμή μόνο στην κάρτα").assertDoesNotExist()
        rule.onNodeWithText("Καρτοαγορά μόνο στην κάρτα").assertDoesNotExist()
    }

    @Test fun productionLedgerQueryNeverSearchesPrivateAccountOrDateDisplayFields() {
        val realRow = ActivityItem(
            "visible", "21 Οκτ", ActivityKind.EXPENSE, "Μη ευρετηριασμένος τίτλος", "",
            -11.0, "Ιδιωτικός λογαριασμός", "Τρόφιμα",
            rawDate = "2026-10-21", note = "Καφές εργασίας",
            canonicalSourceOrdinal = 0,
        )
        var state by androidx.compose.runtime.mutableStateOf(
            ActivityUiState(items = listOf(realRow), query = "Ιδιωτικός"),
        )
        rule.setContent {
            MyFinHubTheme {
                ActivityLedgerScreen(
                    state = state,
                    onAction = { state = reduceActivity(state, it) },
                    onOpenDetail = {}, onOpenQuickEntry = {},
                )
            }
        }
        rule.onNodeWithText("Καφές εργασίας").assertDoesNotExist()
        rule.runOnIdle { state = state.copy(query = "Καφές") }
        rule.onNodeWithTag("activity_list")
            .performScrollToNode(hasText("Καφές εργασίας"))
        rule.onNodeWithText("Καφές εργασίας").assertIsDisplayed()
        // Web never substitutes the category/derived title for persisted note.
        rule.onNodeWithText("Μη ευρετηριασμένος τίτλος").assertDoesNotExist()
        rule.runOnIdle { state = state.copy(query = "2026-10-21") }
        rule.onNodeWithText("Καφές εργασίας").assertDoesNotExist()
    }

    @Test fun repeatedLegacyIdentifiersRenderAsSeparateRowsWithoutDuplicateLazyKeys() {
        val state = ActivityUiState(items = listOf(
            ActivityItem("duplicate", "21 Οκτ", ActivityKind.EXPENSE,
                "Πρώτη ίδια ταυτότητα", "", -5.0, "Μετρητά", "Τρόφιμα",
                rawDate = "2026-10-21", isLegacyTransaction = true,
                note = "Πρώτη ίδια ταυτότητα"),
            ActivityItem("duplicate", "21 Οκτ", ActivityKind.EXPENSE,
                "Δεύτερη ίδια ταυτότητα", "", -10.0, "Μετρητά", "Τρόφιμα",
                rawDate = "2026-10-21", isLegacyTransaction = true,
                note = "Δεύτερη ίδια ταυτότητα"),
            ActivityItem("another", "20 Οκτ", ActivityKind.EXPENSE,
                "Τρίτη ανεξάρτητη", "", -3.0, "Μετρητά", "Τρόφιμα",
                rawDate = "2026-10-20"),
        ))
        assertEquals(3, state.visibleItems.size)
        rule.setContent {
            MyFinHubTheme {
                ActivityLedgerScreen(state = state, onAction = {}, onOpenDetail = {}, onOpenQuickEntry = {})
            }
        }
        rule.onNodeWithTag("activity_list")
            .performScrollToNode(hasText("Δεύτερη ίδια ταυτότητα"))
        rule.onNodeWithText("Δεύτερη ίδια ταυτότητα").assertIsDisplayed()
        rule.onNodeWithTag("activity_list")
            .performScrollToNode(hasText("Πρώτη ίδια ταυτότητα"))
        rule.onNodeWithText("Πρώτη ίδια ταυτότητα").assertIsDisplayed()
    }

    @Test fun blankAndDuplicateGlobalLedgerIdsStayVisibleButNotClickable() {
        val rows = listOf(
            ActivityItem("", "19 Οκτ", ActivityKind.EXPENSE, "Χωρίς κωδικό", "", -15.0,
                "Μετρητά", "Τρόφιμα", rawDate = "2026-10-19", note = "Χωρίς κωδικό"),
            ActivityItem("duplicate", "19 Οκτ", ActivityKind.EXPENSE, "Πρώτη διπλή", "", -20.0,
                "Μετρητά", "Τρόφιμα", rawDate = "2026-10-19", note = "Πρώτη διπλή"),
            ActivityItem("duplicate", "19 Οκτ", ActivityKind.EXPENSE, "Δεύτερη διπλή", "", -35.0,
                "Μετρητά", "Τρόφιμα", rawDate = "2026-10-19", note = "Δεύτερη διπλή"),
            ActivityItem("safe", "19 Οκτ", ActivityKind.EXPENSE, "Έγκυρη κίνηση", "", -10.0,
                "Μετρητά", "Τρόφιμα", rawDate = "2026-10-19", note = "Έγκυρη κίνηση"),
        )
        val state = ActivityUiState(items = rows)
        var selected: String? = null
        rule.setContent { MyFinHubTheme {
            ActivityLedgerScreen(
                state = state,
                onAction = {},
                onOpenDetail = { selected = it },
                onOpenQuickEntry = {},
            )
        } }
        for (index in 0..2) {
            rule.onNodeWithTag("activity_list")
                .performScrollToNode(hasTestTag("activity_ledger_source_0_$index"))
            rule.onNodeWithTag("activity_ledger_source_0_$index")
                .assertIsNotEnabled()
        }
        rule.onNodeWithTag("activity_list")
            .performScrollToNode(hasTestTag("activity_ledger_source_0_3"))
        rule.onNodeWithTag("activity_ledger_source_0_3")
            .assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals("safe", selected) }
    }

    @Test fun queryOnlyProvidesSafeSignedSummaryAndOneStepClearAction() {
        val seed = ActivityUiState(items = listOf(
            ActivityItem("refund", "21 Οκτ", ActivityKind.EXPENSE,
                "Επιστροφή καφέ", "", -30.0, "Μετρητά", "Τρόφιμα",
                rawDate = "2026-10-21", financeIncomeContribution = 0.0,
                financeExpenseContribution = -30.0),
            ActivityItem("spend", "20 Οκτ", ActivityKind.EXPENSE,
                "Αγορά", "", -10.0, "Μετρητά", "Τρόφιμα",
                rawDate = "2026-10-20", financeIncomeContribution = 0.0,
                financeExpenseContribution = 10.0),
        ))
        var state by androidx.compose.runtime.mutableStateOf(seed.copy(query = "Επιστροφή"))
        rule.setContent {
            MyFinHubTheme {
                ActivityLedgerScreen(
                    state = state,
                    onAction = { action -> state = reduceActivity(state, action) },
                    onOpenDetail = {}, onOpenQuickEntry = {},
                )
            }
        }
        rule.onNodeWithTag("activity_filtered_summary").assertExists()
        rule.onNodeWithText("Βρέθηκαν 1 από 2 κινήσεις").assertIsDisplayed()
        rule.onNodeWithTag("activity_clear_all").assertExists().performClick()
        rule.runOnIdle {
            assertEquals("", state.query)
            assertEquals(2, state.visibleItems.size)
            assertEquals(-20.0, state.visibleExpense, 0.001)
            assertEquals(20.0, state.visibleNet, 0.001)
        }
        rule.onNodeWithTag("activity_filtered_summary").assertDoesNotExist()
        rule.onNodeWithTag("activity_clear_all").assertDoesNotExist()
    }

    @Test fun longLedgerSortToggleChangesRealComposeOrderThroughReducer() {
        val histories = (1..24).map { day ->
            ActivityItem(
                id = "tx-$day", dateLabel = "Οκτώβριος", kind = ActivityKind.EXPENSE,
                title = "Κίνηση $day", subtitle = "", amount = -day.toDouble(),
                accountLabel = "Μετρητά", category = "Άλλο",
                rawDate = "2026-10-${day.toString().padStart(2, '0')}",
            )
        }.asReversed()
        var state by androidx.compose.runtime.mutableStateOf(ActivityUiState(items = histories))
        rule.setContent {
            MyFinHubTheme {
                ActivityLedgerScreen(
                    state = state,
                    onAction = { action -> state = reduceActivity(state, action) },
                    onOpenDetail = {}, onOpenQuickEntry = {},
                )
            }
        }
        rule.onNodeWithTag("activity_sort_toggle").assertExists()
        rule.onNodeWithTag("activity_sort_toggle").performClick()
        rule.runOnIdle {
            assertEquals(true, state.sortOldestFirst)
            assertEquals("tx-1", state.visibleItems.first().id)
            assertEquals("tx-24", state.visibleItems.last().id)
        }
        rule.onNodeWithText("Παλαιότερες πρώτα").assertExists()
        rule.onNodeWithTag("activity_sort_toggle").performClick()
        rule.runOnIdle {
            assertEquals(false, state.sortOldestFirst)
            assertEquals("tx-24", state.visibleItems.first().id)
        }
    }

    @Test fun disjointSameDayAndMonthSectionsRetainAllRowsInOriginalOrder() {
        fun event(id: String, date: String, title: String) = ActivityItem(
            id = id,
            dateLabel = date,
            rawDate = date,
            kind = ActivityKind.EXPENSE,
            title = title,
            subtitle = "Δοκιμαστική κίνηση",
            amount = -5.0,
            accountLabel = "Μετρητά",
            category = "Άλλο",
        )
        val state = ActivityUiState(items = listOf(
            event("first-oct", "2026-10-20", "Πρώτη κίνηση"),
            event("sep", "2026-09-20", "Δεύτερη κίνηση"),
            event("last-oct", "2026-10-20", "Τρίτη κίνηση"),
        ))
        assertEquals(listOf("2026-10-20", "2026-09-20", "2026-10-20"),
            state.visibleSections.map { it.date })

        rule.setContent {
            MyFinHubTheme {
                ActivityLedgerScreen(
                    state = state,
                    onAction = {},
                    onOpenDetail = {},
                    onOpenQuickEntry = {},
                )
            }
        }
        // Scrolling composes the repeated October day and month headers again.
        // Prior date-only keys produced duplicate-key exceptions in this production LazyColumn.
        rule.onNodeWithTag("activity_list").performScrollToNode(hasText("Τρίτη κίνηση"))
        rule.onNodeWithText("Τρίτη κίνηση").assertIsDisplayed()
    }
}
