package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.min

data class CanonicalLoanPaymentPreview(
    val date: String,
    val sourceAccountId: String,
    val count: Int,
    val amount: Double?,
    val savingsAccountId: String?,
    val shownAmount: Double,
)

/** UI review only; strict loan outstanding/ledger validation stays in the canonical entry boundary. */
fun canonicalLoanPaymentPreview(
    loan: LoanItem,
    accounts: List<MoneyAccount>,
    date: String,
    source: String,
    countText: String,
    amountText: String,
    savings: String,
): CanonicalLoanPaymentPreview? {
    if (runCatching { LocalDate.parse(date) }.isFailure || loan.remaining <= 0.005) return null
    val usable = accounts.filter { it.canonicalKind in setOf("cash","bank","savings") }
    if (usable.none { it.id == source }) return null
    if (loan.isSelfLoan) {
        val target = usable.firstOrNull { it.id == savings &&
            (it.canonicalKind == "savings" ||
                (it.canonicalKind == "bank" && it.bankAccountCategory == "savings")) }
            ?: return null
        if (target.id == source) return null
        val amount = amountText.replace(',', '.').toDoubleOrNull() ?: return null
        if (!amount.isFinite() || amount <= 0.0 || amount > loan.remaining + 0.005) return null
        return CanonicalLoanPaymentPreview(date, source, 1, amount, target.id, amount)
    }
    val count = countText.toIntOrNull() ?: return null
    if (count !in 1..loan.remainingInstallments) return null
    val shown = min(loan.remaining, loan.monthlyPayment * count)
    if (!shown.isFinite() || shown <= 0.0) return null
    return CanonicalLoanPaymentPreview(date, source, count, null, null, shown)
}

/** Explicit review/confirm before any loan-payment mutation is created. */
@Composable
fun CanonicalLoanPaymentDialog(
    loan: LoanItem,
    accounts: List<MoneyAccount>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Double?, String?) -> Unit,
) {
    val usable = remember(accounts) { accounts.filter { it.canonicalKind in setOf("cash","bank","savings") } }
    val savings = usable.filter { it.canonicalKind == "savings" ||
        (it.canonicalKind == "bank" && it.bankAccountCategory == "savings") }
    var date by remember(loan.id) { mutableStateOf(LocalDate.now().toString()) }
    var count by remember(loan.id) { mutableStateOf("1") }
    var amount by remember(loan.id) {
        mutableStateOf(min(loan.remaining, loan.monthlyPayment).toString())
    }
    var source by remember(loan.id) {
        mutableStateOf(
            loan.defaultAccountId.takeIf { id -> usable.any { it.id == id } }
                ?: usable.firstOrNull()?.id.orEmpty(),
        )
    }
    var saving by remember(loan.id) { mutableStateOf(savings.firstOrNull { it.id != source }?.id.orEmpty()) }
    val review = canonicalLoanPaymentPreview(loan,accounts,date,source,count,amount,saving)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (loan.isSelfLoan) "Επιστροφή ΒΟΗΘΕΙΑΣ" else "Πληρωμή δόσεων") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(loan.name, style = MaterialTheme.typography.titleMedium)
                Text("Υπόλοιπο: " + formatCanonicalEuro(loan.remaining))
                OutlinedTextField(
                    value = date, onValueChange = { date = it },
                    label = { Text("Ημερομηνία YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth().testTag("loan_payment_date"),
                    singleLine = true,
                )
                if (loan.isSelfLoan) {
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it },
                        label = { Text("Ποσό επιστροφής") },
                        modifier = Modifier.fillMaxWidth().testTag("loan_payment_amount"),
                        singleLine = true,
                    )
                } else {
                    OutlinedTextField(
                        value = count, onValueChange = { count = it },
                        label = { Text("Πλήθος δόσεων (έως ${loan.remainingInstallments})") },
                        modifier = Modifier.fillMaxWidth().testTag("loan_payment_count"),
                        singleLine = true,
                    )
                }
                Text("Λογαριασμός πληρωμής", style = MaterialTheme.typography.labelLarge)
                usable.forEach { acc ->
                    TextButton(
                        onClick = { source = acc.id },
                        modifier = Modifier.fillMaxWidth().testTag("loan_payment_source_" + acc.id),
                    ) { Text((if (source == acc.id) "✓ " else "") + acc.name) }
                }
                if (loan.isSelfLoan) {
                    Text("Λογαριασμός αποταμίευσης",style=MaterialTheme.typography.labelLarge)
                    savings.forEach { acc ->
                        TextButton(
                            onClick = { saving = acc.id },
                            modifier = Modifier.fillMaxWidth().testTag("loan_payment_savings_" + acc.id),
                        ) { Text((if (saving == acc.id) "✓ " else "") + acc.name) }
                    }
                }
                Text(
                    if (review == null) "Έλεγξε τα στοιχεία πριν από την καταχώριση."
                    else "Προς καταχώριση: " + formatCanonicalEuro(review.shownAmount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Η κίνηση δημιουργείται μόνο μετά την επιβεβαίωση.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = review != null,
                modifier = Modifier.testTag("loan_payment_confirm"),
                onClick = {
                    review?.let { onConfirm(it.date,it.sourceAccountId,it.count,it.amount,it.savingsAccountId) }
                },
            ) { Text("Καταχώριση") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ακύρωση") }
        },
    )
}
