package app.myfinhub.android.feature.utilities

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.core.data.CanonicalBackupInspection
import app.myfinhub.android.core.data.inspectCanonicalFinanceBackupJson
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_INSPECT_BYTES = 16 * 1024 * 1024

/** Safe, bounded JSON file inspection only. No FinanceData import and no remote changes. */
@Composable
fun CanonicalFinanceBackupImportPreview() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<CanonicalBackupInspection?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        preview = null
        if (uri == null) {
            feedback = "Η επιλογή αρχείου ακυρώθηκε."
        } else {
            busy = true
            feedback = null
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        val source = context.contentResolver.openInputStream(uri)
                            ?: error("Document provider did not open backup file")
                        val bytes = ByteArrayOutputStream()
                        source.use { input ->
                            val buffer = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                if (n == 0) continue
                                total += n
                                require(total <= MAX_INSPECT_BYTES) {
                                    "File exceeds the 16 MiB read-only inspection limit"
                                }
                                bytes.write(buffer, 0, n)
                            }
                        }
                        inspectCanonicalFinanceBackupJson(bytes.toString(Charsets.UTF_8.name()))
                    }
                }
                busy = false
                preview = outcome.getOrNull()
                feedback = if (outcome.isSuccess) {
                    "Διαβάστηκε η βασική δομή του JSON. Δεν έγινε εισαγωγή ή αντικατάσταση."
                } else {
                    // Never log the URI, file content, account IDs, or parser diagnostics.
                    "Το αρχείο δεν μπορεί να επιθεωρηθεί ως πλήρες JSON MyFinHub " +
                        "(ή υπερβαίνει το όριο των 16 MiB)."
                }
            }
        }
    }

    MyFinHubSectionCard(
        modifier = Modifier.fillMaxWidth().testTag("s9_backup_import_preview"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Έλεγχος αρχείου backup", style = MaterialTheme.typography.titleLarge)
            Text(
                "Επίλεξε υπάρχον JSON για προεπισκόπηση της δομής του, " +
                    "χωρίς να αντικαταστήσεις τις κινήσεις ή τους λογαριασμούς σου.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = {
                    preview = null
                    feedback = null
                    picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("s9_backup_choose_for_preview"),
            ) { Text(if (busy) "Έλεγχος…" else "Επιλογή JSON για προεπισκόπηση") }
            feedback?.let {
                Text(it, modifier = Modifier.testTag("s9_backup_preview_feedback"),
                    style = MaterialTheme.typography.bodySmall)
            }
            preview?.let { data ->
                Text(
                    "Ταυτότητα: ${data.appIdentity} · Schema: ${data.schemaVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Λογαριασμοί: ${data.accounts} · Παλαιές κινήσεις: " +
                        "${data.legacyTransactions} · Συμβάντα: ${data.events} · " +
                        "Κάρτες: ${data.cards} · Budgets: ${data.budgets} · " +
                        "Πάγια: ${data.recurring} · Προγραμματισμένα: ${data.scheduled}",
                    modifier = Modifier.testTag("s9_backup_preview_summary"),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Η προεπισκόπηση ΔΕΝ σημαίνει ότι το αρχείο εγκρίθηκε για επαναφορά. " +
                        "Απαιτείται πλήρης έλεγχος schema, ιστορικών συσχετίσεων, " +
                        "δικαιωμάτων και συγχρονισμού πριν επιτραπεί αντικατάσταση. " +
                        "Τα δεδομένα αυτής της συσκευής δεν άλλαξαν.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
