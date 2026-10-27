package app.myfinhub.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.core.data.CanonicalForecastScenario
import app.myfinhub.android.core.data.canonicalScenarioAccountRiskRows
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.amountVisibilityText
import java.text.NumberFormat
import java.util.Locale

private fun accountScenarioEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

/**
 * Drill-down into the already-computed Web scenario account rows.
 * Never treat hypothetical receipts as available balance.
 */
@Composable
internal fun CanonicalForecastScenarioAccountStress(
    scenario: CanonicalForecastScenario,
    accounts: List<ScheduledAccountOption>,
    amountsVisible: Boolean,
) {
    val rows = scenario.canonicalScenarioAccountRiskRows()
    var limit by remember(
        scenario.baseline.asOf,
        scenario.baseline.horizonDays,
        scenario.assumptions,
    ) { mutableIntStateOf(5) }

    Column(
        modifier = Modifier.fillMaxWidth()
            .testTag("s7_forecast_scenario_account_stress"),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
    ) {
        Text(
            "Επίδραση και κίνδυνοι ανά λογαριασμό",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Οι μεταβολές είναι μόνο υποθετικές. " +
                "Δεν έχουν δημιουργηθεί πραγματικές εισπράξεις ή πληρωμές.",
            style = MaterialTheme.typography.bodySmall,
        )
        rows.take(limit).forEachIndexed { index, row ->
            val label = accounts.firstOrNull { it.id == row.accountId }
                ?.label?.takeIf { it.isNotBlank() }
                ?: "Λογαριασμός ${index + 1}"
            Column(
                modifier = Modifier.fillMaxWidth()
                    .testTag("s7_forecast_scenario_account_row_${index + 1}"),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Χωρίς υποθέσεις", style = MaterialTheme.typography.bodySmall)
                    Text(
                        amountVisibilityText(
                            accountScenarioEuro(row.baselineProjected), amountsVisible),
                        modifier = Modifier.testTag(
                            "s7_forecast_scenario_account_baseline_${index + 1}"),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Με τις υποθέσεις", style = MaterialTheme.typography.bodySmall)
                    Text(
                        amountVisibilityText(
                            accountScenarioEuro(row.scenarioProjected), amountsVisible),
                        modifier = Modifier.testTag(
                            "s7_forecast_scenario_account_projected_${index + 1}"),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Υποθετική διαφορά", style = MaterialTheme.typography.bodySmall)
                    Text(
                        amountVisibilityText(
                            accountScenarioEuro(row.scenarioDifference), amountsVisible),
                        modifier = Modifier.testTag(
                            "s7_forecast_scenario_account_delta_${index + 1}"),
                    )
                }
                Text(
                    "Χαμηλότερο υποθετικό υπόλοιπο: " +
                        amountVisibilityText(
                            accountScenarioEuro(row.minimum), amountsVisible),
                    style = MaterialTheme.typography.bodySmall,
                )
                row.firstNegativeDate?.let { date ->
                    Text(
                        "Πιθανό αρνητικό υπόλοιπο από $date.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(
                            "s7_forecast_scenario_account_negative_${index + 1}"),
                    )
                }
                if (row.firstNegativeDate == null) {
                    row.firstLowDate?.let { date ->
                        Text(
                            "Πιθανό χαμηλό υπόλοιπο από $date.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag(
                                "s7_forecast_scenario_account_low_${index + 1}"),
                        )
                    }
                }
            }
        }
        if (rows.size > limit) {
            TextButton(
                onClick = { limit = (limit + 5).coerceAtMost(rows.size) },
                modifier = Modifier.fillMaxWidth()
                    .testTag("s7_forecast_scenario_account_more"),
            ) {
                Text("Περισσότεροι λογαριασμοί (${rows.size - limit})")
            }
        }
        if (scenario.evidence.omitted > 0) {
            Text(
                "${scenario.evidence.omitted} κινήσεις παραλείφθηκαν λόγω ανεπαρκών " +
                    "ή μη έγκυρων στοιχείων. Η πρόβλεψη μπορεί να είναι ελλιπής.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("s7_forecast_scenario_omitted_warning"),
            )
        }
    }
}
