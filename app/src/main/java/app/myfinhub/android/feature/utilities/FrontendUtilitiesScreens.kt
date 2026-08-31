package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: FrontendUtilitiesUiState,
    onAction: (FrontendUtilitiesAction) -> Unit,
    onBack: () -> Unit,
) {
    UtilityScaffold(title = "Ρυθμίσεις", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Προτιμήσεις εφαρμογής", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text("Οι επιλογές παραμένουν τοπικές σε αυτή την έκδοση.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsCard(
                title = "Ιδιωτικότητα",
                rows = listOf(
                    SettingSpec("Απόκρυψη ποσών στην εκκίνηση", "Τα ποσά ξεκινούν κρυφά στην αρχική.", state.settings.hideAmountsOnStart, FrontendUtilitiesAction.ToggleHideAmountsOnStart),
                    SettingSpec("Επιπλέον έλεγχος σε ευαίσθητες οθόνες", "Ζητά πρόσθετη επιβεβαίωση πριν από ευαίσθητες ενέργειες.", state.settings.extraSensitiveScreenCheck, FrontendUtilitiesAction.ToggleExtraSensitiveScreenCheck),
                ),
                onAction = onAction,
            )
            SettingsCard(
                title = "Υπενθυμίσεις",
                rows = listOf(
                    SettingSpec("Προγραμματισμένες υποχρεώσεις", "Εμφάνιση υπενθυμίσεων για επερχόμενες υποχρεώσεις.", state.settings.remindersEnabled, FrontendUtilitiesAction.ToggleReminders),
                ),
                onAction = onAction,
            )
        }
    }
}

private data class SettingSpec(
    val title: String,
    val subtitle: String,
    val checked: Boolean,
    val action: FrontendUtilitiesAction,
)

@Composable
private fun SettingsCard(
    title: String,
    rows: List<SettingSpec>,
    onAction: (FrontendUtilitiesAction) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            rows.forEachIndexed { index, row ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(row.title, fontWeight = FontWeight.SemiBold)
                        Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = row.checked, onCheckedChange = { onAction(row.action) })
                }
                if (index != rows.lastIndex) HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataTransferScreen(
    state: FrontendUtilitiesUiState,
    onAction: (FrontendUtilitiesAction) -> Unit,
    onBack: () -> Unit,
) {
    if (state.importConfirmationOpen) {
        AlertDialog(
            onDismissRequest = { onAction(FrontendUtilitiesAction.CancelReplaceImport) },
            title = { Text("Αντικατάσταση όλων των δεδομένων;") },
            text = { Text("Η εισαγωγή μπορεί να αντικαταστήσει τα τρέχοντα δεδομένα. Έλεγξε το αντίγραφο πριν συνεχίσεις.") },
            confirmButton = {
                Button(onClick = { onAction(FrontendUtilitiesAction.ConfirmReplaceImport) }) { Text("Επιβεβαίωση αντικατάστασης") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(FrontendUtilitiesAction.CancelReplaceImport) }) { Text("Ακύρωση") }
            },
        )
    }
    UtilityScaffold(title = "Εισαγωγή & αντίγραφα", onBack = onBack) { modifier ->
        Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Αντίγραφο ασφαλείας", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Εξαγωγή", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Προετοίμασε μια ασφαλή προεπισκόπηση πριν αποθηκευτεί οποιοδήποτε αρχείο.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { onAction(FrontendUtilitiesAction.PrepareBackupPreview) }, modifier = Modifier.fillMaxWidth()) { Text("Προετοιμασία αντιγράφου") }
                    state.backupMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Εισαγωγή", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Δες πρώτα μια περίληψη και επιβεβαίωσε ρητά πριν από αντικατάσταση δεδομένων.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { onAction(FrontendUtilitiesAction.PrepareImportPreview) }, modifier = Modifier.fillMaxWidth()) { Text("Προεπισκόπηση εισαγωγής") }
                    if (state.importPreviewReady) {
                        Text("Δείγμα αντιγράφου · 24 κινήσεις · 3 λογαριασμοί", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { onAction(FrontendUtilitiesAction.RequestReplaceImport) }, modifier = Modifier.fillMaxWidth()) { Text("Αντικατάσταση δεδομένων") }
                    }
                    state.importMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeHistoryScreen(
    state: FrontendUtilitiesUiState,
    onAction: (FrontendUtilitiesAction) -> Unit,
    onBack: () -> Unit,
) {
    UtilityScaffold(title = "Ιστορικό αλλαγών", onBack = onBack) { modifier ->
        Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Αναίρεση & επανάληψη", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text("Το ιστορικό δεν εμφανίζει ποσά, αριθμούς καρτών ή άλλες ευαίσθητες λεπτομέρειες.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onAction(FrontendUtilitiesAction.Undo) }, enabled = state.historyCursor > 0, modifier = Modifier.weight(1f)) { Text("Αναίρεση") }
                OutlinedButton(onClick = { onAction(FrontendUtilitiesAction.Redo) }, enabled = state.historyCursor < state.history.size, modifier = Modifier.weight(1f)) { Text("Επανάληψη") }
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.history.forEachIndexed { index, entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(entry.title, fontWeight = FontWeight.SemiBold)
                            Text("${entry.timeLabel} · ${if (index < state.historyCursor) "Εφαρμοσμένη" else "Αναιρεμένη"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (index != state.history.lastIndex) HorizontalDivider()
                    }
                }
            }
            Text("${state.historyCursor} από ${state.history.size} αλλαγές εφαρμοσμένες", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UtilityScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Πίσω") } },
            )
        },
    ) { padding ->
        content(Modifier.fillMaxSize().padding(padding).padding(20.dp))
    }
}
