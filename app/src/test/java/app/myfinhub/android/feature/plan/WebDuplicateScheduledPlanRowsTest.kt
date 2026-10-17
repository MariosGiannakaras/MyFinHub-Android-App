package app.myfinhub.android.feature.plan

import org.junit.Assert.assertEquals
import org.junit.Test

/** Web scheduled.ts source array rows are never collapsed by historical duplicate IDs. */
class WebDuplicateScheduledPlanRowsTest {
    @Test fun anUrgentDuplicateCannotHideAnotherRowWithTheSameId() {
        val overdue = PlannedItem(
            id="legacy",title="Α",dueLabel="10 Οκτ",amount=30.0,
            kind=PlannedKind.SCHEDULED,dueDateIso="2026-10-10",
            urgency=PlannedUrgency.OVERDUE)
        val upcoming=overdue.copy(title="Β",amount=40.0,
            dueDateIso="2026-11-10",urgency=PlannedUrgency.LATER)
        val state=PlanUiState(items=listOf(overdue,upcoming))
        assertEquals(listOf("Α"),planUrgentObligations(state).map { it.title })
        assertEquals(listOf("Β"),planRemainingItems(state).map { it.title })
        assertEquals(listOf("Α","Β"),state.items.map { it.title })
    }
    @Test fun multipleSameIdRowsKeepTheirOwnForecastOccurrence() {
        val a=PlannedItem("same","Internet A","1 Νοε",20.0,PlannedKind.SCHEDULED,
            dueDateIso="2026-11-01")
        val b=a.copy(title="Internet B",amount=35.0,dueDateIso="2026-11-15")
        val state=PlanUiState(items=listOf(a,b),forecastEndDateIso="2026-11-30")
        assertEquals(listOf("Internet A","Internet B"),
            planForecastIncludedItems(state).map { it.title })
    }
}
