package app.myfinhub.android.feature.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityReducerTest {
    @Test
    fun transferFilter_keepsOnlyTransfers() {
        val state = reduceActivity(ActivityUiState(), ActivityAction.FilterChanged(ActivityFilter.TRANSFER))

        assertTrue(state.visibleItems.isNotEmpty())
        assertTrue(state.visibleItems.all { it.kind == ActivityKind.TRANSFER })
    }

    @Test
    fun search_matchesTitleSubtitleOrCategory() {
        val state = reduceActivity(ActivityUiState(), ActivityAction.QueryChanged("τρόφιμα"))

        assertEquals(listOf("evt-1"), state.visibleItems.map { it.id })
    }

    @Test
    fun explicitSaveEdit_updatesOnlySelectedEvent() {
        val initial = ActivityUiState()
        val updated = reduceActivity(
            initial,
            ActivityAction.SaveEdit("evt-1", note = "Νέα σημείωση", category = "Νέα κατηγορία"),
        )

        val edited = updated.items.first { it.id == "evt-1" }
        assertEquals("Νέα σημείωση", edited.subtitle)
        assertEquals("Νέα κατηγορία", edited.category)
        assertEquals(initial.items.first { it.id == "evt-2" }, updated.items.first { it.id == "evt-2" })
    }
    @Test
    fun accountFilter_keepsDirectAndTransferAccountActivity() {
        val items = listOf(
            ActivityItem(
                id = "direct",
                dateLabel = "Σήμερα",
                kind = ActivityKind.EXPENSE,
                title = "Άμεση",
                subtitle = "",
                amount = -10.0,
                accountLabel = "Κύριος",
                category = null,
                rawDate = "2026-09-07",
                accountId = "main",
            ),
            ActivityItem(
                id = "transfer",
                dateLabel = "Σήμερα",
                kind = ActivityKind.TRANSFER,
                title = "Μεταφορά",
                subtitle = "",
                amount = 20.0,
                accountLabel = "Κύριος → Αποταμίευση",
                category = null,
                rawDate = "2026-09-07",
                fromAccountId = "main",
                toAccountId = "savings",
            ),
            ActivityItem(
                id = "other",
                dateLabel = "Σήμερα",
                kind = ActivityKind.EXPENSE,
                title = "Άλλος",
                subtitle = "",
                amount = -5.0,
                accountLabel = "Μετρητά",
                category = null,
                rawDate = "2026-09-07",
                accountId = "cash",
            ),
        )
        val initial = ActivityUiState(
            items = items,
            accountOptions = listOf(ActivityAccountOption("main", "Κύριος")),
        )

        val filtered = reduceActivity(initial, ActivityAction.AccountFilterChanged("main"))

        assertEquals(listOf("direct", "transfer"), filtered.visibleItems.map { it.id })
    }

}
