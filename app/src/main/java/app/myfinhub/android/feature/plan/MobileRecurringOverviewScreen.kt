package app.myfinhub.android.feature.plan

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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

internal enum class MobileRecurringFilter(val label: String) {
    ALL("Όλα"), ACTIVE("Ενεργά"), INACTIVE("Ανενεργά"),
}

internal fun mobileRecurringVisibleRows(
    items: List<RecurringManageItem>,
    filter: MobileRecurringFilter,
): List<IndexedValue<RecurringManageItem>> = items.withIndex().filter { indexed ->
    when (filter) {
        MobileRecurringFilter.ALL -> true
        MobileRecurringFilter.ACTIVE -> indexed.value.status == "active"
        MobileRecurringFilter.INACTIVE -> indexed.value.status != "active"
    }
}

/** Never present variable/invalid planned expenses as settled or fixed ledger amounts. */
internal fun mobileRecurringExpectedLabel(item: RecurringManageItem, amountsVisible: Boolean): String {
    if (!amountsVisible) return "•••• €"
    if (item.amountValidationError != null) return "Μη έγκυρη εκτίμηση"
    val money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR"))
    return when (item.amountMode) {
        "fixed" -> if (item.amount.isFinite() && item.amount >= 0) {
            "Προβλεπόμενο ποσό: " + money.format(item.amount)
        } else "Μη έγκυρο ποσό"
        "variable" -> {
            val min = item.estimateMin
            val max = item.estimateMax
            if (min != null && max != null && min.isFinite() && max.isFinite() &&
                min >= 0 && max >= min
            ) "Εκτιμώμενο εύρος: ${money.format(min)} – ${money.format(max)}"
            else if (item.amount.isFinite() && item.amount >= 0)
                "Εκτιμώμενο ποσό: " + money.format(item.amount)
            else "Μεταβλητό ποσό"
        }
        else -> "Μη αναγνωρίσιμος τύπος ποσού"
    }
}
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
    fun displayAmount(amount: Double) = if (!amountsVisible) "•••• €"
        else if (amount.isFinite()) money.format(amount) else "Μη έγκυρο ποσό"
    var filter by rememberSaveable { mutableStateOf(MobileRecurringFilter.ALL) }
    val visibleRows = mobileRecurringVisibleRows(items, filter)
    val activeCount = items.count { it.status == "active" }

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
            item("overview-summary") {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                    Text(
                        "${items.size} πάγια και συνδρομές · ${activeCount} ενεργά",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("mobile_recurring_summary"),
                    )
                    Text(
                        "Οι προβλεπόμενες χρεώσεις δεν αποτελούν πραγματοποιημένες πληρωμές.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (items.isNotEmpty()) {
                item("status-filters") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    ) {
                        MobileRecurringFilter.entries.forEach { choice ->
                            FilterChip(
                                selected = filter == choice,
                                onClick = { filter = choice },
                                label = { Text(choice.label) },
                                modifier = Modifier.testTag(
                                    "mobile_recurring_filter_${choice.name.lowercase()}"
                                ),
                            )
                        }
                    }
                }
            }
            if (visibleRows.isEmpty()) {
                item("empty") {
                    Text(
                        if (items.isEmpty()) "Δεν υπάρχουν καταγεγραμμένες επαναλαμβανόμενες υποχρεώσεις."
                        else "Δεν υπάρχουν πάγια σε αυτή την κατηγορία.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Preserve the canonical index even when filters change: historical
            // duplicated IDs remain independently viewable with stable row keys.
            items(visibleRows, key = { "recurring:${it.value.id}:${it.index}" }) { indexed ->
                val index = indexed.index
                val recurring = indexed.value
                var historyBatches by rememberSaveable(recurring.id, index) { mutableIntStateOf(1) }
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth().testTag("mobile_recurring_card_$index")) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text(
                            recurring.title.ifBlank { "Χωρίς τίτλο" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            mobileRecurringExpectedLabel(recurring, amountsVisible),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        recurring.amountValidationError?.let {
                            Text(
                                "Ελέγξτε την εκτίμηση στο Web.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            listOfNotNull(
                                recurring.cadenceLabel.takeIf(String::isNotBlank),
                                recurring.nextDueDateIso.takeIf(String::isNotBlank)
                                    ?.takeIf { recurring.status == "active" }
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
                                modifier = Modifier.testTag("mobile_recurring_paid_month_$index"),
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
                                    modifier = Modifier.testTag("mobile_recurring_payment_${index}_$paymentIndex"),
                                )
                            }
                            if (shown < recurring.actualPaymentHistory.size) {
                                TextButton(
                                    onClick = { historyBatches += 1 },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .testTag("mobile_recurring_more_$index"),
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
