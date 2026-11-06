package app.myfinhub.android.feature.money

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.myfinhub.android.core.data.moneyToCents
import app.myfinhub.android.core.data.centsToMoney
import app.myfinhub.android.feature.utilities.amountVisibilityText
import app.myfinhub.android.feature.utilities.rememberAmountVisibilityPreference
import kotlin.math.abs
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.min

private fun loanMoneyLabel(amount: Double): String = safeHistoricalMoneyLabel(amount)

/** Reject rounded-up or sub-cent input in the read-only payment preview. */
private fun exactLoanPreviewCents(amount: Double): Long? {
    if (!amount.isFinite() || amount <= 0.0) return null
    val cents = runCatching { moneyToCents(amount) }.getOrNull() ?: return null
    if (cents <= 0L || cents > 9_007_199_254_740_991L ||
        abs(amount * 100.0 - cents.toDouble()) > 1e-6) return null
    return cents
}

/**
 * Payment sources are physical canonical identities, not first-match UI rows.
 * Duplicate IDs (even when one is marked credit), blank/padded IDs and
 * credit-only accounts must never become an implicit money route.
 */
internal fun eligibleCanonicalLoanPayers(accounts: List<MoneyAccount>): List<MoneyAccount> {
    val counts = accounts.groupingBy { it.id }.eachCount()
    return accounts.filter {
        it.id.isNotBlank() && it.id == it.id.trim() &&
            counts[it.id] == 1 && it.canonicalKind in setOf("cash", "bank", "savings")
    }
}

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
    val outstandingCents = exactLoanPreviewCents(loan.remaining) ?: return null
    if (runCatching { LocalDate.parse(date) }.isFailure) return null
    val usable = eligibleCanonicalLoanPayers(accounts)
    if (usable.count { it.id == source } != 1) return null
    if (loan.isSelfLoan) {
        val target = usable.singleOrNull { it.id == savings &&
            (it.canonicalKind == "savings" ||
                (it.canonicalKind == "bank" && it.bankAccountCategory == "savings")) }
            ?: return null
        if (target.id == source) return null
        val amount = amountText.replace(',', '.').toDoubleOrNull() ?: return null
        val amountCents = exactLoanPreviewCents(amount) ?: return null
        if (amountCents > outstandingCents) return null
        val exactAmount = centsToMoney(amountCents)
        return CanonicalLoanPaymentPreview(date, source, 1, exactAmount, target.id, exactAmount)
    }
    val count = countText.toIntOrNull() ?: return null
    if (count !in 1..loan.remainingInstallments) return null
    val nominalCents = exactLoanPreviewCents(loan.monthlyPayment) ?: return null
    val sum = runCatching { Math.multiplyExact(nominalCents, count.toLong()) }.getOrNull()
        ?: return null
    val cents = min(outstandingCents, sum)
    if (cents <= 0L) return null
    return CanonicalLoanPaymentPreview(date, source, count, null, null,
        centsToMoney(cents))
}

/** Explicit review/confirm before any loan-payment mutation is created. */
@Composable
fun CanonicalLoanPaymentDialog(
    loan: LoanItem,
    accounts: List<MoneyAccount>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Double?, String?) -> Unit,
    amountsVisible: Boolean? = null,
) {
    val revealAmounts = amountsVisible ?: rememberAmountVisibilityPreference()
    val usable = remember(accounts) { eligibleCanonicalLoanPayers(accounts) }
    val savings = usable.filter { it.canonicalKind == "savings" ||
        (it.canonicalKind == "bank" && it.bankAccountCategory == "savings") }
    var date by remember(loan.id) { mutableStateOf(LocalDate.now().toString()) }
    var count by remember(loan.id) { mutableStateOf("1") }
    var amount by remember(loan.id) {
        // An invalid imported loan must not prefill a plausible repayment
        // from NaN/Infinity, even though the guarded confirm remains disabled.
        mutableStateOf(min(loan.remaining, loan.monthlyPayment).let { candidate ->
            if (exactLoanPreviewCents(candidate) == null) "" else candidate.toString()
        })
    }
    var source by remember(loan.id) {
        // Respect a valid explicitly configured payer only; never silently
        // redirect an orphaned or missing default to the first other account.
        mutableStateOf(loan.defaultAccountId.takeIf { id ->
            usable.count { it.id == id } == 1
        }.orEmpty())
    }
    // Internal transfers always need an explicit savings destination choice.
    var saving by remember(loan.id) { mutableStateOf("") }
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
                Text(
                    "Υπόλοιπο: " + amountVisibilityText(
                        loanMoneyLabel(loan.remaining), revealAmounts),
                    modifier = Modifier.testTag("loan_payment_remaining"),
                )
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
                if (usable.none { it.id == source }) {
                    Text(
                        "Επίλεξε ρητά διαθέσιμο λογαριασμό πληρωμής.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("loan_payment_payer_required"),
                    )
                }
                usable.forEach { acc ->
                    TextButton(
                        onClick = {
                            source = acc.id
                            // A previously selected destination cannot also
                            // become the payer of the same internal transfer.
                            if (loan.isSelfLoan && saving == source) saving = ""
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("loan_payment_source_" + acc.id),
                    ) { Text((if (source == acc.id) "✓ " else "") + acc.name) }
                }
                if (loan.isSelfLoan) {
                    Text("Λογαριασμός αποταμίευσης",style=MaterialTheme.typography.labelLarge)
                    if (savings.none { it.id == saving && it.id != source }) {
                        Text(
                            "Επίλεξε ρητά διαφορετικό αποταμιευτικό λογαριασμό.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("loan_payment_savings_required"),
                        )
                    }
                    savings.forEach { acc ->
                        TextButton(
                            onClick = { saving = acc.id },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .testTag("loan_payment_savings_" + acc.id),
                        ) { Text((if (saving == acc.id) "✓ " else "") + acc.name) }
                    }
                }
                Text(
                    if (review == null) "Έλεγξε τα στοιχεία πριν από την καταχώριση."
                    else "Προς καταχώριση: " + amountVisibilityText(
                        loanMoneyLabel(review.shownAmount), revealAmounts),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("loan_payment_review_amount"),
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
                modifier = Modifier.heightIn(min = 48.dp).testTag("loan_payment_confirm"),
                onClick = {
                    // Re-read the current source/target and debt on tap, not
                    // merely the previous preview captured by composition.
                    canonicalLoanPaymentPreview(
                        loan, accounts, date, source, count, amount, saving,
                    )?.let {
                        onConfirm(it.date,it.sourceAccountId,it.count,it.amount,it.savingsAccountId)
                    }
                },
            ) { Text("Καταχώριση") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ακύρωση") }
        },
    )
}
