package app.myfinhub.android.feature.utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FrontendUtilitiesReducerTest {
    @Test
    fun settingsToggle_changesOnlyRequestedPreference() {
        val state = FrontendUtilitiesUiState()

        val result = reduceFrontendUtilities(state, FrontendUtilitiesAction.ToggleReminders)

        assertFalse(result.settings.remindersEnabled)
        assertEquals(state.settings.hideAmountsOnStart, result.settings.hideAmountsOnStart)
        assertEquals(state.settings.extraSensitiveScreenCheck, result.settings.extraSensitiveScreenCheck)
    }

    @Test
    fun undoRedo_staysWithinHistoryBounds() {
        var state = FrontendUtilitiesUiState()

        repeat(10) { state = reduceFrontendUtilities(state, FrontendUtilitiesAction.Undo) }
        assertEquals(0, state.historyCursor)

        repeat(10) { state = reduceFrontendUtilities(state, FrontendUtilitiesAction.Redo) }
        assertEquals(state.history.size, state.historyCursor)
    }
}
