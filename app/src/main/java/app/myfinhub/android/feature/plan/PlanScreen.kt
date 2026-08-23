package app.myfinhub.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    state: PlanUiState,
    onAction: (PlanAction) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Πλάνο", modifier = Modifier.semantics { heading() }) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle("Επόμενες υποχρεώσεις") }
            items(state.items, key = PlannedItem::id) { item ->
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(item.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${if (item.kind == PlannedKind.RECURRING) "Επαναλαμβανόμενο" else "Προγραμματισμένο"} · ${item.dueLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(formatEuro(item.amount), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                SectionTitle("Μηνιαίο budget")
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.budget.monthlyLimitText,
                        onValueChange = { onAction(PlanAction.MonthlyLimitChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Συνολικό όριο") },
                        suffix = { Text("€") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.budget.alertThresholdText,
                        onValueChange = { onAction(PlanAction.AlertThresholdChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ειδοποίηση στο") },
                        suffix = { Text("%") },
                        singleLine = true,
                    )
                    Button(onClick = { onAction(PlanAction.SaveBudget) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Έλεγχος budget")
                    }
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                SectionTitle("Πρόβλεψη ταμειακής ροής")
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Εκτιμώμενο διαθέσιμο στο τέλος του ορίζοντα: ${formatEuro(state.forecastEndBalance)}")
                    Text(
                        "Η πρόβλεψη θα υπολογίζεται από recurring/scheduled canonical data· δεν δημιουργείται δεύτερη mobile βάση δεδομένων.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).semantics { heading() },
    )
}

private fun formatEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
