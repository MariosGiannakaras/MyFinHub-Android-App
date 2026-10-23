package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.core.data.CanonicalCreditStatementView
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.amountVisibilityText
import java.text.NumberFormat
import java.util.Locale

private fun statementHistoryEuro(value: Double, visible: Boolean): String =
    amountVisibilityText(
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value),
        visible,
    )

private fun statementHistoryStatus(status: String) = when (status) {
    "open" -> "Ανοιχτή"
    "closed" -> "Κλειστή"
    "due" -> "Προς εξόφληση"
    "paid" -> "Εξοφλημένη"
    else -> "Άγνωστη κατάσταση"
}

/**
 * Canonical statement groups are authoritative. Never allocate historical events to
 * guessed cycles, truncate a statement's true totals, or synthesize card payments.
 */
@Composable
internal fun CanonicalCardStatementHistorySection(
    card: MoneyCard,
    statements: List<CanonicalCreditStatementView>,
    amountsVisible: Boolean,
    onOpenActivity: (String) -> Unit,
) {
    if (statements.isEmpty()) return
    var visibleLimit by rememberSaveable(card.id) { mutableIntStateOf(8) }
    var expandedId by remember(card.id) { mutableStateOf<String?>(null) }
    var showAllLinked by remember(card.id) { mutableStateOf(false) }
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()
        .testTag("card_statement_history")) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Καταστάσεις πιστωτικής", style = MaterialTheme.typography.titleLarge)
            Text(
                "Αυτοτελείς περίοδοι έκδοσης και εξόφλησης. " +
                    "Ασύνδετες ιστορικές κινήσεις δεν αποδίδονται αυθαίρετα σε κατάσταση.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            statements.take(visibleLimit).forEachIndexed { index, statement ->
                val identityUnique = statements.count { it.id == statement.id } == 1
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .testTag("card_statement_row_$index"),
                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                ) {
                    Text(
                        "${statement.openDate} – ${statement.closeDate} · " +
                            statementHistoryStatus(statement.status),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Λήξη ${statement.dueDate} · Πληρωτέο " +
                            statementHistoryEuro(statement.remaining, amountsVisible),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Αγορές " + statementHistoryEuro(statement.purchaseTotal, amountsVisible) +
                            " · Πληρωμές " + statementHistoryEuro(statement.paymentTotal, amountsVisible),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!identityUnique) {
                        Text(
                            "Μη μοναδικό ID κατάστασης · οι συνδεδεμένες κινήσεις είναι μόνο για προβολή.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        TextButton(
                            onClick = {
                                expandedId = if (expandedId == statement.id) null else statement.id
                                showAllLinked = false
                            },
                            modifier = Modifier.testTag("card_statement_expand_$index"),
                        ) {
                            Text(
                                if (expandedId == statement.id) "Απόκρυψη κινήσεων"
                                else "Συνδεδεμένες κινήσεις (${statement.purchaseIds.size + statement.paymentIds.size})"
                            )
                        }
                        if (expandedId == statement.id) {
                            val linked = listOf(
                                "Αγορά" to statement.purchaseIds,
                                "Πληρωμή" to statement.paymentIds,
                            ).flatMap { (label, ids) -> ids.map { label to it } }
                            val shown = if (showAllLinked) linked else linked.take(15)
                            if (linked.isEmpty()) {
                                Text(
                                    "Δεν υπάρχουν συνδεδεμένες κινήσεις.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            shown.forEachIndexed { rowIndex, (kind, eventId) ->
                                val matches = card.activity.filter { it.id == eventId }
                                val activity = matches.singleOrNull()
                                if (activity != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        TextButton(
                                            onClick = { onOpenActivity(activity.id) },
                                            modifier = Modifier.weight(1f)
                                                .testTag("card_statement_event_${index}_$rowIndex"),
                                        ) {
                                            Text("$kind · " + activity.title.ifBlank { eventId })
                                        }
                                        Text(
                                            statementHistoryEuro(activity.amount, amountsVisible),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                } else {
                                    Text(
                                        "$kind · συνδεδεμένη ιστορική κίνηση · ID $eventId",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            if (!showAllLinked && linked.size > shown.size) {
                                TextButton(onClick = { showAllLinked = true }) {
                                    Text("Προβολή όλων των ${linked.size} συνδεδεμένων κινήσεων")
                                }
                            }
                        }
                    }
                }
                if (index < statements.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (statements.size > visibleLimit) {
                TextButton(
                    onClick = { visibleLimit = (visibleLimit + 8).coerceAtMost(statements.size) },
                    modifier = Modifier.fillMaxWidth().testTag("card_statements_more"),
                ) {
                    Text("Περισσότερες καταστάσεις (${statements.size - visibleLimit})")
                }
            }
        }
    }
}
