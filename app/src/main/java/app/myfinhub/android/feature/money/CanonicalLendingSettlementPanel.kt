package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.core.data.canonicalLendingSettlementProposal
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.amountVisibilityText
import java.text.NumberFormat
import java.util.Locale

private fun lendingProposalEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

/**
 * Advisory only. A proposed repayment is not a booked receivable reduction,
 * confirmed schedule, notification or incoming transfer.
 * UI drafts are ephemeral and never become server-authoritative FinanceData.
 */
@Composable
internal fun CanonicalLendingSettlementPanel(
    person: String,
    outstanding: Double,
    asOf: String,
    amountsVisible: Boolean,
) {
    var amount by rememberSaveable(person) { mutableStateOf("") }
    var firstDate by rememberSaveable(person) { mutableStateOf(asOf) }
    var count by rememberSaveable(person) { mutableStateOf("3") }
    var cadence by rememberSaveable(person) { mutableStateOf("1") }
    Column(modifier = Modifier.fillMaxWidth().testTag("lending_settlement_panel"),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
        Text("Εκτίμηση προγράμματος επιστροφής", style = MaterialTheme.typography.titleSmall)
        Text("Μόνο υπόθεση · καμία είσπραξη, αυτόματη αποταμίευση ή υπενθύμιση δεν καταχωρίζεται.",
            style = MaterialTheme.typography.bodySmall)
        Text("Καταγεγραμμένη απαίτηση: " +
            amountVisibilityText(lendingProposalEuro(outstanding), amountsVisible),
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = amount, onValueChange = { amount = it },
            label = { Text("Προτεινόμενο συνολικό ποσό (€)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("lending_settlement_amount"),
        )
        OutlinedTextField(
            value = firstDate, onValueChange = { firstDate = it },
            label = { Text("Πρώτη επιστροφή YYYY-MM-DD") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("lending_settlement_due"),
        )
        OutlinedTextField(
            value = count, onValueChange = { count = it },
            label = { Text("Αριθμός δόσεων (1–60)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("lending_settlement_installments"),
        )
        OutlinedTextField(
            value = cadence, onValueChange = { cadence = it },
            label = { Text("Κάθε πόσους μήνες (1–24)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("lending_settlement_every_months"),
        )
        if (amount.isNotBlank()) {
            val result = runCatching {
                canonicalLendingSettlementProposal(
                    person = person, outstanding = outstanding, asOf = asOf,
                    firstDueDate = firstDate,
                    amount = amount.replace(',', '.').toDoubleOrNull() ?: Double.NaN,
                    installments = count.toIntOrNull() ?: 0,
                    everyMonths = cadence.toIntOrNull() ?: 0,
                )
            }
            result.getOrNull()?.let { proposal ->
                Column(
                    modifier = Modifier.fillMaxWidth().testTag("lending_settlement_schedule"),
                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                ) {
                    Text("Υποθετικό πρόγραμμα · ${proposal.schedule.size} δόσεις",
                        style = MaterialTheme.typography.labelLarge)
                    // Mirror Web's bounded six-row preview: do not build an
                    // inaccessible 60-row card inside the parent LazyColumn.
                    proposal.schedule.take(6).forEachIndexed { index, row ->
                        Text("${index + 1}. ${row.dueDate} · " +
                            amountVisibilityText(lendingProposalEuro(row.amount), amountsVisible),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("lending_settlement_installment_${index + 1}"))
                    }
                    if (proposal.schedule.size > 6) {
                        Text("Και ${proposal.schedule.size - 6} ακόμη προτεινόμενες δόσεις.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("lending_settlement_more_installments"))
                    }
                    Text(proposal.note, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            result.exceptionOrNull()?.let { error ->
                Text(error.message ?: "Μη έγκυρο υποθετικό πρόγραμμα.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("lending_settlement_error"))
            }
        }
    }
}
