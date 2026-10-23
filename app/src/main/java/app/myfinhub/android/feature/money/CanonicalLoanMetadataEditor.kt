package app.myfinhub.android.feature.money

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
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.round

/** Ordinary and installment loan definition ONLY; repayments use another flow. */
data class LoanMetadataEditRequest(
    val id: String?,
    val kind: String,
    val name: String,
    val provider: String,
    val total: Double,
    val installment: Double,
    val installments: Int,
    val firstExpectedDate: String,
    val defaultAccountId: String,
    val expenseCategory: String,
    val longTermRecurring: Boolean,
)

private fun moneyDraftValue(value: String): Double? {
    val raw = value.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (!raw.isFinite() || raw <= 0.0) return null
    val scaled = raw * 100.0
    return raw.takeIf { scaled.isFinite() && scaled > 0.0 &&
        abs(round(scaled) - scaled) < 0.00001 }
}

@Composable
fun CanonicalLoanMetadataEditor(
    initial: LoanItem?,
    accounts: List<MoneyAccount>,
    categories: List<String>,
    mutationBlocked: Boolean,
    onDismiss: () -> Unit,
    onSave: (LoanMetadataEditRequest) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var provider by rememberSaveable(initial?.id) { mutableStateOf(initial?.lender.orEmpty()) }
    var kind by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.kind?.takeIf { it in setOf("loan", "installment") }
            ?: "installment")
    }
    var totalText by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.originalAmount?.toString().orEmpty())
    }
    var installmentText by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.monthlyPayment?.toString().orEmpty())
    }
    var installmentsText by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.totalInstallments?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var date by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.firstExpectedDate.orEmpty())
    }
    var accountId by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.defaultAccountId?.takeIf { it.isNotEmpty() }
            ?: accounts.firstOrNull { it.canonicalKind != "credit" }?.id.orEmpty())
    }
    var category by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.expenseCategory?.takeIf(String::isNotBlank)
            ?: categories.firstOrNull().orEmpty())
    }
    var recurring by rememberSaveable(initial?.id) { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var kindMenu by remember { mutableStateOf(false) }

    val total = moneyDraftValue(totalText)
    val installment = moneyDraftValue(installmentText)
    val installments = installmentsText.toIntOrNull()
    val dateValid = date.isBlank() || runCatching { LocalDate.parse(date) }.isSuccess
    val accountEligible = accounts.count { it.id == accountId && it.canonicalKind != "credit" } == 1
    val categoryValid = category.isNotBlank() &&
        (categories.isEmpty() || category in categories || category == initial?.expenseCategory)
    val nameValid = name.trim().isNotBlank() && name.length <= 500 &&
        !Regex("\\bHELP\\b|ΒΟΗΘΕΙΑ", RegexOption.IGNORE_CASE)
            .containsMatchIn(name + " " + provider)
    val valid = !mutationBlocked && nameValid &&
        kind in setOf("loan", "installment") &&
        (initial == null || initial.kind == kind) &&
        total != null && installment != null &&
        installments != null && installments in 1..1200 &&
        accountEligible && dateValid && categoryValid

    AlertDialog(
        onDismissRequest = { if (!mutationBlocked) onDismiss() },
        title = { Text(if (initial == null) "Νέο δάνειο" else "Επεξεργασία δανείου") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            ) {
                Text(
                    "Τα στοιχεία δανείου δεν αποτελούν πληρωμή. " +
                        "Το ήδη καταγεγραμμένο ιστορικό διατηρείται.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (initial == null) {
                    TextButton(
                        onClick = { kindMenu = true }, enabled = !mutationBlocked,
                        modifier = Modifier.fillMaxWidth().testTag("loan_editor_kind"),
                    ) { Text(if (kind == "loan") "Μακροχρόνιο δάνειο" else "Αγορά σε δόσεις") }
                    DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                        listOf("installment" to "Αγορά σε δόσεις", "loan" to "Δάνειο")
                            .forEach { (value, label) ->
                                DropdownMenuItem(text = { Text(label) },
                                    onClick = { kind = value; kindMenu = false })
                            }
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Όνομα δανείου") }, singleLine = true,
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("loan_editor_name"),
                )
                OutlinedTextField(
                    value = provider, onValueChange = { provider = it },
                    label = { Text("Πάροχος/δανειστής") }, singleLine = true,
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("loan_editor_provider"),
                )
                OutlinedTextField(
                    value = totalText, onValueChange = { totalText = it },
                    label = { Text("Συνολικό ποσό (€)") }, singleLine = true,
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("loan_editor_total"),
                )
                OutlinedTextField(
                    value = installmentsText, onValueChange = { installmentsText = it },
                    label = { Text("Αριθμός δόσεων") }, singleLine = true,
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("loan_editor_count"),
                )
                OutlinedTextField(
                    value = installmentText, onValueChange = { installmentText = it },
                    label = { Text("Ποσό ανά δόση (€)") }, singleLine = true,
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("loan_editor_installment"),
                )
                OutlinedTextField(
                    value = date, onValueChange = { date = it },
                    label = { Text("Πρώτη προβλεπόμενη δόση (YYYY-MM-DD)") },
                    singleLine = true, enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("loan_editor_first_date"),
                )
                TextButton(
                    onClick = { accountMenu = true }, enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("loan_editor_account"),
                ) { Text("Λογαριασμός: " +
                    (accounts.firstOrNull { it.id == accountId }?.name ?: "Επίλεξε")) }
                DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                    accounts.filter { it.canonicalKind != "credit" }.forEach { account ->
                        DropdownMenuItem(text = { Text(account.name) },
                            onClick = { accountId = account.id; accountMenu = false })
                    }
                }
                TextButton(
                    onClick = { categoryMenu = true }, enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("loan_editor_expense_category"),
                ) { Text("Κατηγορία δόσης: " + category.ifBlank { "Επίλεξε" }) }
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    (categories + listOfNotNull(initial?.expenseCategory)
                        .filter(String::isNotBlank)).distinct().forEach { option ->
                        DropdownMenuItem(text = { Text(option) },
                            onClick = { category = option; categoryMenu = false })
                    }
                }
                if (kind == "loan" && initial == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = recurring, enabled = !mutationBlocked,
                            onCheckedChange = { recurring = it })
                        Text("Μακροχρόνια περιοδική υποχρέωση")
                    }
                }
                if (!valid) {
                    Text("Συμπλήρωσε έγκυρο ποσό, δόσεις, λογαριασμό και κατηγορία.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    if (valid) onSave(LoanMetadataEditRequest(
                        id = initial?.id,
                        kind = kind,
                        name = name.trim(),
                        provider = provider.trim(),
                        total = requireNotNull(total),
                        installment = requireNotNull(installment),
                        installments = requireNotNull(installments),
                        firstExpectedDate = date.trim(),
                        defaultAccountId = accountId,
                        expenseCategory = category,
                        longTermRecurring = kind == "loan" && recurring,
                    ))
                },
                modifier = Modifier.testTag("loan_editor_save"),
            ) { Text("Αποθήκευση") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !mutationBlocked) { Text("Ακύρωση") }
        },
    )
}
