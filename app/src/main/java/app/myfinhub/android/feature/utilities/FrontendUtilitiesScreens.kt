package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing

@Composable
fun SettingsScreen(
    state: FrontendUtilitiesUiState,
    onAction: (FrontendUtilitiesAction) -> Unit,
    onBack: () -> Unit,
    diagnostics: AppDiagnosticsSnapshot? = null,
) {
    UtilityScaffold(
        title = "Ρυθμίσεις",
        subtitle = "Προτιμήσεις εφαρμογής",
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            Text(
                "Οι επιλογές παραμένουν τοπικές σε αυτή την έκδοση.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsCard(
                title = "Ιδιωτικότητα",
                rows = listOf(
                    SettingSpec(
                        "Απόκρυψη ποσών στην εκκίνηση",
                        "Τα ποσά ξεκινούν κρυφά στην αρχική.",
                        state.settings.hideAmountsOnStart,
                        FrontendUtilitiesAction.ToggleHideAmountsOnStart,
                    ),
                    SettingSpec(
                        "Επιπλέον έλεγχος σε ευαίσθητες οθόνες",
                        "Ζητά πρόσθετη επιβεβαίωση πριν από ευαίσθητες ενέργειες.",
                        state.settings.extraSensitiveScreenCheck,
                        FrontendUtilitiesAction.ToggleExtraSensitiveScreenCheck,
                    ),
                ),
                onAction = onAction,
            )
            SettingsCard(
                title = "Υπενθυμίσεις",
                rows = listOf(
                    SettingSpec(
                        "Προγραμματισμένες υποχρεώσεις",
                        "Εμφάνιση υπενθυμίσεων για επερχόμενες υποχρεώσεις.",
                        state.settings.remindersEnabled,
                        FrontendUtilitiesAction.ToggleReminders,
                    ),
                ),
                onAction = onAction,
            )
            diagnostics?.let { DiagnosticsCard(it) }
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
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                ) {
                    MyFinHubIconBadge(
                        icon = if (title == "Υπενθυμίσεις") MyFinHubIcons.Attention else MyFinHubIcons.Goal,
                        tone = if (title == "Υπενθυμίσεις") FinanceTone.Attention else FinanceTone.Neutral,
                        contentDescription = null,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            row.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = row.checked, onCheckedChange = { onAction(row.action) })
                }
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

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
                Button(onClick = { onAction(FrontendUtilitiesAction.ConfirmReplaceImport) }) {
                    Text("Επιβεβαίωση αντικατάστασης")
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(FrontendUtilitiesAction.CancelReplaceImport) }) {
                    Text("Ακύρωση")
                }
            },
        )
    }
    UtilityScaffold(
        title = "Εισαγωγή & αντίγραφα",
        subtitle = "Ασφαλής μεταφορά δεδομένων",
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            DataTransferCard(
                title = "Εξαγωγή",
                body = "Προετοίμασε μια ασφαλή προεπισκόπηση πριν αποθηκευτεί οποιοδήποτε αρχείο.",
                tone = FinanceTone.Transfer,
            ) {
                OutlinedButton(
                    onClick = { onAction(FrontendUtilitiesAction.PrepareBackupPreview) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Προετοιμασία αντιγράφου")
                }
                state.backupMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DataTransferCard(
                title = "Εισαγωγή",
                body = "Δες πρώτα μια περίληψη και επιβεβαίωσε ρητά πριν από αντικατάσταση δεδομένων.",
                tone = FinanceTone.Attention,
            ) {
                OutlinedButton(
                    onClick = { onAction(FrontendUtilitiesAction.PrepareImportPreview) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Προεπισκόπηση εισαγωγής")
                }
                if (state.importPreviewReady) {
                    Text(
                        "Δείγμα αντιγράφου · 24 κινήσεις · 3 λογαριασμοί",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { onAction(FrontendUtilitiesAction.RequestReplaceImport) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("Αντικατάσταση δεδομένων")
                    }
                }
                state.importMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DataTransferCard(
    title: String,
    body: String,
    tone: FinanceTone,
    content: @Composable ColumnScope.() -> Unit,
) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MyFinHubIconBadge(
                    icon = MyFinHubIcons.Transfer,
                    tone = tone,
                    contentDescription = null,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}


@Composable
private fun DiagnosticsCard(diagnostics: AppDiagnosticsSnapshot) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text("Διαγνωστικά", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Ασφαλείς τεχνικές πληροφορίες για σύνδεση και συγχρονισμό. Δεν περιλαμβάνονται οικονομικά δεδομένα ή μυστικά.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DiagnosticRow("Έκδοση", "${diagnostics.versionName} · ${diagnostics.buildType}")
            DiagnosticRow("Περιβάλλον", diagnostics.environment)
            DiagnosticRow("API", diagnostics.apiHost)
            DiagnosticRow("Δίκτυο", diagnostics.networkStatus)
            DiagnosticRow("Κατάσταση API", diagnostics.apiStatus)
            DiagnosticRow("Συνεδρία", diagnostics.sessionStatus)
            DiagnosticRow("Τελευταίος συγχρονισμός", diagnostics.lastSuccessfulSync ?: "Δεν υπάρχει ακόμη")
            DiagnosticRow("Τελευταίος κωδικός", diagnostics.lastDiagnosticCode ?: "Κανένας")
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun ChangeHistoryScreen(
    state: FrontendUtilitiesUiState,
    onAction: (FrontendUtilitiesAction) -> Unit,
    onBack: () -> Unit,
) {
    UtilityScaffold(
        title = "Ιστορικό αλλαγών",
        subtitle = "Αναίρεση & επανάληψη",
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            Text(
                "Το ιστορικό δεν εμφανίζει ποσά, αριθμούς καρτών ή άλλες ευαίσθητες λεπτομέρειες.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                OutlinedButton(
                    onClick = { onAction(FrontendUtilitiesAction.Undo) },
                    enabled = state.historyCursor > 0,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Αναίρεση")
                }
                OutlinedButton(
                    onClick = { onAction(FrontendUtilitiesAction.Redo) },
                    enabled = state.historyCursor < state.history.size,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Επανάληψη")
                }
            }
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    state.history.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MyFinHubIconBadge(
                                icon = MyFinHubIcons.Activity,
                                tone = if (index < state.historyCursor) FinanceTone.Savings else FinanceTone.Neutral,
                                contentDescription = null,
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${entry.timeLabel} · ${if (index < state.historyCursor) "Εφαρμοσμένη" else "Αναιρεμένη"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (index != state.history.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            Text(
                "${state.historyCursor} από ${state.history.size} αλλαγές εφαρμοσμένες",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(MyFinHubSpacing.xs))
        }
    }
}

@Composable
private fun UtilityScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = title,
                subtitle = subtitle,
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(MyFinHubIcons.Back, contentDescription = "Πίσω")
                    }
                },
            )
        },
    ) { padding ->
        content(Modifier.fillMaxSize().padding(padding).padding(MyFinHubSpacing.lg))
    }
}
