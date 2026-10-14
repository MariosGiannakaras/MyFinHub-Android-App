package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.core.data.CanonicalCreditStatementView
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.text.NumberFormat
import java.util.Locale

internal fun creditUtilizationRatio(card: MoneyCard): Float =
    if (card.limit == null || card.limit <= 0.0) 0f
    else (card.currentBalance.coerceAtLeast(0.0) / card.limit).toFloat().coerceIn(0f, 1f)

private fun creditWorkspaceMoney(amount: Double, visible: Boolean): String =
    if (visible) NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(amount) else "•••• €"

private fun creditStatementStatusLabel(status: String): String = when (status) {
    "due" -> "Προς εξόφληση"
    "closed" -> "Κλειστή"
    "open" -> "Ανοιχτή"
    "paid" -> "Εξοφλημένη"
    else -> "Κατάσταση"
}

/** Card and all immediate credit context coexist in one Wallet screen; details are optional. */
@Composable
fun InlineCreditCardWorkspace(
    card: MoneyCard,
    statements: List<CanonicalCreditStatementView>,
    amountsVisible: Boolean,
    onPayCard: () -> Unit,
    onAddPurchase: () -> Unit,
    onOpenActivity: (String) -> Unit,
    onOpenDetails: () -> Unit,
) {
    val credit = card.canonicalKind == "credit" || card.kind.contains("Πιστωτ", ignoreCase = true)
    Column(
        modifier = Modifier.fillMaxWidth().testTag("inline_credit_card_workspace"),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
    ) {
        MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                Text(card.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(if (card.last4.isBlank()) card.kind else "${card.kind} · •••• ${card.last4}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (credit) {
                    val outstanding = card.currentBalance.coerceAtLeast(0.0)
                    Text("Τρέχουσα οφειλή", style = MaterialTheme.typography.titleMedium)
                    Text(creditWorkspaceMoney(outstanding, amountsVisible),
                        style = MaterialTheme.typography.headlineMedium)
                    card.limit?.takeIf { it > 0.0 }?.let { limit ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Όριο: " + creditWorkspaceMoney(limit, amountsVisible))
                            Text("Διαθέσιμα: " + creditWorkspaceMoney((limit - outstanding).coerceAtLeast(0.0), amountsVisible))
                        }
                        // Privacy mode also conceals utilization, not just textual figures.
                        if (amountsVisible) {
                            LinearProgressIndicator(
                                progress = { creditUtilizationRatio(card) },
                                modifier = Modifier.fillMaxWidth().testTag("credit_utilization_indicator"),
                            )
                            Text(
                                "Χρήση ορίου: ${(creditUtilizationRatio(card) * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Button(
                        onClick = onPayCard,
                        enabled = outstanding > 0.005,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Πληρωμή κάρτας") }
                    OutlinedButton(onClick = onAddPurchase, modifier = Modifier.fillMaxWidth()) {
                        Text("Καταχώριση αγοράς")
                    }
                }
                TextButton(onClick = onOpenDetails) { Text("Στοιχεία / διαχείριση κάρτας") }
            }
        }
        if (credit) {
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Text("Καταστάσεις πιστωτικής", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (statements.isEmpty()) {
                        Text("Δεν υπάρχουν συνδεδεμένες καταστάσεις. Η οφειλή και οι κινήσεις εμφανίζονται ξεχωριστά.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        statements.forEach { statement ->
                            Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                                Text("${statement.openDate} – ${statement.closeDate} · ${creditStatementStatusLabel(statement.status)}",
                                    style = MaterialTheme.typography.titleSmall)
                                Text("Πληρωτέο: " + creditWorkspaceMoney(statement.remaining, amountsVisible)
                                    + " · Λήξη ${statement.dueDate}", style = MaterialTheme.typography.bodyMedium)
                                Text("Αγορές: " + creditWorkspaceMoney(statement.purchaseTotal, amountsVisible)
                                    + " · Πληρωμές: " + creditWorkspaceMoney(statement.paymentTotal, amountsVisible),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Text("Αγορές και πληρωμές", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (card.activity.isEmpty()) {
                        Text("Δεν υπάρχουν καταγεγραμμένες κινήσεις για την κάρτα.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        card.activity.forEach { entry ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    TextButton(onClick = { onOpenActivity(entry.id) }) {
                                        Text(entry.title.ifBlank {
                                            if (entry.kind == MoneyCardActivityKind.PAYMENT) "Πληρωμή" else "Αγορά"
                                        })
                                    }
                                    Text(entry.dateLabel, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(creditWorkspaceMoney(entry.amount, amountsVisible),
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}
