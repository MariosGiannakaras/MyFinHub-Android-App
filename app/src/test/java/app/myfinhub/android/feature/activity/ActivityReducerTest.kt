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
    fun detailEdits_updateOnlySelectedEvent() {
        val initial = ActivityUiState()
        val updated = reduceActivity(initial, ActivityAction.UpdateNote("evt-1", "Νέα σημείωση"))

        assertEquals("Νέα σημείωση", updated.items.first { it.id == "evt-1" }.subtitle)
        assertEquals(initial.items.first { it.id == "evt-2" }, updated.items.first { it.id == "evt-2" })
    }
}
