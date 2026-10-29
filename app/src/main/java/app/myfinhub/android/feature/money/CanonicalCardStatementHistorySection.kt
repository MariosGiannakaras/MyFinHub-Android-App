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

private fun statementHistoryEuro(value: Double, visible: Boolean): String {
    if (!value.isFinite()) return if (visible) "Μη έγκυρο ποσό" else "•••• €"
    return amountVisibilityText(
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value),
        visible,
    )
}

private fun statementHistoryStatus(status: String) = when (status) {
    "open" -> "Ανοιχτή"
    "closed" -> "Κλειστή"
    "due" -> "Προς εξόφληση"
    "paid" -> "Εξοφλημένη"
    else -> "Άγνωστη κατάσταση"
}

/** Do not show another card's statement even if an upstream caller passes mixed imported data. */
internal fun canonicalStatementsForCard(
    cardId: String,
    statements: List<CanonicalCreditStatementView>,
): List<CanonicalCreditStatementView> =
    if (cardId.isBlank()) emptyList() else statements.filter { it.cardId == cardId }

/**
 * Even a locally unique statement ID is unsafe when the same canonical ID
 * belongs to another card in the full statement ledger.
 */
internal fun canonicalStatementIdentityUnique(
    id: String,
    scopedStatements: List<CanonicalCreditStatementView>,
    fullStatementIdCounts: Map<String, Int>? = null,
): Boolean = id.isNotBlank() &&
    scopedStatements.count { it.id == id } == 1 &&
    (fullStatementIdCounts == null || fullStatementIdCounts[id] == 1)

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
    onPayStatement: (String, Double) -> Unit,
    fullActivityIdCounts: Map<String, Int>? = null,
    fullStatementIdCounts: Map<String, Int>? = null,
) {
    val scopedStatements = canonicalStatementsForCard(card.id, statements)
    if (scopedStatements.isEmpty()) return
    var visibleLimit by rememberSaveable(card.id) { mutableIntStateOf(8) }
    var expandedId by remember(card.id) { mutableStateOf<String?>(null) }
    // Never eagerly render a potentially unbounded historical card statement.
    var linkedLimit by remember(card.id, expandedId) { mutableIntStateOf(15) }
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
            scopedStatements.take(visibleLimit).forEachIndexed { index, statement ->
                val identityUnique = canonicalStatementIdentityUnique(
                    statement.id, scopedStatements, fullStatementIdCounts,
                )
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
                    if (identityUnique && statement.remaining.isFinite() && statement.remaining > 0.005) {
                        TextButton(
                            onClick = { onPayStatement(statement.id, statement.remaining) },
                            modifier = Modifier.fillMaxWidth()
                                .testTag("card_statement_pay_$index"),
                        ) { Text("Πληρωμή αυτής της κατάστασης") }
                    }
                    if (!identityUnique) {
                        Text(
                            "Μη έγκυρο ή μη μοναδικό ID κατάστασης · οι συνδεδεμένες κινήσεις είναι μόνο για προβολή.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        TextButton(
                            onClick = {
                                expandedId = if (expandedId == statement.id) null else statement.id
                                linkedLimit = 15
                            },
                            modifier = Modifier.testTag("card_statement_expand_$index"),
                        ) {
                            Text(
                                if (expandedId == statement.id) "Απόκρυψη κινήσεων"
                                else "Συνδεδεμένες κινήσεις (${statement.purchaseIds.size + statement.paymentIds.size})"
                            )
                        }
                        if (expandedId == statement.id) {
                            val linked = canonicalStatementLinkedRows(
                                statement, card.activity, fullActivityIdCounts,
                            )
                            val shown = linked.take(linkedLimit)
                            if (linked.isEmpty()) {
                                Text(
                                    "Δεν υπάρχουν συνδεδεμένες κινήσεις.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            shown.forEachIndexed { rowIndex, row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("card_statement_linked_row_${index}_$rowIndex"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    if (row.openActivityId != null) {
                                        TextButton(
                                            onClick = { onOpenActivity(row.openActivityId) },
                                            modifier = Modifier.weight(1f)
                                                .testTag("card_statement_event_${index}_$rowIndex"),
                                        ) {
                                            Text("${row.kindLabel} · ${row.displayTitle}")
                                        }
                                    } else {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${row.kindLabel} · ${row.displayTitle}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(
                                                row.warning ?: "Ιστορική σύνδεση προς έλεγχο",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.testTag(
                                                    "card_statement_link_warning_${index}_$rowIndex"),
                                            )
                                        }
                                    }
                                    row.amount?.let { amount ->
                                        Text(
                                            statementHistoryEuro(amount, amountsVisible),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                            if (linked.size > shown.size) {
                                TextButton(
                                    onClick = {
                                        linkedLimit = (linkedLimit + 15).coerceAtMost(linked.size)
                                    },
                                    modifier = Modifier.testTag("card_statement_more_links_$index"),
                                ) {
                                    Text("Περισσότερες κινήσεις (${linked.size - shown.size})")
                                }
                            }
                        }
                    }
                }
                if (index < scopedStatements.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (scopedStatements.size > visibleLimit) {
                TextButton(
                    onClick = { visibleLimit = (visibleLimit + 8).coerceAtMost(scopedStatements.size) },
                    modifier = Modifier.fillMaxWidth().testTag("card_statements_more"),
                ) {
                    Text("Περισσότερες καταστάσεις (${scopedStatements.size - visibleLimit})")
                }
            }
        }
    }
}
