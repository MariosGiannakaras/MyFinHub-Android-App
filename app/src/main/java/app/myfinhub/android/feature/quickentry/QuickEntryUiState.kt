package app.myfinhub.android.feature.quickentry

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class QuickEntryKind(val label: String) {
    EXPENSE("Έξοδο"),
    TRANSFER("Μεταφορά"),
    CARD_PAYMENT("Πληρωμή κάρτας"),
    SPLIT("Μοίρασμα"),
}

data class QuickEntryUiState(
    val kind: QuickEntryKind = QuickEntryKind.EXPENSE,
    val amountText: String = "",
    val note: String = "",
    val category: String = "",
    val fromAccount: String = "Κύριος λογαριασμός",
    val destination: String = "Αποταμίευση",
    val splitPeople: Int = 2,
    val validationMessage: String? = null,
    val savedSummary: String? = null,
) {
    val amount: Double?
        get() = amountText.replace(',', '.').toDoubleOrNull()
}

sealed interface QuickEntryAction {
    data class SelectKind(val kind: QuickEntryKind) : QuickEntryAction
    data class AmountChanged(val value: String) : QuickEntryAction
    data class NoteChanged(val value: String) : QuickEntryAction
    data class CategoryChanged(val value: String) : QuickEntryAction
    data class DestinationChanged(val value: String) : QuickEntryAction
    data class SplitPeopleChanged(val value: Int) : QuickEntryAction
    data object Save : QuickEntryAction
    data object Reset : QuickEntryAction
}

fun reduceQuickEntry(state: QuickEntryUiState, action: QuickEntryAction): QuickEntryUiState = when (action) {
    is QuickEntryAction.SelectKind -> state.copy(
        kind = action.kind,
        validationMessage = null,
        savedSummary = null,
    )
    is QuickEntryAction.AmountChanged -> state.copy(amountText = action.value, validationMessage = null, savedSummary = null)
    is QuickEntryAction.NoteChanged -> state.copy(note = action.value, savedSummary = null)
    is QuickEntryAction.CategoryChanged -> state.copy(category = action.value, savedSummary = null)
    is QuickEntryAction.DestinationChanged -> state.copy(destination = action.value, savedSummary = null)
    is QuickEntryAction.SplitPeopleChanged -> state.copy(splitPeople = action.value.coerceIn(2, 12), savedSummary = null)
    QuickEntryAction.Reset -> QuickEntryUiState(kind = state.kind)
    QuickEntryAction.Save -> validateAndSave(state)
}

private fun validateAndSave(state: QuickEntryUiState): QuickEntryUiState {
    val amount = state.amount
    if (amount == null || amount <= 0.0) {
        return state.copy(validationMessage = "Βάλε ποσό μεγαλύτερο από μηδέν.", savedSummary = null)
    }
    if (state.note.isBlank()) {
        return state.copy(validationMessage = "Πρόσθεσε μια σύντομη περιγραφή.", savedSummary = null)
    }
    if (state.kind == QuickEntryKind.TRANSFER && state.destination.isBlank()) {
        return state.copy(validationMessage = "Διάλεξε προορισμό μεταφοράς.", savedSummary = null)
    }

    val kindSummary = when (state.kind) {
        QuickEntryKind.EXPENSE -> "Έξοδο"
        QuickEntryKind.TRANSFER -> "Μεταφορά προς ${state.destination}"
        QuickEntryKind.CARD_PAYMENT -> "Πληρωμή κάρτας"
        QuickEntryKind.SPLIT -> "Μοίρασμα σε ${state.splitPeople} άτομα"
    }
    return state.copy(
        validationMessage = null,
        savedSummary = "$kindSummary · ${state.amountText} € · ${state.note}",
    )
}

class QuickEntryViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(QuickEntryUiState())
    val state: StateFlow<QuickEntryUiState> = mutableState.asStateFlow()

    fun onAction(action: QuickEntryAction) {
        mutableState.update { reduceQuickEntry(it, action) }
    }
}
