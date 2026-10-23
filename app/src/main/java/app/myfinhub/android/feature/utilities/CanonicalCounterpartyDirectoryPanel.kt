package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.data.CanonicalCounterpartyRow
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing

data class CanonicalCounterpartyEditRequest(
    val id: String?,
    val kind: String,
    val displayName: String,
    val aliases: List<String>,
)

/** Web Settings CounterpartyDirectory: editable identities, no guesses or historical rewrites. */
@Composable
fun CanonicalCounterpartyDirectoryPanel(
    rows: List<CanonicalCounterpartyRow>,
    mutationBlocked: Boolean,
    onSave: (CanonicalCounterpartyEditRequest) -> Unit,
) {
    var showEditor by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var count by rememberSaveable { mutableIntStateOf(16) }
    val current = editingId?.let { id -> rows.singleOrNull { it.id == id } }
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth().testTag("s9_counterparty_directory")) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Έμποροι και δικαιούχοι", style = MaterialTheme.typography.titleLarge)
            Text(
                "Προαιρετικές ταυτότητες και εναλλακτικές ονομασίες. " +
                    "Οι προηγούμενες συναλλαγές και σημειώσεις παραμένουν ανέπαφες.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                enabled = !mutationBlocked && rows.size < 2000,
                onClick = { editingId = null; showEditor = true },
                modifier = Modifier.fillMaxWidth().testTag("s9_counterparty_create"),
            ) { Text("Νέος έμπορος ή δικαιούχος") }
            if (rows.isEmpty()) {
                Text("Δεν έχουν καταχωριστεί ταυτότητες.",
                    style = MaterialTheme.typography.bodySmall)
            }
            rows.take(count).forEachIndexed { index, item ->
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                    Text(item.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        (if (item.kind == "merchant") "Έμπορος" else "Δικαιούχος") +
                            if (item.aliases.isEmpty()) "" else
                                " · ${item.aliases.size} εναλλακτικές ονομασίες",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { editingId = item.id; showEditor = true },
                        enabled = !mutationBlocked && item.id.isNotBlank() &&
                            rows.count { it.id == item.id } == 1,
                        modifier = Modifier.testTag("s9_counterparty_edit_$index"),
                    ) { Text("Επεξεργασία") }
                }
            }
            if (rows.size > count) {
                TextButton(onClick = { count = (count + 16).coerceAtMost(rows.size) }) {
                    Text("Περισσότεροι αντισυμβαλλόμενοι (${rows.size - count})")
                }
            }
        }
    }
    if (showEditor && (editingId == null || current != null)) {
        CanonicalCounterpartyEditDialog(
            initial = current,
            mutationBlocked = mutationBlocked,
            onDismiss = { showEditor = false; editingId = null },
            onSave = { request ->
                onSave(request)
                showEditor = false
                editingId = null
            },
        )
    }
}

@Composable
private fun CanonicalCounterpartyEditDialog(
    initial: CanonicalCounterpartyRow?,
    mutationBlocked: Boolean,
    onDismiss: () -> Unit,
    onSave: (CanonicalCounterpartyEditRequest) -> Unit,
) {
    var kind by rememberSaveable(initial?.id) { mutableStateOf(initial?.kind ?: "merchant") }
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.displayName.orEmpty()) }
    var aliases by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.aliases?.joinToString("\n").orEmpty())
    }
    val aliasRows = aliases.lines().map(String::trim).filter(String::isNotEmpty).distinct()
    val valid = name.trim().length in 2..120 && aliases.length <= 1935 &&
        aliasRows.size <= 16 && aliasRows.all { it.length <= 120 } &&
        kind in setOf("merchant", "beneficiary")

    AlertDialog(
        onDismissRequest = { if (!mutationBlocked) onDismiss() },
        title = { Text(if (initial == null) "Νέα ταυτότητα" else "Επεξεργασία ταυτότητας") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            ) {
                TextButton(
                    enabled = !mutationBlocked && initial == null,
                    onClick = { kind = if (kind == "merchant") "beneficiary" else "merchant" },
                    modifier = Modifier.fillMaxWidth().testTag("s9_counterparty_kind"),
                ) {
                    Text(if (kind == "merchant") "Τύπος: Έμπορος" else "Τύπος: Δικαιούχος")
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    enabled = !mutationBlocked,
                    label = { Text("Όνομα") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("s9_counterparty_name"),
                )
                OutlinedTextField(
                    value = aliases,
                    onValueChange = { aliases = it.take(1935) },
                    enabled = !mutationBlocked,
                    label = { Text("Εναλλακτικά ονόματα, ένα ανά γραμμή") },
                    minLines = 2, maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("s9_counterparty_aliases"),
                )
                if (!valid) Text(
                    "Δώσε όνομα 2–120 χαρακτήρων και έως 16 έγκυρα ψευδώνυμα.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !mutationBlocked,
                modifier = Modifier.testTag("s9_counterparty_save"),
                onClick = {
                    onSave(CanonicalCounterpartyEditRequest(
                        id = initial?.id,
                        kind = kind,
                        displayName = name.trim(),
                        aliases = aliasRows,
                    ))
                },
            ) { Text("Αποθήκευση") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !mutationBlocked) { Text("Ακύρωση") }
        },
    )
}
