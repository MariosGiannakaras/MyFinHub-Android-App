package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.time.LocalDate

data class CreditBillingSettingsRequest(
    val cardId: String,
    val closingDay: Int,
    val dueDay: Int,
    val boundaryRule: String,
)

data class CreditRateVersionRequest(
    val cardId: String,
    val annualPercent: Double,
    val effectiveFrom: String,
)

/**
 * Only changes billing configuration for FUTURE purchases. Historical statements and
 * source-linked card purchase/payment events are never reallocated.
 */
@Composable
fun CanonicalCreditBillingSettingsEditor(
    card: MoneyCard,
    mutationBlocked: Boolean,
    onDismiss: () -> Unit,
    onSave: (CreditBillingSettingsRequest) -> Unit,
) {
    var closing by rememberSaveable(card.id) {
        mutableStateOf(card.statementClosingDay?.toString().orEmpty())
    }
    var due by rememberSaveable(card.id) {
        mutableStateOf(card.statementDueDay?.toString().orEmpty())
    }
    var boundary by rememberSaveable(card.id) {
        mutableStateOf(card.statementBoundaryRule ?: "next-cycle")
    }
    val closeDay = closing.trim().toIntOrNull()
    val dueDay = due.trim().toIntOrNull()
    val valid = !mutationBlocked && card.canonicalKind == "credit" &&
        closeDay != null && closeDay in 1..31 &&
        dueDay != null && dueDay in 1..31 &&
        boundary in setOf("include-closing-day", "next-cycle")
    AlertDialog(
        onDismissRequest = { if (!mutationBlocked) onDismiss() },
        title = { Text("Ρυθμίσεις κύκλου πιστωτικής") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                Text(
                    "Οι αλλαγές αφορούν την αντιστοίχιση νέων αγορών. " +
                        "Τα ήδη αποθηκευμένα statements και οι πληρωμές τους παραμένουν ανέπαφα.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = closing, onValueChange = { closing = it },
                    label = { Text("Ημέρα κλεισίματος (1–31)") }, singleLine = true,
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("card_billing_closing"),
                )
                OutlinedTextField(
                    value = due, onValueChange = { due = it },
                    label = { Text("Ημέρα πληρωμής (1–31)") }, singleLine = true,
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("card_billing_due"),
                )
                TextButton(
                    onClick = {
                        boundary = if (boundary == "next-cycle")
                            "include-closing-day" else "next-cycle"
                    },
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("card_billing_boundary"),
                ) {
                    Text(if (boundary == "next-cycle")
                        "Η ημέρα κλεισίματος ανήκει στον επόμενο κύκλο"
                    else "Η ημέρα κλεισίματος ανήκει στον τρέχοντα κύκλο")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                modifier = Modifier.testTag("card_billing_save"),
                onClick = {
                    if (valid) onSave(CreditBillingSettingsRequest(
                        cardId = card.id,
                        closingDay = requireNotNull(closeDay),
                        dueDay = requireNotNull(dueDay),
                        boundaryRule = boundary,
                    ))
                },
            ) { Text("Αποθήκευση ρυθμίσεων") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !mutationBlocked) { Text("Ακύρωση") }
        },
    )
}

/**
 * Web creditRates.addCreditRate. Every bank-disclosed entry has an immutable start date;
 * no historical effective rate can be replaced and no interest charge is auto-generated.
 */
@Composable
fun CanonicalCreditRateVersionEditor(
    card: MoneyCard,
    mutationBlocked: Boolean,
    todayIso: String,
    onDismiss: () -> Unit,
    onSave: (CreditRateVersionRequest) -> Unit,
) {
    var annualRate by rememberSaveable(card.id) { mutableStateOf("") }
    var effectiveDate by rememberSaveable(card.id) { mutableStateOf(todayIso) }
    val numeric = annualRate.trim().replace(',', '.').toDoubleOrNull()
    val decimalPrecisionValid = numeric != null && numeric.isFinite() &&
        kotlin.math.abs(kotlin.math.round(numeric * 10_000.0) -
            numeric * 10_000.0) < 0.00001
    val earliest = runCatching { LocalDate.parse(todayIso) }.getOrNull()
    val effective = runCatching { LocalDate.parse(effectiveDate.trim()) }.getOrNull()
    val valid = !mutationBlocked && card.canonicalKind == "credit" &&
        numeric != null && numeric.isFinite() && numeric in 0.0..100.0 &&
        decimalPrecisionValid && effective != null && earliest != null &&
        !effective.isBefore(earliest) &&
        card.annualRates.none { it.effectiveFrom == effectiveDate }
    AlertDialog(
        onDismissRequest = { if (!mutationBlocked) onDismiss() },
        title = { Text("Νέο γνωστοποιημένο επιτόκιο") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                Text(
                    "Ονομαστικό ετήσιο επιτόκιο της τράπεζας, όχι ΣΕΠΕ/APR. " +
                        "Ημερομηνία ισχύος σήμερα ή μεταγενέστερη. " +
                        "Δεν γίνεται αυτόματη καταχώριση τόκων.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = annualRate, onValueChange = { annualRate = it },
                    label = { Text("Ετήσιο επιτόκιο (%)") }, singleLine = true,
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("card_rate_percent"),
                )
                OutlinedTextField(
                    value = effectiveDate, onValueChange = { effectiveDate = it },
                    label = { Text("Ισχύει από (YYYY-MM-DD)") }, singleLine = true,
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("card_rate_effective_from"),
                )
                if (card.annualRates.any { it.effectiveFrom == effectiveDate }) {
                    Text("Υπάρχει ήδη επιτόκιο για την ίδια ημέρα.",
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                modifier = Modifier.testTag("card_rate_save"),
                onClick = {
                    if (valid) onSave(CreditRateVersionRequest(
                        cardId = card.id,
                        annualPercent = requireNotNull(numeric),
                        effectiveFrom = effectiveDate.trim(),
                    ))
                },
            ) { Text("Καταχώριση επιτοκίου") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !mutationBlocked) { Text("Ακύρωση") }
        },
    )
}
