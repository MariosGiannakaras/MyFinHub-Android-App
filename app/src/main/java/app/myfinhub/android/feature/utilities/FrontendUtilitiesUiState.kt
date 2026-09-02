package app.myfinhub.android.feature.utilities

data class FrontendSettings(
    val hideAmountsOnStart: Boolean = true,
    val remindersEnabled: Boolean = true,
    val extraSensitiveScreenCheck: Boolean = true,
)

data class PrivacySafeHistoryEntry(
    val id: String,
    val title: String,
    val timeLabel: String,
)

data class FrontendUtilitiesUiState(
    val settings: FrontendSettings = FrontendSettings(),
    val history: List<PrivacySafeHistoryEntry> = syntheticPrivacySafeHistory(),
    val historyCursor: Int = history.size,
)

sealed interface FrontendUtilitiesAction {
    data object ToggleHideAmountsOnStart : FrontendUtilitiesAction
    data object ToggleReminders : FrontendUtilitiesAction
    data object ToggleExtraSensitiveScreenCheck : FrontendUtilitiesAction
    data object Undo : FrontendUtilitiesAction
    data object Redo : FrontendUtilitiesAction
}

fun reduceFrontendUtilities(
    state: FrontendUtilitiesUiState,
    action: FrontendUtilitiesAction,
): FrontendUtilitiesUiState = when (action) {
    FrontendUtilitiesAction.ToggleHideAmountsOnStart -> state.copy(
        settings = state.settings.copy(hideAmountsOnStart = !state.settings.hideAmountsOnStart),
    )
    FrontendUtilitiesAction.ToggleReminders -> state.copy(
        settings = state.settings.copy(remindersEnabled = !state.settings.remindersEnabled),
    )
    FrontendUtilitiesAction.ToggleExtraSensitiveScreenCheck -> state.copy(
        settings = state.settings.copy(extraSensitiveScreenCheck = !state.settings.extraSensitiveScreenCheck),
    )
    FrontendUtilitiesAction.Undo -> state.copy(
        historyCursor = (state.historyCursor - 1).coerceAtLeast(0),
    )
    FrontendUtilitiesAction.Redo -> state.copy(
        historyCursor = (state.historyCursor + 1).coerceAtMost(state.history.size),
    )
}

fun syntheticPrivacySafeHistory(): List<PrivacySafeHistoryEntry> = listOf(
    PrivacySafeHistoryEntry("history-1", "Ενημέρωση κατηγορίας", "Πριν από λίγο"),
    PrivacySafeHistoryEntry("history-2", "Αλλαγή προγραμματισμένης υποχρέωσης", "Σήμερα"),
    PrivacySafeHistoryEntry("history-3", "Ενημέρωση στόχου αποταμίευσης", "Σήμερα"),
)
