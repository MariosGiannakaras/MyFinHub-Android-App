package app.myfinhub.android.feature.plan

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

internal enum class MobileRecurringFilter(val label: String) {
    ALL("Όλα"),
    ACTIVE("Ενεργά"),
    INACTIVE("Ανενεργά"),
}

internal fun mobileRecurringIsActive(item: RecurringManageItem): Boolean =
    item.status == "active"

/**
 * Keep the Web-canonical order and index: duplicate historical IDs remain inspectable
 * without colliding as LazyColumn keys or mutating an ambiguous finance record.
 */
internal fun mobileRecurringRows(
    items: List<RecurringManageItem>, filter: MobileRecurringFilter,
): List<IndexedValue<RecurringManageItem>> = items.withIndex().filter { indexed ->
    when (filter) {
        MobileRecurringFilter.ALL -> true
        MobileRecurringFilter.ACTIVE -> mobileRecurringIsActive(indexed.value)
        MobileRecurringFilter.INACTIVE -> !mobileRecurringIsActive(indexed.value)
    }
}

internal fun mobileRecurringStatusLabel(status: String): String = when (status) {
    "active" -> "Ενεργό"
    "paused" -> "Σε παύση"
    "stopped" -> "Ολοκληρωμένο / ανενεργό"
    else -> "Άγνωστη κατάσταση"
}

internal fun mobileRecurringDateLabel(iso: String): String? {
    if (iso.isBlank()) return null
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return "Μη έγκυρη ημερομηνία"
    return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("el-GR")))
}

/**
 * Planned/estimated figures are NEVER described as settled payments. Invalid Web
 * variable ranges stay visible with a warning, never presented as trusted fixed money.
 */
internal fun mobileRecurringExpectedAmount(
    item: RecurringManageItem, visible: Boolean,
): String {
    if (!visible) return "•••• €"
    if (item.amountValidationError != null) return "Μη έγκυρη εκτίμηση"
    val euro = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR"))
    return when (item.amountMode) {
        "fixed" -> if (item.amount.isFinite() && item.amount >= 0.0) {
            euro.format(item.amount)
        } else "Μη έγκυρο ποσό"
        "variable" -> {
            val low = item.estimateMin
            val high = item.estimateMax
            if (low != null && high != null && low.isFinite() && high.isFinite() &&
                low >= 0.0 && high >= low
            ) {
                "Εκτίμηση: ${euro.format(low)} – ${euro.format(high)}"
            } else if (item.amount.isFinite() && item.amount >= 0.0) {
                "Εκτίμηση: ${euro.format(item.amount)}"
            } else {
                "Μεταβλητό ποσό"
            }
        }
        else -> "Μη αναγνωρίσιμος τύπος ποσού"
    }
}

/**
 * Android is a read-only overview of source recurring obligations. Their cadence,
 * pause/resume, templates and accounting mutations remain Web-only.
 */
@Composable
fun MobileRecurringOverviewScreen(
    items: List<RecurringManageItem>,
    onBack: () -> Unit,
) {
    val amountsVisible = rememberAmountVisibilityPreference()
    var filter by rememberSaveable { mutableStateOf(MobileRecurringFilter.ALL) }
    val visibleItems = mobileRecurringRows(items, filter)
    val activeCount = items.count(::mobileRecurringIsActive)
    val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR"))

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
            item("summary") {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                    Text(
                        "${items.size} πάγια και συνδρομές · ${activeCount} ενεργά",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("mobile_recurring_summary"),
                    )
                    Text(
                        "Οι επόμενες χρεώσεις είναι προγραμματισμένες, όχι ήδη πληρωμένες.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (items.isNotEmpty()) {
                item("filters") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                        modifier = Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        MobileRecurringFilter.entries.forEach { choice ->
                            FilterChip(
                                selected = filter == choice,
                                onClick = { filter = choice },
                                label = { Text(choice.label) },
                                modifier = Modifier.testTag("mobile_recurring_filter_${choice.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
            if (visibleItems.isEmpty()) {
                item("empty") {
                    Text(
                        if (items.isEmpty()) "Δεν υπάρχουν καταγεγραμμένα πάγια ή συνδρομές."
                        else "Δεν υπάρχουν πάγια σε αυτή την κατηγορία.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("mobile_recurring_empty"),
                    )
                }
            }
            items(
                items = visibleItems,
                key = { "${it.index}:${it.value.id}" },
            ) { indexed ->
                val recurring = indexed.value
                MyFinHubSectionCard(
                    modifier = Modifier.fillMaxWidth()
                        .testTag("mobile_recurring_row_${indexed.index}"),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                        Text(
                            recurring.title.ifBlank { "Χωρίς τίτλο" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            mobileRecurringStatusLabel(recurring.status),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            mobileRecurringExpectedAmount(recurring, amountsVisible),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (recurring.amountMode == "variable") {
                            Text(
                                "Εκτιμώμενο ποσό, όχι πραγματοποιημένη πληρωμή.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val due = mobileRecurringDateLabel(recurring.nextDueDateIso)
                        val details = listOfNotNull(
                            recurring.cadenceLabel.takeIf(String::isNotBlank),
                            if (recurring.status == "active") {
                                due?.let { "Επόμενη: $it" } ?: "Χωρίς επόμενη ημερομηνία"
                            } else null,
                        )
                        if (details.isNotEmpty()) {
                            Text(
                                details.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        recurring.amountValidationError?.let {
                            Text(
                                "Ελέγξτε το ποσό στο Web.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (recurring.actualPaymentsThisMonth > 0 &&
                            recurring.actualPaidThisMonth?.isFinite() == true
                        ) {
                            Text(
                                "Πληρώθηκαν αυτόν τον μήνα: " +
                                    (if (amountsVisible) currency.format(recurring.actualPaidThisMonth)
                                        else "•••• €"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
