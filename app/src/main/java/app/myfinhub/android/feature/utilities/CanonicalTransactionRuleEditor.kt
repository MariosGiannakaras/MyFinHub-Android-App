package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.plan.PlanningRule

/** Web TransactionRulesWorkspace native editor; no historical event is rewritten. */
@Composable
fun CanonicalTransactionRuleEditor(
    initial: PlanningRule?,
    categories: List<String>,
    subcategories: Map<String, List<String>>,
    accountOptions: List<Pair<String, String>>,
    mutationBlocked: Boolean,
    onDismiss: () -> Unit,
    onSave: (TransactionRuleEditRequest) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var description by rememberSaveable(initial?.id) { mutableStateOf(initial?.matchDescription.orEmpty()) }
    var merchant by rememberSaveable(initial?.id) { mutableStateOf(initial?.matchMerchant.orEmpty()) }
    var account by rememberSaveable(initial?.id) { mutableStateOf(initial?.matchAccountId.orEmpty()) }
    var equalsMode by rememberSaveable(initial?.id) { mutableStateOf(initial?.matchMode == "equals") }
    var category by rememberSaveable(initial?.id) { mutableStateOf(initial?.actionCategory.orEmpty()) }
    var subcategory by rememberSaveable(initial?.id) { mutableStateOf(initial?.actionSubcategory.orEmpty()) }
    var note by rememberSaveable(initial?.id) { mutableStateOf(initial?.actionNote.orEmpty()) }
    var manual by rememberSaveable(initial?.id) { mutableStateOf(initial?.scopes?.contains("manual") ?: true) }
    var imported by rememberSaveable(initial?.id) { mutableStateOf(initial?.scopes?.contains("imported") ?: false) }
    var review by rememberSaveable(initial?.id) { mutableStateOf(initial?.scopes?.contains("review") ?: false) }
    var accountMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var subcategoryMenu by remember { mutableStateOf(false) }
    val knownSubcategory = subcategory.isEmpty() ||
        subcategories[category].orEmpty().contains(subcategory) ||
        (subcategory == initial?.actionSubcategory && category == initial?.actionCategory)
    val hasCondition = description.isNotBlank() || merchant.isNotBlank() || account.isNotBlank()
    val hasAction = category.isNotBlank() || subcategory.isNotBlank() || note.isNotBlank()
    val knownAccount = account.isBlank() || accountOptions.any { it.first == account } ||
        account == initial?.matchAccountId
    val knownCategory = category.isBlank() || categories.isEmpty() ||
        category in categories || category == initial?.actionCategory
    val canSave = !mutationBlocked && name.isNotBlank() && name.length <= 500 &&
        hasCondition && hasAction && (manual || imported || review) &&
        knownAccount && knownCategory && knownSubcategory &&
        (subcategory.isBlank() || category.isNotBlank())

    AlertDialog(
        onDismissRequest = { if (!mutationBlocked) onDismiss() },
        title = { Text(if (initial == null) "Νέος κανόνας" else "Επεξεργασία κανόνα") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            ) {
                Text(
                    "Οι κανόνες εφαρμόζονται μόνο σε νέες κινήσεις. " +
                        "Δεν επεξεργάζονται το υπάρχον ιστορικό.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Όνομα") }, singleLine = true,
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("s9_rule_editor_name"),
                )
                Text("Όταν", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Περιγραφή (προαιρετική)") }, singleLine = true,
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("s9_rule_editor_description"),
                )
                TextButton(
                    onClick = { equalsMode = !equalsMode }, enabled = !mutationBlocked,
                    modifier = Modifier.testTag("s9_rule_editor_mode"),
                ) { Text(if (equalsMode) "Ακριβής αντιστοίχιση" else "Περιέχει τη φράση") }
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text("Έμπορος (προαιρετικό)") }, singleLine = true,
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("s9_rule_editor_merchant"),
                )
                TextButton(
                    onClick = { accountMenu = true }, enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("s9_rule_editor_account"),
                ) {
                    Text(accountOptions.firstOrNull { it.first == account }?.second
                        ?: if (account.isEmpty()) "Όλοι οι λογαριασμοί" else account)
                }
                DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Όλοι οι λογαριασμοί") },
                        onClick = { account = ""; accountMenu = false },
                    )
                    accountOptions.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { account = id; accountMenu = false },
                        )
                    }
                }
                Text("Τότε", style = MaterialTheme.typography.titleSmall)
                TextButton(
                    onClick = { categoryMenu = true }, enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("s9_rule_editor_category"),
                ) { Text(if (category.isEmpty()) "Χωρίς αλλαγή κατηγορίας" else "Κατηγορία: $category") }
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Χωρίς αλλαγή κατηγορίας") },
                        onClick = { category = ""; subcategory = ""; categoryMenu = false },
                    )
                    categories.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item) },
                            onClick = {
                                if (category != item) subcategory = ""
                                category = item; categoryMenu = false
                            },
                        )
                    }
                }
                TextButton(
                    onClick = { subcategoryMenu = true },
                    enabled = !mutationBlocked && category.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("s9_rule_editor_subcategory"),
                ) { Text(if (subcategory.isBlank()) "Χωρίς υποκατηγορία" else "Υποκατηγορία: $subcategory") }
                DropdownMenu(
                    expanded = subcategoryMenu,
                    onDismissRequest = { subcategoryMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Χωρίς υποκατηγορία") },
                        onClick = { subcategory = ""; subcategoryMenu = false },
                    )
                    subcategories[category].orEmpty().forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item) },
                            onClick = { subcategory = item; subcategoryMenu = false },
                        )
                    }
                    if (subcategory.isNotBlank() &&
                        subcategory !in subcategories[category].orEmpty()
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ιστορική επιλογή: $subcategory") },
                            onClick = { subcategoryMenu = false },
                        )
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Σχόλιο όταν είναι κενό") }, singleLine = true,
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("s9_rule_editor_note"),
                )
                Text("Εφαρμογή σε", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = manual, onCheckedChange = { manual = it },
                        enabled = !mutationBlocked,
                    )
                    Text("Χειροκίνητες")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = imported, onCheckedChange = { imported = it },
                        enabled = !mutationBlocked,
                    )
                    Text("Εισαγωγές")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = review, onCheckedChange = { review = it },
                        enabled = !mutationBlocked,
                    )
                    Text("Επιβεβαιώσεις")
                }
                if (!hasCondition) {
                    Text("Χρειάζεται τουλάχιστον μία συνθήκη.",
                        color = MaterialTheme.colorScheme.error)
                }
                if (!hasAction) {
                    Text("Χρειάζεται τουλάχιστον μία ενέργεια.",
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                modifier = Modifier.testTag("s9_rule_editor_save"),
                onClick = {
                    onSave(TransactionRuleEditRequest(
                        id = initial?.id,
                        name = name,
                        matchDescription = description,
                        matchMerchant = merchant,
                        matchAccountId = account,
                        matchMode = if (equalsMode) "equals" else "contains",
                        actionCategory = category,
                        actionSubcategory = subcategory,
                        actionNote = note,
                        scopes = buildList {
                            if (manual) add("manual")
                            if (imported) add("imported")
                            if (review) add("review")
                        },
                    ))
                },
            ) { Text("Αποθήκευση") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !mutationBlocked) { Text("Ακύρωση") }
        },
    )
}
