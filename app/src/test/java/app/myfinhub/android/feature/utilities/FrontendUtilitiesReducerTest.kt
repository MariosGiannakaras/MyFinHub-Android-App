package app.myfinhub.android.feature.utilities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
    fun importReplacement_requiresPreviewAndExplicitConfirmation() {
        val state = FrontendUtilitiesUiState()
        val blocked = reduceFrontendUtilities(state, FrontendUtilitiesAction.RequestReplaceImport)
        assertFalse(blocked.importConfirmationOpen)
        assertTrue(blocked.importMessage.orEmpty().contains("προεπισκόπηση"))

        val preview = reduceFrontendUtilities(state, FrontendUtilitiesAction.PrepareImportPreview)
        val requested = reduceFrontendUtilities(preview, FrontendUtilitiesAction.RequestReplaceImport)
        assertTrue(requested.importConfirmationOpen)

        val confirmed = reduceFrontendUtilities(requested, FrontendUtilitiesAction.ConfirmReplaceImport)
        assertFalse(confirmed.importConfirmationOpen)
        assertTrue(confirmed.importMessage.orEmpty().contains("Δεν άλλαξαν δεδομένα"))
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
