package app.myfinhub.android.feature.quickentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntryScreen(
    state: QuickEntryUiState,
    onAction: (QuickEntryAction) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Νέα κίνηση", modifier = Modifier.semantics { heading() }) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Πίσω") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Τι θέλεις να καταχωρίσεις;",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickEntryKind.entries.take(2).forEach { kind ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = { onAction(QuickEntryAction.SelectKind(kind)) },
                        label = { Text(kind.label) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickEntryKind.entries.drop(2).forEach { kind ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = { onAction(QuickEntryAction.SelectKind(kind)) },
                        label = { Text(kind.label) },
                    )
                }
            }
            OutlinedTextField(
                value = state.amountText,
                onValueChange = { onAction(QuickEntryAction.AmountChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ποσό") },
                suffix = { Text("€") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = { onAction(QuickEntryAction.NoteChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Περιγραφή") },
            )
            if (state.kind == QuickEntryKind.EXPENSE || state.kind == QuickEntryKind.CARD_PAYMENT) {
                OutlinedTextField(
                    value = state.category,
                    onValueChange = { onAction(QuickEntryAction.CategoryChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Κατηγορία") },
                    singleLine = true,
                )
            }
            if (state.kind == QuickEntryKind.TRANSFER) {
                OutlinedTextField(
                    value = state.destination,
                    onValueChange = { onAction(QuickEntryAction.DestinationChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Προς λογαριασμό") },
                    singleLine = true,
                )
                Text(
                    "Οι εσωτερικές μεταφορές δεν μετρούν ως έσοδα ή έξοδα.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.kind == QuickEntryKind.SPLIT) {
                Text("Άτομα: ${state.splitPeople}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onAction(QuickEntryAction.SplitPeopleChanged(state.splitPeople - 1)) },
                    ) { Text("−") }
                    OutlinedButton(
                        onClick = { onAction(QuickEntryAction.SplitPeopleChanged(state.splitPeople + 1)) },
                    ) { Text("+") }
                }
            }
            state.validationMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            state.savedSummary?.let { summary ->
                Text(
                    text = "Έτοιμο: $summary",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Στο prototype δεν γράφεται πραγματικό FinanceData μέχρι να ενεργοποιηθεί το canonical API.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { onAction(QuickEntryAction.Save) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Αποθήκευση κίνησης")
            }
        }
    }
}
