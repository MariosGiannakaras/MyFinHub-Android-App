package app.myfinhub.android.feature.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S7PlanPresentationTest {
    private val state = PlanUiState(
        items = listOf(
            PlannedItem("late", "Ενοίκιο", "8 Σεπ", 680.0, PlannedKind.SCHEDULED, dueDateIso = "2026-09-08", urgency = PlannedUrgency.OVERDUE),
            PlannedItem("soon", "Internet", "12 Σεπ", 35.0, PlannedKind.RECURRING, dueDateIso = "2026-09-12", urgency = PlannedUrgency.THIS_WEEK),
            PlannedItem("later", "Ασφάλεια", "25 Σεπ", 120.0, PlannedKind.SCHEDULED, dueDateIso = "2026-09-25", urgency = PlannedUrgency.LATER),
            PlannedItem("income", "Μισθός", "15 Σεπ", 1_500.0, PlannedKind.SCHEDULED, flow = PlannedFlow.INCOME, dueDateIso = "2026-09-15", urgency = PlannedUrgency.THIS_WEEK),
            PlannedItem("outside", "Μελλοντικό", "20 Νοε", 40.0, PlannedKind.SCHEDULED, dueDateIso = "2026-11-20", urgency = PlannedUrgency.LATER),
        ),
        forecastHorizonDays = 30,
        forecastStartDateIso = "2026-09-10",
        forecastEndDateIso = "2026-10-10",
    )

    @Test
    fun urgentAndRemaining_doNotDuplicateCanonicalSources() {
        val urgent = planUrgentObligations(state)
        val remaining = planRemainingItems(state)
        assertEquals(setOf("late", "soon"), urgent.map { it.id }.toSet())
        assertFalse(remaining.any { item -> plannedItemSourceKey(item) in urgent.map(::plannedItemSourceKey) })
        assertEquals(state.items.map(::plannedItemSourceKey).toSet(), (urgent + remaining).map(::plannedItemSourceKey).toSet())
    }

    @Test
    fun stableSourceKey_keepsSameRawIdFromDifferentCanonicalSourcesDistinct() {
        val scheduled = PlannedItem("same", "Λογαριασμός", "12 Σεπ", 20.0, PlannedKind.SCHEDULED)
        val recurring = scheduled.copy(kind = PlannedKind.RECURRING)
        assertTrue(plannedItemSourceKey(scheduled) != plannedItemSourceKey(recurring))
    }

    @Test
    fun forecast_keepsEveryEligibleStableSource_withoutPresentationCap() {
        val many = (1..25).map { index ->
            PlannedItem("bill-$index", "Λογαριασμός", "12 Σεπ", 10.0, PlannedKind.SCHEDULED, dueDateIso = "2026-09-12")
        }
        val projected = state.copy(items = many + state.items.first { it.id == "outside" })
        val included = planForecastIncludedItems(projected)
        assertEquals(25, included.size)
        assertEquals(25, included.map(::plannedItemSourceKey).distinct().size)
        assertTrue(included.none { it.id == "outside" })
    }

    @Test
    fun forecastScope_exposesExactStartAndEndDates() {
        val label = planForecastScopeLabel(state)
        assertTrue(label.contains("10 Σεπ 2026"))
        assertTrue(label.contains("10 Οκτ 2026"))
    }
}
