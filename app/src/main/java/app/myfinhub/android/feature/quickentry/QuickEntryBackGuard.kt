package app.myfinhub.android.feature.quickentry

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Route-level guard shared by fast and complete Quick Entry editors. It catches system/predictive
 * Back as well as toolbar callbacks passed through [requestBack], and treats every state mutation
 * marked dirty by the reducer as draft content worth protecting.
 */
@Composable
fun QuickEntryBackGuard(
    state: QuickEntryUiState,
    onAction: (QuickEntryAction) -> Unit,
    onExit: () -> Unit,
    content: @Composable (requestBack: () -> Unit) -> Unit,
) {
    var discardDialogOpen by rememberSaveable { mutableStateOf(false) }
    val hasUnsavedDraft = state.dirty && !state.persisted && !state.pendingSync
    val requestBack = {
        if (hasUnsavedDraft) discardDialogOpen = true else onExit()
    }

    BackHandler(onBack = requestBack)
    content(requestBack)

    if (discardDialogOpen) {
        AlertDialog(
            onDismissRequest = { discardDialogOpen = false },
            title = { Text("Απόρριψη αλλαγών;") },
            text = { Text("Οι αλλαγές αυτής της καταχώρισης δεν έχουν αποθηκευτεί.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        discardDialogOpen = false
                        onAction(QuickEntryAction.Reset)
                        onExit()
                    },
                ) { Text("Απόρριψη") }
            },
            dismissButton = {
                TextButton(onClick = { discardDialogOpen = false }) { Text("Συνέχεια") }
            },
        )
    }
}