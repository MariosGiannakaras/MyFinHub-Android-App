package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import app.myfinhub.android.core.data.canonicalCustomGoalScenario
import app.myfinhub.android.feature.utilities.amountVisibilityText
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.util.Locale

/**
 * Web SavingsGoalProjectionPanel: advisory-only detail embedded in an existing
 * real savings goal. All financial values come from canonical FinanceData.
 * No posting, transfer, auto-saving or local financial persistence.
 */
@Composable
internal fun CanonicalSavingsGoalAdvisoryDetails(
    advice: SavingsGoalAdvisoryUiItem,
    amountsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val projection = advice.projection
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement =
        Arrangement.spacedBy(MyFinHubSpacing.xs)) {
        Text("Εκτίμηση στόχου · μόνο για ενημέρωση",
            style = MaterialTheme.typography.titleSmall)
        Text("Η προβολή δεν δημιουργεί, δεσμεύει ή μεταφέρει χρήματα.",
            style = MaterialTheme.typography.bodySmall)
        if (projection == null) {
            Text(advice.error ?: "Δεν είναι διαθέσιμη η προβολή.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("savings_goal_projection_error"))
            return@Column
        }
        val status = when (projection.status) {
            "reached" -> "Ο στόχος έχει ήδη καλυφθεί από το κοινό υπόλοιπο."
            "undated" -> "Χωρίς προθεσμία δεν υπολογίζεται μηνιαία πρόταση."
            "overdue" -> "Η προθεσμία έχει παρέλθει."
            "due_today" -> "Ο στόχος λήγει σήμερα."
            else -> "Ενεργή πρόταση έως τη δηλωμένη προθεσμία."
        }
        Text(status, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("savings_goal_advice_status"))
        @Composable
        fun metric(label: String, amount: Double) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f))
                Text(amountVisibilityText(formatCanonicalEuro(amount), amountsVisible),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        metric("Κοινό πραγματικό υπόλοιπο", projection.sharedSavingsBalance)
        metric("Υπόλοιπο προς στόχο", projection.remaining)
        projection.suggestedMonthly?.let { metric("Προτεινόμενο ανά μήνα", it) }
        projection.contributionMonths?.let {
            Text("Συνεισφορές έως την προθεσμία: $it",
                style = MaterialTheme.typography.bodySmall)
        }
        metric("Ελάχιστο λειτουργικών λογαριασμών · 90 ημέρες",
            projection.operatingLiquidityMinimum90d)
        Text("Γνωστές υποχρεώσεις 90 ημερών: ${projection.knownObligationCount90d}",
            style = MaterialTheme.typography.bodySmall)
        if (projection.suggestedContributionRisk == "liquidity_warning") {
            Text("Προσοχή: η προτεινόμενη αποταμίευση υπερβαίνει " +
                "το ελάχιστο προβλεπόμενο λειτουργικό υπόλοιπο.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("savings_goal_liquidity_warning"))
        }
        if (projection.forecastCompleteness == "limited") {
            Text("Η πρόβλεψη είναι ελλιπής ή δεν υπάρχουν αρκετά δεδομένα.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("savings_goal_forecast_limited"))
        }

        var customText by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = customText,
            onValueChange = { customText = it },
            label = { Text("Υποθετική μηνιαία αποταμίευση (€)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("savings_goal_custom_monthly"),
        )
        val candidate = customText.trim()
        val validInput = candidate.isEmpty() ||
            Regex("^\\d+(?:[.,]\\d{1,2})?$").matches(candidate)
        val custom = if (candidate.isNotEmpty() && validInput) runCatching {
            projection.canonicalCustomGoalScenario(candidate.replace(',', '.').toDouble())
        }.getOrNull() else null
        if (!validInput) {
            Text("Χρησιμοποίησε μη αρνητικό ποσό με έως δύο δεκαδικά.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("savings_goal_custom_error"))
        }
        val scenarios = projection.scenarios + listOfNotNull(custom)
        if (scenarios.isNotEmpty()) {
            Text("Υποθετικά σενάρια · όχι πραγματικές κινήσεις",
                style = MaterialTheme.typography.titleSmall)
            scenarios.forEach { scenario ->
                val title = when (scenario.label) {
                    "none" -> "Χωρίς επιπλέον αποταμίευση"
                    "suggested" -> "Προτεινόμενη συνεισφορά"
                    else -> "Δικό σου σενάριο"
                }
                Column(modifier = Modifier.fillMaxWidth().testTag(
                    "savings_goal_scenario_" + scenario.label)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    Text("Ανά μήνα: " +
                        amountVisibilityText(formatCanonicalEuro(scenario.monthly), amountsVisible))
                    Text("Εκτίμηση στόχου: " +
                        amountVisibilityText(formatCanonicalEuro(scenario.totalAtDeadline), amountsVisible))
                    Text("Εκτιμώμενο κενό: " +
                        amountVisibilityText(formatCanonicalEuro(scenario.gapAtDeadline), amountsVisible))
                }
            }
        }
        Text("Όλοι οι στόχοι μοιράζονται το ίδιο αποταμιευτικό υπόλοιπο. " +
            "Οι άγνωστες μελλοντικές δαπάνες δεν περιλαμβάνονται.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
