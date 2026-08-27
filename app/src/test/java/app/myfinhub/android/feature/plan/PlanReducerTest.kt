package app.myfinhub.android.feature.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanReducerTest {
    @Test
    fun invalidThreshold_isRejected() {
        val state = PlanUiState(budget = BudgetDraft(monthlyLimitText = "900", alertThresholdText = "120"))

        val result = reducePlan(state, PlanAction.SaveBudget)

        assertEquals("Το όριο ειδοποίησης πρέπει να είναι από 1 έως 100%.", result.message)
    }

    @Test
    fun validBudget_isReadyForCanonicalSave() {
        val state = PlanUiState(budget = BudgetDraft(monthlyLimitText = "850,50", alertThresholdText = "80"))

        val result = reducePlan(state, PlanAction.SaveBudget)

        assertTrue(result.message.orEmpty().contains("έγκυρο"))
    }

    @Test
    fun plannedItemDraft_updatesOnlySelectedStableId() {
        val state = PlanUiState()
        val untouched = state.items[1]

        val result = reducePlan(
            state,
            PlanAction.UpdatePlannedItem(
                id = state.items.first().id,
                title = "Νέο ενοίκιο",
                dueLabel = "2 Σεπ",
                amount = 700.0,
                kind = PlannedKind.RECURRING,
                category = "Στέγαση",
                accountLabel = "Τράπεζα",
                note = "Draft",
            ),
        )

        assertEquals("Νέο ενοίκιο", result.items.first().title)
        assertEquals(700.0, result.items.first().amount, 0.0)
        assertEquals(untouched, result.items[1])
        assertTrue(result.itemMessage.orEmpty().contains("Android UI"))
    }

    @Test
    fun invalidEnabledCategoryBudget_isRejected() {
        val state = PlanUiState(
            categoryBudgets = listOf(
                CategoryBudget(
                    id = "food",
                    name = "Τρόφιμα",
                    monthlyLimitText = "0",
                    alertThresholdText = "80",
                    spent = 10.0,
                ),
            ),
        )

        val result = reducePlan(state, PlanAction.SaveCategoryBudgets)

        assertTrue(result.categoryBudgetMessage.orEmpty().contains("Τρόφιμα"))
    }

    @Test
    fun disabledCategoryBudget_doesNotBlockFrontendDraftValidation() {
        val state = PlanUiState(
            categoryBudgets = listOf(
                CategoryBudget(
                    id = "food",
                    name = "Τρόφιμα",
                    monthlyLimitText = "0",
                    alertThresholdText = "200",
                    spent = 10.0,
                    enabled = false,
                ),
            ),
        )

        val result = reducePlan(state, PlanAction.SaveCategoryBudgets)

        assertTrue(result.categoryBudgetMessage.orEmpty().contains("frontend draft"))
    }

    @Test
    fun forecastHorizon_acceptsOnlyDefinedWindow() {
        val state = PlanUiState(forecastHorizonDays = 30)

        val selected = reducePlan(state, PlanAction.ForecastHorizonChanged(60))
        val rejected = reducePlan(selected, PlanAction.ForecastHorizonChanged(45))

        assertEquals(60, selected.forecastHorizonDays)
        assertEquals(60, rejected.forecastHorizonDays)
    }

    @Test
    fun pauseToggle_isStableById() {
        val state = PlanUiState()
        val id = state.items.first().id

        val paused = reducePlan(state, PlanAction.TogglePlannedItemPause(id))
        val resumed = reducePlan(paused, PlanAction.TogglePlannedItemPause(id))

        assertTrue(paused.items.first { it.id == id }.paused)
        assertFalse(resumed.items.first { it.id == id }.paused)
    }
}
