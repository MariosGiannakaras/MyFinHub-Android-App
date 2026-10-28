package app.myfinhub.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.rememberAmountVisibilityPreference
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val MOBILE_RECURRING_HISTORY_BATCH = 5
private val MOBILE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu")

/** Unparseable imported dates are kept intact, rather than guessed or silently dropped. */
internal fun mobileRecurringDateLabel(rawIso: String): String =
    runCatching { LocalDate.parse(rawIso).format(MOBILE_DATE_FORMAT) }.getOrDefault(rawIso)

internal fun mobileRecurringStatusLabel(status: String): String = when (status) {
    "active" -> "Ενεργή"
    "paused" -> "Σε παύση"
    "stopped" -> "Διακομμένη"
    else -> status
}

/** Bound initial history on the main Plan LazyColumn; use Long to avoid count overflow. */
internal fun mobileRecurringVisibleHistoryCount(total: Int, batches: Int): Int =
    minOf(total.coerceAtLeast(0).toLong(),
        batches.coerceAtLeast(1).toLong() * MOBILE_RECURRING_HISTORY_BATCH).toInt()

/**
 * Phone-only recurring overview: real payment events are read-only projections.
 * Authoring, recurrence editing and lifecycle mutation remain exclusively in Web.
 */
@Composable
fun MobileRecurringOverviewScreen(
    items: List<RecurringManageItem>,
    onBack: () -> Unit,
) {
    MobileRecurringOverviewContent(
        items = items,
        onBack = onBack,
        amountsVisible = rememberAmountVisibilityPreference(),
    )
}

/** Separate content permits privacy regression testing without changing real device preferences. */
@Composable
internal fun MobileRecurringOverviewContent(
    items: List<RecurringManageItem>,
    onBack: () -> Unit,
    amountsVisible: Boolean,
) {
    val money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR"))
    fun displayAmount(amount: Double) = if (amountsVisible) money.format(amount) else "•••• €"

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Πάγια και συνδρομές",
                subtitle = "Προβολή · διαχείριση από το Web",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("mobile_recurring_overview"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = padding.calculateTopPadding() + MyFinHubSpacing.sm,
                bottom = MyFinHubDesignMetrics.productSnackbarBottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            if (items.isEmpty()) {
                item("empty") {
                    Text(
                        "Δεν υπάρχουν καταγεγραμμένες επαναλαμβανόμενες υποχρεώσεις.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Imported historical data can reuse a recurring ID. A raw id key would
            // crash LazyColumn; the index makes rendering deterministic and read-only.
            itemsIndexed(items, key = { index, recurring -> "recurring:${recurring.id}:$" + index }) { index, recurring ->
                var historyBatches by rememberSaveable(recurring.id, index) { mutableIntStateOf(1) }
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth().testTag("mobile_recurring_card_$" + index)) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text(recurring.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            (if (recurring.amountMode == "variable") "Εκτιμώμενο ποσό: " else "Προβλεπόμενο ποσό: ") +
                                displayAmount(recurring.amount),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            listOfNotNull(
                                recurring.cadenceLabel.takeIf(String::isNotBlank),
                                recurring.nextDueDateIso.takeIf(String::isNotBlank)
                                    ?.let { "Επόμενη: ${mobileRecurringDateLabel(it)}" },
                                "Κατάσταση: " + mobileRecurringStatusLabel(recurring.status),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Use actual ledger-linked payment totals only; never confuse
                        // scheduled estimates with events that have been booked.
                        recurring.actualPaidThisMonth?.let { paid ->
                            Text(
                                "Πραγματικές πληρωμές μήνα (${recurring.actualPaymentsThisMonth}): " +
                                    displayAmount(paid),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("mobile_recurring_paid_month_$" + index),
                            )
                        }
                        recurring.lastActualPaymentDateIso?.takeIf(String::isNotBlank)?.let { date ->
                            val amountLabel = recurring.lastActualPaymentAmount
                                ?.let { ": " + displayAmount(it) }.orEmpty()
                            Text(
                                "Τελευταία πραγματική πληρωμή: ${mobileRecurringDateLabel(date)}" + amountLabel,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (recurring.actualPaymentHistory.isNotEmpty()) {
                            Text(
                                "Καταγεγραμμένες πληρωμές",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            val shown = mobileRecurringVisibleHistoryCount(
                                recurring.actualPaymentHistory.size, historyBatches,
                            )
                            recurring.actualPaymentHistory.take(shown).forEachIndexed { paymentIndex, payment ->
                                Text(
                                    "${mobileRecurringDateLabel(payment.dateIso)} · ${displayAmount(payment.amount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.testTag("mobile_recurring_payment_$" + index + "_$" + paymentIndex),
                                )
                            }
                            if (shown < recurring.actualPaymentHistory.size) {
                                TextButton(
                                    onClick = { historyBatches += 1 },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .testTag("mobile_recurring_more_$" + index),
                                ) {
                                    Text("Περισσότερες πληρωμές (${recurring.actualPaymentHistory.size - shown})")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
