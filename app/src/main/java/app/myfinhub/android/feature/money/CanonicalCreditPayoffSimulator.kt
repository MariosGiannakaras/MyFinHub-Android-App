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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.core.data.estimateCanonicalCreditPayoff
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** Read-only, privacy-aware Web payoff forecast. Does not mutate a card or create payments. */
@Composable
internal fun CanonicalCreditPayoffSimulator(
    card: MoneyCard,
    amountsVisible: Boolean,
) {
    val rate = selectedCreditRate(card.annualRates, LocalDate.now())
    val debt = creditWorkspaceFinancialSnapshot(card).outstanding
    var hypotheticalPayment by remember(card.id) { mutableStateOf("") }
    val payment = hypotheticalPayment.trim().replace(',', '.').toDoubleOrNull()
    val estimate = if (amountsVisible && rate is CreditRateSelection.Selected &&
        debt != null && payment != null
    ) estimateCanonicalCreditPayoff(
        balance = debt,
        annualPercent = rate.record.annualPercent,
        monthlyPayment = payment,
    ) else null
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")) }
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()
        .testTag("card_credit_payoff_simulator")) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Ενδεικτική αποπληρωμή", style = MaterialTheme.typography.titleLarge)
            Text(
                "Υποθετική σταθερή μηνιαία πληρωμή, χωρίς νέες αγορές, χρεώσεις " +
                    "ή μεταβολή του ονομαστικού επιτοκίου. Δεν είναι τραπεζική πρόβλεψη " +
                    "και δεν δημιουργεί κινήσεις.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!amountsVisible) {
                Text("Η πρόβλεψη αποκρύπτεται όσο είναι κρυφά τα ποσά.",
                    style = MaterialTheme.typography.bodySmall)
            } else if (debt == null) {
                Text(
                    "Η οφειλή είναι μη έγκυρη. Δεν μπορεί να υπολογιστεί πρόβλεψη.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("card_payoff_invalid_debt"),
                )
            } else if (rate is CreditRateSelection.Invalid) {
                Text(
                    "Το επιτόκιο είναι μη έγκυρο ή έχει διπλή ημερομηνία. Η πρόβλεψη αποκλείστηκε.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("card_payoff_invalid_rate"),
                )
            } else if (rate is CreditRateSelection.Missing) {
                Text("Δεν υπάρχει επιτόκιο σε ισχύ. Καταχώρισε τη γνωστοποιημένη τιμή.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                OutlinedTextField(
                    value = hypotheticalPayment,
                    onValueChange = { hypotheticalPayment = it },
                    label = { Text("Υποθετική μηνιαία πληρωμή (€)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("card_payoff_monthly_amount"),
                )
                if (hypotheticalPayment.isNotBlank()) {
                    when {
                        estimate == null -> Text(
                            "Δώσε θετικό ποσό με έως δύο δεκαδικά.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        estimate.feasible -> Text(
                            "Ενδεικτική εξόφληση σε ${estimate.months} μήνες · " +
                                "τόκοι ${currency.format(estimate.totalInterest)} · " +
                                "συνολικές πληρωμές ${currency.format(estimate.totalPaid)}.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("card_payoff_estimate"),
                        )
                        else -> Text(
                            "Δεν προκύπτει εξόφληση με αυτά τα δεδομένα εντός 360 μηνών. " +
                                "Υπόλοιπο ${currency.format(estimate.remaining)}.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("card_payoff_infeasible"),
                        )
                    }
                }
            }
        }
    }
}
