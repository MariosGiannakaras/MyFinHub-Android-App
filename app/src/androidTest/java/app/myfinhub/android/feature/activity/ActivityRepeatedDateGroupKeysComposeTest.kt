package app.myfinhub.android.feature.activity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real ledger must render repeated noncontiguous day/month headers without duplicate LazyColumn keys. */
class ActivityRepeatedDateGroupKeysComposeTest {
    @get:Rule val rule = createComposeRule()

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
