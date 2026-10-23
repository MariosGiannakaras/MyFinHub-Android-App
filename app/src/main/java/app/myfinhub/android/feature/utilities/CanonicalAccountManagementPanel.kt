package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import app.myfinhub.android.core.data.CanonicalAccount
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing

/** Account finance settings do not contain any sensitive owner+AAL2 IBAN metadata. */
data class ManagedAccountEditRequest(
    val id: String?,
    val name: String,
    val mode: String,
    val providerId: String?,
    val providerName: String?,
    val bankCategory: String?,
    val cashType: String?,
)

/** Web AccountManagementSettings: account directory and selected default source roles. */
@Composable
fun CanonicalAccountManagementPanel(
    accounts: List<CanonicalAccount>,
    defaults: Map<String, String>,
    providers: List<Pair<String, String>>,
    mutationBlocked: Boolean,
    onSave: (ManagedAccountEditRequest) -> Unit,
    onChooseDefault: (String, String) -> Unit,
) {
    var editorOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var limit by rememberSaveable { mutableIntStateOf(12) }
    var defaultMenu by remember { mutableStateOf<String?>(null) }
    val eligible = accounts.filter {
        it.id.isNotBlank() && it.kind != "credit" &&
            it.showInQuickChoices && !it.excludeFromAvailable &&
            accounts.count { candidate -> candidate.id == it.id } == 1
    }
    val selected = editingId?.let { id -> accounts.singleOrNull { it.id == id } }
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth().testTag("s9_managed_accounts")) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Διαχείριση λογαριασμών", style = MaterialTheme.typography.titleLarge)
            Text(
                "Οι προεπιλογές ισχύουν για τις επόμενες κινήσεις. " +
                    "Η επεξεργασία δεν αλλάζει παλιές κινήσεις, υπόλοιπα ή τα προστατευμένα IBAN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOf(
                "defaultExpenseAccount" to "Έξοδα",
                "defaultIncomeAccount" to "Έσοδα",
                "defaultLoanAccount" to "Πληρωμές δόσεων",
            ).forEach { (role, title) ->
                val id = defaults[role]
                val current = accounts.singleOrNull { it.id == id }
                TextButton(
                    enabled = !mutationBlocked && eligible.isNotEmpty(),
                    onClick = { defaultMenu = role },
                    modifier = Modifier.fillMaxWidth().testTag("s9_default_account_$role"),
                ) {
                    Text("$title: " + (current?.name ?: "Χωρίς προεπιλογή"))
                }
                DropdownMenu(
                    expanded = defaultMenu == role,
                    onDismissRequest = { defaultMenu = null },
                ) {
                    eligible.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.name) },
                            onClick = {
                                defaultMenu = null
                                if (account.id != id) onChooseDefault(role, account.id)
                            },
                        )
                    }
                }
            }
            TextButton(
                enabled = !mutationBlocked && accounts.size < 2000,
                onClick = { editingId = null; editorOpen = true },
                modifier = Modifier.fillMaxWidth().testTag("s9_account_create"),
            ) { Text("Νέος λογαριασμός") }
            accounts.take(limit).forEachIndexed { index, account ->
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                    Text(account.name, style = MaterialTheme.typography.titleMedium)
                    val label = if (account.kind == "cash") {
                        if (account.cashType == "reserve" || account.cashRole == "reserve")
                            "Καβάτζα" else "Μετρητά"
                    } else if (account.bankAccountCategory == "term") {
                        "Προθεσμιακός"
                    } else account.provider ?: account.providerId ?: "Τραπεζικός λογαριασμός"
                    Text(label, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(
                        enabled = !mutationBlocked && account.id.isNotBlank() &&
                            accounts.count { it.id == account.id } == 1,
                        onClick = { editingId = account.id; editorOpen = true },
                        modifier = Modifier.testTag("s9_account_edit_$index"),
                    ) { Text("Επεξεργασία") }
                }
            }
            if (accounts.size > limit) TextButton(
                onClick = { limit = (limit + 12).coerceAtMost(accounts.size) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Περισσότεροι λογαριασμοί (${accounts.size - limit})") }
        }
    }
    if (editorOpen && (editingId == null || selected != null)) {
        CanonicalAccountProfileDialog(
            initial = selected,
            providers = providers,
            mutationBlocked = mutationBlocked,
            onDismiss = { editorOpen = false; editingId = null },
            onSave = { request ->
                onSave(request)
                editorOpen = false
                editingId = null
            },
        )
    }
}

@Composable
private fun CanonicalAccountProfileDialog(
    initial: CanonicalAccount?,
    providers: List<Pair<String, String>>,
    mutationBlocked: Boolean,
    onDismiss: () -> Unit,
    onSave: (ManagedAccountEditRequest) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var mode by rememberSaveable(initial?.id) {
        mutableStateOf(if (initial?.kind == "cash") "cash" else "bank")
    }
    var providerId by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.providerId.orEmpty())
    }
    var bankCategory by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.bankAccountCategory ?: "current")
    }
    var cashType by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.cashType ?: if (initial?.cashRole == "reserve") "reserve" else "cash")
    }
    var menu by remember { mutableStateOf<String?>(null) }
    val allProviders = (providers + listOfNotNull(
        initial?.providerId?.takeIf(String::isNotBlank)?.let {
            it to (initial.provider ?: it)
        },
    )).filter { it.first.isNotBlank() }.distinctBy { it.first }
    val bankCategories = listOf(
        "payroll" to "Μισθοδοσίας",
        "current" to "Τρεχούμενος",
        "savings" to "Αποταμιευτικός",
        "term" to "Προθεσμιακός",
        "payment" to "Πληρωμών / Wallet",
        "other" to "Άλλος",
    )
    val cashCategories = listOf("cash" to "Μετρητά", "reserve" to "Καβάτζα",
        "other" to "Άλλο")
    val valid = name.trim().length in 1..120 && mode in setOf("bank", "cash") &&
        (if (mode == "cash") cashCategories.any { it.first == cashType }
            else allProviders.any { it.first == providerId } &&
                bankCategories.any { it.first == bankCategory })
    AlertDialog(
        onDismissRequest = { if (!mutationBlocked) onDismiss() },
        title = { Text(if (initial == null) "Νέος λογαριασμός" else "Επεξεργασία λογαριασμού") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            ) {
                Text(
                    "Τα IBAN διαχειρίζονται ξεχωριστά στην προστατευμένη οθόνη " +
                        "λεπτομερειών λογαριασμού.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(120) },
                    enabled = !mutationBlocked,
                    label = { Text("Όνομα λογαριασμού") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("s9_account_name"),
                )
                TextButton(
                    enabled = !mutationBlocked && initial == null,
                    onClick = { mode = if (mode == "bank") "cash" else "bank" },
                    modifier = Modifier.fillMaxWidth().testTag("s9_account_mode"),
                ) { Text(if (mode == "cash") "Μετρητά" else "Τραπεζικός λογαριασμός") }
                if (mode == "bank") {
                    TextButton(
                        onClick = { menu = "providers" }, enabled = !mutationBlocked,
                        modifier = Modifier.fillMaxWidth().testTag("s9_account_provider"),
                    ) {
                        Text("Πάροχος: " + (allProviders.singleOrNull {
                            it.first == providerId
                        }?.second ?: "Επιλογή"))
                    }
                    DropdownMenu(
                        expanded = menu == "providers",
                        onDismissRequest = { menu = null },
                    ) {
                        allProviders.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { providerId = id; menu = null },
                            )
                        }
                    }
                    TextButton(
                        onClick = { menu = "bankCategory" }, enabled = !mutationBlocked,
                        modifier = Modifier.fillMaxWidth().testTag("s9_account_category"),
                    ) { Text("Κατηγορία: " +
                        (bankCategories.firstOrNull { it.first == bankCategory }?.second ?: "Επιλογή")) }
                    DropdownMenu(
                        expanded = menu == "bankCategory",
                        onDismissRequest = { menu = null },
                    ) {
                        bankCategories.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { bankCategory = id; menu = null },
                            )
                        }
                    }
                } else {
                    TextButton(
                        onClick = { menu = "cashType" }, enabled = !mutationBlocked,
                        modifier = Modifier.fillMaxWidth().testTag("s9_account_cash_type"),
                    ) { Text("Χρήση: " +
                        (cashCategories.firstOrNull { it.first == cashType }?.second ?: "Επιλογή")) }
                    DropdownMenu(
                        expanded = menu == "cashType",
                        onDismissRequest = { menu = null },
                    ) {
                        cashCategories.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { cashType = id; menu = null },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !mutationBlocked && valid,
                onClick = {
                    onSave(ManagedAccountEditRequest(
                        id = initial?.id, name = name, mode = mode,
                        providerId = if (mode == "bank") providerId else null,
                        providerName = if (mode == "bank") {
                            allProviders.firstOrNull { it.first == providerId }?.second
                        } else null,
                        bankCategory = if (mode == "bank") bankCategory else null,
                        cashType = if (mode == "cash") cashType else null,
                    ))
                },
                modifier = Modifier.testTag("s9_account_save"),
            ) { Text("Αποθήκευση") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !mutationBlocked) { Text("Ακύρωση") }
        },
    )
}
