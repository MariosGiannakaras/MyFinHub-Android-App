package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.core.data.CanonicalCreditStatementView
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.text.NumberFormat
import java.util.Locale

/**
 * A missing or corrupt imported credit balance must never be converted to a
 * plausible zero. Only finite canonical source metrics can contribute to
 * the debt, available limit or utilization of the selected credit card.
 */
internal data class CreditWorkspaceFinancialSnapshot(
    val outstanding: Double?,
    val limit: Double?,
    val available: Double?,
    val utilization: Float?,
)

internal fun creditWorkspaceFinancialSnapshot(card: MoneyCard): CreditWorkspaceFinancialSnapshot {
    val outstanding = card.currentBalance.takeIf(Double::isFinite)?.coerceAtLeast(0.0)
    val limit = card.limit?.takeIf { it.isFinite() && it > 0.0 }
    val available = if (outstanding != null && limit != null) {
        (limit - outstanding).takeIf(Double::isFinite)?.coerceAtLeast(0.0)
    } else null
    val utilization = if (outstanding != null && limit != null) {
        (outstanding / limit).takeIf(Double::isFinite)
            ?.coerceIn(0.0, 1.0)?.toFloat()
    } else null
    return CreditWorkspaceFinancialSnapshot(outstanding, limit, available, utilization)
}

internal fun creditUtilizationRatio(card: MoneyCard): Float =
    creditWorkspaceFinancialSnapshot(card).utilization ?: 0f

internal fun creditWorkspaceMoney(amount: Double, visible: Boolean): String =
    when {
        !visible -> "•••• €"
        !amount.isFinite() -> "Μη έγκυρο ποσό"
        else -> NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(amount)
    }

internal fun creditWorkspaceOptionalMoney(amount: Double?, visible: Boolean): String =
    if (amount == null) {
        if (visible) "Μη έγκυρο ποσό" else "•••• €"
    } else creditWorkspaceMoney(amount, visible)

/** Preserve signed statement-history amounts without formatting invalid source values. */
internal fun creditWorkspaceSignedMoney(amount: Double, visible: Boolean): String =
    if (!visible || !amount.isFinite()) creditWorkspaceMoney(amount, visible)
    else (if (amount > 0.005) "+" else "") + creditWorkspaceMoney(amount, true)

private fun creditStatementStatusLabel(status: String): String = when (status) {
    "due" -> "Προς εξόφληση"
    "closed" -> "Κλειστή"
    "open" -> "Ανοιχτή"
    "paid" -> "Εξοφλημένη"
    else -> "Κατάσταση"
}

/** Render only bounded canonical rows at first; more remain accessible in place. */
internal const val INLINE_CREDIT_STATEMENT_BATCH = 4
internal const val INLINE_CREDIT_ACTIVITY_BATCH = 8

internal fun inlineCreditVisibleCount(total: Int, batches: Int, perBatch: Int): Int =
    minOf(
        total.coerceAtLeast(0).toLong(),
        batches.coerceAtLeast(1).toLong() * perBatch.coerceAtLeast(1).toLong(),
    ).toInt()

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
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val financial = creditWorkspaceFinancialSnapshot(card)
    val scopedStatements = canonicalStatementsForCard(card.id, statements)
    val activityIdCounts = canonicalCardActivityIdCounts(card.activity)
    var statementBatches by rememberSaveable(card.id) { mutableIntStateOf(1) }
    var activityBatches by rememberSaveable(card.id) { mutableIntStateOf(1) }
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
                    Text("Τρέχουσα οφειλή", style = MaterialTheme.typography.titleMedium)
                    Text(creditWorkspaceOptionalMoney(financial.outstanding, amountsVisible),
                        style = MaterialTheme.typography.headlineMedium)
                    if (card.limit != null && financial.limit == null && amountsVisible) {
                        Text("Μη έγκυρο πιστωτικό όριο",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("inline_credit_invalid_limit"))
                    }
                    financial.limit?.let { limit ->
                        if (largeFont) {
                            Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                                Text("Όριο: " + creditWorkspaceMoney(limit, amountsVisible))
                                Text("Διαθέσιμα: " + creditWorkspaceOptionalMoney(
                                    financial.available, amountsVisible,
                                ))
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Όριο: " + creditWorkspaceMoney(limit, amountsVisible))
                                Text("Διαθέσιμα: " + creditWorkspaceOptionalMoney(
                                    financial.available, amountsVisible,
                                ))
                            }
                        }
                        // Privacy mode also conceals utilization, not just textual figures.
                        if (amountsVisible) {
                            financial.utilization?.let { utilization ->
                                LinearProgressIndicator(
                                    progress = { utilization },
                                    modifier = Modifier.fillMaxWidth().testTag("credit_utilization_indicator"),
                                )
                                Text(
                                    "Χρήση ορίου: ${(utilization * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } ?: Text(
                                "Μη διαθέσιμη χρήση ορίου",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Button(
                        onClick = onPayCard,
                        enabled = (financial.outstanding ?: 0.0) > 0.005,
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
                    if (scopedStatements.isEmpty()) {
                        Text("Δεν υπάρχουν συνδεδεμένες καταστάσεις. Η οφειλή και οι κινήσεις εμφανίζονται ξεχωριστά.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        scopedStatements.take(inlineCreditVisibleCount(scopedStatements.size, statementBatches, INLINE_CREDIT_STATEMENT_BATCH)).forEach { statement ->
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
                        val shown = inlineCreditVisibleCount(
                            scopedStatements.size, statementBatches, INLINE_CREDIT_STATEMENT_BATCH,
                        )
                        if (shown < scopedStatements.size) {
                            TextButton(
                                onClick = { statementBatches += 1 },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("inline_credit_more_statements"),
                            ) { Text("Περισσότερες καταστάσεις (${scopedStatements.size - shown})") }
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
                        card.activity.take(
                            inlineCreditVisibleCount(card.activity.size, activityBatches, INLINE_CREDIT_ACTIVITY_BATCH),
                        ).forEachIndexed { index, entry ->
                            val openId = canonicalCardActivityOpenId(entry.id, activityIdCounts)
                            val identityWarning = canonicalCardActivityIdentityWarning(entry.id, activityIdCounts)
                            Surface(
                                onClick = { openId?.let(onOpenActivity) },
                                enabled = openId != null,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag(
                                        if (openId != null) "inline_credit_activity_${entry.id}"
                                        else "inline_credit_activity_unavailable_$index",
                                    ),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                val title = entry.title.ifBlank {
                                    if (entry.kind == MoneyCardActivityKind.PAYMENT) "Πληρωμή" else "Αγορά"
                                }
                                if (largeFont) {
                                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                                        Text(title, style = MaterialTheme.typography.titleSmall)
                                        Text(entry.dateLabel, style = MaterialTheme.typography.bodySmall)
                                        identityWarning?.let { warning ->
                                            Text(warning, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error)
                                        }
                                        Text(creditWorkspaceMoney(entry.amount, amountsVisible),
                                            style = MaterialTheme.typography.bodyMedium)
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(title, style = MaterialTheme.typography.titleSmall)
                                            Text(entry.dateLabel, style = MaterialTheme.typography.bodySmall)
                                            identityWarning?.let { warning ->
                                                Text(warning, style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                        Text(creditWorkspaceMoney(entry.amount, amountsVisible),
                                            style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        val shown = inlineCreditVisibleCount(
                            card.activity.size, activityBatches, INLINE_CREDIT_ACTIVITY_BATCH,
                        )
                        if (shown < card.activity.size) {
                            TextButton(
                                onClick = { activityBatches += 1 },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("inline_credit_load_more"),
                            ) { Text("Περισσότερες κινήσεις (${card.activity.size - shown})") }
                        }
                    }
                }
            }
        }
    }
}
