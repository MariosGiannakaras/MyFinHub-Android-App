package app.myfinhub.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import app.myfinhub.android.core.data.CanonicalCashFlowForecast
import app.myfinhub.android.core.data.CanonicalForecastAssumption
import app.myfinhub.android.core.data.webForecastScenario
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.amountVisibilityText
import java.text.NumberFormat
import java.util.Locale

/** Ephemeral draft: never enters the guarded mutation pipeline or saved state. */
private data class WhatIfDraft(
    val id: Int,
    val accountId: String,
    val date: String,
    val kind: String = "extra_expense",
    val amountText: String = "",
)

private fun forecastScenarioEuro(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(amount)

/** Web ForecastScenarioPanel 30/60/90-day hypotheses; read-only and account-accurate. */
@Composable
internal fun CanonicalForecastScenarioPanel(
    baselines: Map<Int, CanonicalCashFlowForecast>,
    accounts: List<ScheduledAccountOption>,
    amountsVisible: Boolean,
) {
    val baselineAccounts = baselines[30]?.accounts?.map { it.accountId }?.toSet().orEmpty()
    val available = accounts.filter { it.id in baselineAccounts }
    var open by remember(baselines) { mutableStateOf(false) }
    var horizon by remember(baselines) { mutableStateOf(30) }
    var drafts by remember(baselines) { mutableStateOf(emptyList<WhatIfDraft>()) }
    var nextId by remember(baselines) { mutableStateOf(0) }
    val baseline = baselines[horizon]

    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth().testTag("s7_forecast_scenario_panel")) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text("Τι θα γινόταν αν…", style = MaterialTheme.typography.titleMedium)
            Text("Υποθετικές εισπράξεις ή δαπάνες, χωρίς πραγματική καταχώριση ή αποθήκευση.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = { open = !open },
                modifier = Modifier.testTag("s7_forecast_scenario_toggle"),
            ) { Text(if (open) "Κλείσιμο σεναρίου" else "Δοκιμή σεναρίου") }
            if (open && baseline != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    listOf(30, 60, 90).forEach { days ->
                        TextButton(
                            enabled = days in baselines,
                            onClick = { horizon = days },
                            modifier = Modifier.testTag("s7_forecast_scenario_horizon_$days"),
                        ) { Text("$days ημέρες" + if (horizon == days) " ✓" else "") }
                    }
                }
                Text("Οι υποθέσεις μένουν μόνο σε αυτή την οθόνη. " +
                    "Καμία οικονομική συναλλαγή δεν καταχωρίζεται.",
                    style = MaterialTheme.typography.bodySmall)
                drafts.forEach { draft ->
                    Column(modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text("Υπόθεση ${draft.id}", style = MaterialTheme.typography.labelLarge)
                        var kindExpanded by remember(draft.id) { mutableStateOf(false) }
                        TextButton(onClick = { kindExpanded = true },
                            modifier = Modifier.testTag("s7_forecast_scenario_kind_${draft.id}")) {
                            Text(if (draft.kind == "extra_income") "Πρόσθετη είσπραξη" else "Πρόσθετη δαπάνη")
                        }
                        DropdownMenu(expanded = kindExpanded, onDismissRequest = { kindExpanded = false }) {
                            listOf("extra_expense" to "Πρόσθετη δαπάνη",
                                "extra_income" to "Πρόσθετη είσπραξη").forEach { (key, title) ->
                                DropdownMenuItem(
                                    text = { Text(title) },
                                    onClick = {
                                        drafts = drafts.map { if (it.id == draft.id) it.copy(kind = key) else it }
                                        kindExpanded = false
                                    },
                                )
                            }
                        }
                        var accountExpanded by remember(draft.id) { mutableStateOf(false) }
                        TextButton(onClick = { accountExpanded = true },
                            modifier = Modifier.testTag("s7_forecast_scenario_account_${draft.id}")) {
                            Text(available.firstOrNull { it.id == draft.accountId }?.label ?: draft.accountId)
                        }
                        DropdownMenu(expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false }) {
                            available.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.label) },
                                    onClick = {
                                        drafts = drafts.map {
                                            if (it.id == draft.id) it.copy(accountId = account.id) else it
                                        }
                                        accountExpanded = false
                                    },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = draft.date,
                            onValueChange = { value ->
                                drafts = drafts.map { if (it.id == draft.id) it.copy(date = value) else it }
                            },
                            label = { Text("Ημερομηνία YYYY-MM-DD") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag(
                                "s7_forecast_scenario_date_${draft.id}"),
                        )
                        OutlinedTextField(
                            value = draft.amountText,
                            onValueChange = { value ->
                                drafts = drafts.map {
                                    if (it.id == draft.id) it.copy(amountText = value) else it
                                }
                            },
                            label = { Text("Υποθετικό ποσό (€)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag(
                                "s7_forecast_scenario_amount_${draft.id}"),
                        )
                        TextButton(
                            onClick = { drafts = drafts.filterNot { it.id == draft.id } },
                            modifier = Modifier.testTag("s7_forecast_scenario_remove_${draft.id}"),
                        ) { Text("Αφαίρεση υπόθεσης") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    TextButton(
                        enabled = drafts.size < 16 && available.isNotEmpty(),
                        onClick = {
                            nextId += 1
                            drafts = drafts + WhatIfDraft(
                                nextId, available.first().id, baseline.asOf.plusDays(1).toString())
                        },
                        modifier = Modifier.testTag("s7_forecast_scenario_add"),
                    ) { Text("Προσθήκη υπόθεσης") }
                    if (drafts.isNotEmpty()) TextButton(
                        onClick = { drafts = emptyList() },
                        modifier = Modifier.testTag("s7_forecast_scenario_clear"),
                    ) { Text("Καθαρισμός") }
                }
                if (drafts.isEmpty()) {
                    Text("Πρόσθεσε μία υπόθεση για να συγκρίνεις προβλέψεις.",
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    val evaluated = runCatching {
                        baseline.webForecastScenario(drafts.map { draft ->
                            CanonicalForecastAssumption(
                                id = "scenario-${draft.id}",
                                accountId = draft.accountId,
                                date = draft.date,
                                kind = draft.kind,
                                amount = draft.amountText.replace(',', '.').toDoubleOrNull() ?: Double.NaN,
                            )
                        })
                    }
                    evaluated.onFailure { error ->
                        Text(error.message ?: "Μη έγκυρες υποθέσεις σεναρίου.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("s7_forecast_scenario_error"))
                    }
                    evaluated.getOrNull()?.let { result ->
                        @Composable
                        fun totalRow(label: String, amount: Double) {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(amountVisibilityText(forecastScenarioEuro(amount), amountsVisible),
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Column(modifier = Modifier.testTag("s7_forecast_scenario_results"),
                            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            totalRow("Χωρίς υποθέσεις", result.baseline.projectedPortfolio)
                            totalRow("Με τις υποθέσεις", result.projectedPortfolio)
                            totalRow("Χαμηλότερη υποθετική ρευστότητα", result.minimumPortfolio)
                            Text("Ημέρα χαμηλότερου υπολοίπου: ${result.minimumPortfolioDate}",
                                style = MaterialTheme.typography.bodySmall)
                            result.firstPortfolioShortfallDate?.let { date ->
                                Text("Υποθετικό αρνητικό συνολικό υπόλοιπο από $date.",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.testTag("s7_forecast_scenario_shortfall"))
                            }
                            CanonicalForecastScenarioAccountStress(
                                scenario = result,
                                accounts = available,
                                amountsVisible = amountsVisible,
                            )
                            Text("Δηλωμένες κινήσεις: ${result.evidence.declared} · " +
                                "Εκτιμώμενα πάγια/δάνεια: ${result.evidence.estimated} · " +
                                "Υποθέσεις: ${result.evidence.hypothetical} · " +
                                "Παραλείψεις: ${result.evidence.omitted}.",
                                style = MaterialTheme.typography.bodySmall)
                            Text("Οι προβλέψεις δεν αποτελούν επιβεβαιωμένες εισπράξεις.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
