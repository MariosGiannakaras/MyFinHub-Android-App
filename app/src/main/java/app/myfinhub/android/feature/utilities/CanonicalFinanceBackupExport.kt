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
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** The picker may return after the finance document or mutation state has changed. */
internal fun isCurrentBackupExportCandidate(
    selectedSnapshot: JsonObject?,
    currentDocument: JsonObject?,
    pendingChangeCount: Int,
    mutationBlocked: Boolean,
): Boolean = selectedSnapshot != null &&
    currentDocument != null &&
    selectedSnapshot == currentDocument &&
    pendingChangeCount == 0 &&
    !mutationBlocked

/**
 * Export the complete canonical Web FinanceData document via Android's document picker.
 *
 * No temporary file, automatic cloud upload, authentication token, vault secrets or
 * production mutation is involved. Export is blocked while the server snapshot differs
 * from locally pending edits, to prevent a misleading "fully backed up" claim.
 *
 * Import/restore is intentionally NOT available without a separate schema/ownership,
 * revision and security review.
 */
@Composable
fun CanonicalFinanceBackupExport(
    canonicalDocument: JsonObject?,
    pendingChangeCount: Int,
    mutationBlocked: Boolean,
    offline: Boolean,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingSnapshot by remember { mutableStateOf<JsonObject?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val selectedSnapshot = pendingSnapshot
        pendingSnapshot = null
        if (uri == null) {
            feedback = "Η εξαγωγή ακυρώθηκε."
        } else if (!isCurrentBackupExportCandidate(
                selectedSnapshot, canonicalDocument, pendingChangeCount, mutationBlocked,
            )) {
            // The document picker leaves the app: recheck the live finance snapshot
            // before writing an unencrypted file. Never export stale or pending data.
            feedback = "Τα οικονομικά δεδομένα άλλαξαν ή εκκρεμεί συγχρονισμός. " +
                "Ξεκίνησε νέα εξαγωγή όταν ολοκληρωθεί."
        } else {
            exporting = true
            feedback = null
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val prettyJson = Json { prettyPrint = true }
                        val writer = context.contentResolver.openOutputStream(uri, "wt")
                            ?: error("Document provider did not create an output stream")
                        writer.bufferedWriter(Charsets.UTF_8).use { out ->
                            out.write(prettyJson.encodeToString(JsonObject.serializer(), selectedSnapshot!!))
                            out.flush()
                        }
                    }
                }
                exporting = false
                feedback = if (result.isSuccess) {
                    "Το JSON αποθηκεύτηκε στην τοποθεσία που επέλεξες."
                } else {
                    // Avoid logging provider URIs, file paths or the user's financial document.
                    "Η αποθήκευση απέτυχε. Δοκίμασε άλλη τοποθεσία."
                }
            }
        }
    }

    MyFinHubSectionCard(
        modifier = Modifier.fillMaxWidth().testTag("s9_finance_backup_export"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Αντίγραφο οικονομικών δεδομένων",
                style = MaterialTheme.typography.titleLarge)
            Text(
                "Εξαγωγή του πλήρους JSON MyFinHub, με λογαριασμούς, κινήσεις, " +
                    "στόχους, ρυθμίσεις και ιστορικό. Η εξαγωγή δεν τροποποιεί τα δεδομένα.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Προσοχή: το αρχείο δεν είναι κρυπτογραφημένο. " +
                    "Αποθήκευσέ το μόνο σε ασφαλή τοποθεσία. " +
                    "Οι αριθμοί καρτών, η λήξη και το CVV του ασφαλούς vault " +
                    "δεν περιλαμβάνονται στο οικονομικό JSON.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (offline) {
                Text(
                    "Εκτός σύνδεσης: το διαθέσιμο αντίγραφο μπορεί να μην έχει " +
                        "τις νεότερες αλλαγές άλλων συσκευών.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (pendingChangeCount > 0) {
                Text(
                    "Υπάρχουν ${pendingChangeCount} εκκρεμείς αλλαγές. " +
                        "Περίμενε τον συγχρονισμό πριν εξαγάγεις συνεπές αντίγραφο.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(
                enabled = isCurrentBackupExportCandidate(
                    canonicalDocument, canonicalDocument, pendingChangeCount, mutationBlocked,
                ) && !exporting && pendingSnapshot == null,
                modifier = Modifier.fillMaxWidth().testTag("s9_finance_backup_save"),
                onClick = {
                    val snapshot = canonicalDocument
                        ?.takeIf {
                            isCurrentBackupExportCandidate(
                                it, canonicalDocument, pendingChangeCount, mutationBlocked,
                            )
                        } ?: return@TextButton
                    pendingSnapshot = snapshot
                    feedback = null
                    val filename = "MyFinHub-backup-" +
                        Instant.now().toString().replace(':', '-').replace('.', '-') + ".json"
                    launcher.launch(filename)
                },
            ) { Text(if (exporting) "Αποθήκευση…" else "Εξαγωγή JSON") }
            feedback?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("s9_finance_backup_feedback"),
                )
            }
        }
    }
}
