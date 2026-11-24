package app.myfinhub.android.feature.quickentry

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Route-level guard shared by every Quick Entry entry point. System/predictive Back obeys
 * reducer-level dirty state, including selector/date/category changes that are not text entry.
 */
@Composable
fun QuickEntryBackGuard(
    state: QuickEntryUiState,
    onAction: (QuickEntryAction) -> Unit,
    onExit: () -> Unit,
    mutationInFlight: Boolean = false,
    content: @Composable (requestExit: () -> Unit) -> Unit,
) {
    var discardDialogOpen by rememberSaveable { mutableStateOf(false) }
    val saved = state.persisted || state.pendingSync
    val hasUnsavedDraft = state.dirty && !saved
    val submitPending = mutationInFlight && !saved

    // A stale discard dialog must not survive a save starting or completing.
    LaunchedEffect(submitPending, hasUnsavedDraft) {
        if (submitPending || !hasUnsavedDraft) discardDialogOpen = false
    }

    // Toolbar and Android system/predictive Back share the same guard.
    // The write cannot be cancelled by Reset; wait for durable enqueue/ack
    // or failure. A confirmed save may still auto-exit via the screen effect.
    val requestExit: () -> Unit = {
        when {
            submitPending -> Unit
            hasUnsavedDraft -> discardDialogOpen = true
            else -> onExit()
        }
    }
    BackHandler(onBack = requestExit)
    content(requestExit)

    if (discardDialogOpen && hasUnsavedDraft && !submitPending) {
        AlertDialog(
            onDismissRequest = { discardDialogOpen = false },
            title = { Text("Απόρριψη νέας κίνησης;") },
            text = { Text("Τα στοιχεία που συμπλήρωσες δεν έχουν αποθηκευτεί.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        discardDialogOpen = false
                        if (!submitPending && hasUnsavedDraft) {
                            onAction(QuickEntryAction.Reset)
                            onExit()
                        }
                    },
                ) { Text("Απόρριψη αλλαγών") }
            },
            dismissButton = {
                TextButton(onClick = { discardDialogOpen = false }) { Text("Παραμονή στην επεξεργασία") }
            },
        )
    }
}