package app.myfinhub.android.feature.activity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
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

    @Test fun repeatedLegacyIdentifiersRenderAsSeparateRowsWithoutDuplicateLazyKeys() {
        val state = ActivityUiState(items = listOf(
            ActivityItem("duplicate", "21 Οκτ", ActivityKind.EXPENSE,
                "Πρώτη ίδια ταυτότητα", "", -5.0, "Μετρητά", "Τρόφιμα",
                rawDate = "2026-10-21", isLegacyTransaction = true),
            ActivityItem("duplicate", "21 Οκτ", ActivityKind.EXPENSE,
                "Δεύτερη ίδια ταυτότητα", "", -10.0, "Μετρητά", "Τρόφιμα",
                rawDate = "2026-10-21", isLegacyTransaction = true),
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
