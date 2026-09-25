package app.myfinhub.android.feature.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class S8AnalyticsScopeTest {
    @Test
    fun remainderDrillDown_preservesExactCategorySetAndInterval() {
        val state = ActivityUiState(
            items = listOf(
                ActivityItem("split", "", ActivityKind.EXPENSE, "Split", "", -100.0, "A", "Ζ", rawDate = "2026-09-05", categoryContributions = mapOf("Ζ" to 20.0, "Η" to 30.0, "Α" to 50.0)),
                ActivityItem("outside", "", ActivityKind.EXPENSE, "Outside", "", -10.0, "A", "Ζ", rawDate = "2026-08-30", categoryContributions = mapOf("Ζ" to 10.0)),
            ),
        ).forCategories(listOf("Ζ", "Η"), "Λοιπά", "2026-09-01", "2026-09-30")

        assertEquals("Λοιπά", state.categoryFilter)
        assertEquals(setOf("Ζ", "Η"), state.categoryFilterIds)
        assertEquals(listOf("split"), state.visibleItems.map { it.id })
        assertEquals(-50.0, state.visibleItems.single().amount, 0.001)
        assertTrue(state.isAnalyticsScope)
    }
}
