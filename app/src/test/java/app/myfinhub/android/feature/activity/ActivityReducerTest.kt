package app.myfinhub.android.feature.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityReducerTest {
    @Test
    fun categoryScope_isExactInclusiveAndIndependentOfGlobalFilters() {
        fun row(id: String, date: String, category: String, amount: Double, contribution: Double) =
            ActivityItem(id, date, ActivityKind.EXPENSE, "Τρόφιμα στο σημείωμα", "", amount, "bank", category,
                rawDate = date, categoryContributions = mapOf(category to contribution))
        val state = ActivityUiState(query = "global query", filter = ActivityFilter.TRANSFER,
            accountFilterId = "other", selectedId = "split", items = listOf(
                row("split", "2026-09-01", "Τρόφιμα", -100.0, 20.0),
                row("refund", "2026-09-10", "Τρόφιμα", 5.0, -5.0),
                row("substring", "2026-09-10", "Τρόφιμα εκτός", -10.0, 10.0),
                row("old", "2026-08-31", "Τρόφιμα", -10.0, 10.0),
                row("future", "2026-09-11", "Τρόφιμα", -10.0, 10.0),
            ))
        val scoped = state.forCategory("Τρόφιμα", "2026-09-01", "2026-09-10")
        assertEquals(listOf("split", "refund"), scoped.visibleItems.map { it.id })
        assertEquals(listOf(-20.0, 5.0), scoped.visibleItems.map { it.amount })
        assertEquals(-100.0, state.selectedItem!!.amount, 0.0)
        assertEquals("global query", state.query)
        assertEquals(ActivityFilter.TRANSFER, state.filter)
        assertEquals("other", state.accountFilterId)
        assertTrue(state.forCategory("Τρόφιμα", "2026-09-10", "2026-09-01").visibleItems.isEmpty())
    }

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

    @Test
    fun transferRoute_usesCanonicalAccountOptionsWhenAvailable() {
        val item = ActivityItem(
            id = "transfer",
            dateLabel = "Σήμερα",
            kind = ActivityKind.TRANSFER,
            title = "Μεταφορά",
            subtitle = "Εσωτερική μεταφορά",
            amount = 120.0,
            accountLabel = "Fallback",
            category = null,
            rawDate = "2026-09-09",
            fromAccountId = "main",
            toAccountId = "savings",
        )

        val label = activityTransferRouteLabel(
            item,
            listOf(
                ActivityAccountOption("main", "Κύριος"),
                ActivityAccountOption("savings", "Αποταμίευση"),
            ),
        )

        assertEquals("Από Κύριος → Προς Αποταμίευση", label)
    }

    @Test
    fun signedAmount_keepsExplicitPositiveAndNegativeSigns() {
        assertTrue(formatSignedEuro(12.5).startsWith("+"))
        assertTrue(formatSignedEuro(-12.5).startsWith("−"))
    }
}
