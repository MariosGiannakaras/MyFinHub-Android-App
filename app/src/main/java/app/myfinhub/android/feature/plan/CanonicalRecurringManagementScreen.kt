package app.myfinhub.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.LocalRecurringServiceAssets
import app.myfinhub.android.designsystem.MyFinHubCertifiedRecurringServiceMark
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.amountVisibilityText
import app.myfinhub.android.feature.utilities.rememberAmountVisibilityPreference
import java.text.NumberFormat
import java.util.Locale

/** Native real-state management for Web recurringOverrides/custom lifecycle. */
@Composable
fun CanonicalRecurringManagementScreen(
    state: PlanUiState,
    onBack: () -> Unit,
    onStatusChanged: (String, String) -> Unit,
    onRecordPayment: (RecurringManageItem) -> Unit = {},
    mutationBlocked: Boolean = false,
    paymentAccountAvailable: Boolean = true,
) {
    val showAmounts = rememberAmountVisibilityPreference()
    val serviceAssetCatalog = LocalRecurringServiceAssets.current
    val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR"))
    Scaffold(topBar = {
        MyFinHubScreenHeader(
            title = "Πάγια & συνδρομές",
            subtitle = "Ασφαλείς αλλαγές κατάστασης με συγχρονισμό",
            navigation = { MyFinHubBackButton(onBack) },
        )
    }) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("recurring_management_root"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = insets.calculateTopPadding() + MyFinHubSpacing.sm,
                bottom = MyFinHubDesignMetrics.navigationContentBottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            // Duplicate historical IDs remain independent display rows. Index
            // contributes to stable Compose identity; finance actions are gated.
            itemsIndexed(state.recurringManageItems, key = { index, item ->
                item.id + "#row#" + index
            }) { index, item ->
                val uniqueId = state.recurringManageItems.count { it.id == item.id } == 1
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()
                    .testTag("recurring_manage_item_" + item.id +
                        (if (uniqueId) "" else "_" + index))) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The asset is a verified session-only reference to the exact
                            // recurring ID. Duplicate historical IDs remain text-only.
                            val asset = serviceAssetCatalog.firstOrNull {
                                it.recurringId == item.id &&
                                    (item.logoAssetKey.isNullOrBlank() ||
                                        it.assetKey == item.logoAssetKey)
                            }.takeIf { uniqueId }
                            if (asset != null) {
                                MyFinHubCertifiedRecurringServiceMark(
                                    asset = asset,
                                    fallbackLabel = item.title,
                                    modifier = Modifier.size(36.dp).testTag(
                                        "recurring_service_mark_" + item.id,
                                    ),
                                    contentDescription = null,
                                )
                            }
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            when (item.status) {
                                "active" -> "Ενεργό"
                                "paused" -> "Σε παύση"
                                else -> "Διακομμένο"
                            } + item.cadenceLabel.takeIf(String::isNotBlank)
                                ?.let { " · " + it }.orEmpty() +
                                (if (item.nextDueDateIso.isNotBlank())
                                    " · Επόμενο: " + item.nextDueDateIso else ""),

                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(amountVisibilityText(currency.format(item.amount), showAmounts),
                            style = MaterialTheme.typography.bodyMedium)
                        // Variable Web amountMode is an estimate, never a recorded payment.
                        Text(
                            if (item.amountMode == "variable") "Εκτίμηση · όχι πραγματική πληρωμή"
                            else "Σταθερό ποσό",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("recurring_amount_mode_" + item.id),
                        )
                        if (item.amountMode == "variable" &&
                            (item.estimateMin != null || item.estimateMax != null)) {
                            Text(
                                "Εύρος εκτίμησης: " +
                                    (item.estimateMin?.let {
                                        amountVisibilityText(currency.format(it), showAmounts)
                                    } ?: "—") +
                                    " – " +
                                    (item.estimateMax?.let {
                                        amountVisibilityText(currency.format(it), showAmounts)
                                    } ?: "—"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("recurring_estimate_range_" + item.id),
                            )
                        }
                        // Read-only Web actual linked expense amounts, never the estimate.
                        if (uniqueId && item.lastActualPaymentAmount != null) {
                            Text(
                                "Τελευταία πραγματική πληρωμή: " +
                                    amountVisibilityText(currency.format(item.lastActualPaymentAmount), showAmounts) +
                                    (item.lastActualPaymentDateIso?.let { " · $it" }.orEmpty()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("recurring_last_actual_" + item.id),
                            )
                        }
                        if (uniqueId && item.actualPaidThisMonth != null &&
                            item.actualPaymentsThisMonth > 0) {
                            Text(
                                "Καταχωρισμένο έως σήμερα στον μήνα: " +
                                    amountVisibilityText(currency.format(item.actualPaidThisMonth), showAmounts) +
                                    " · " + item.actualPaymentsThisMonth + " πληρωμές",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("recurring_actual_paid_month_" + item.id),
                            )
                        }
                        if (item.paymentCount > 0) {
                            Text(
                                "Καταγεγραμμένες πληρωμές: " + item.paymentCount +
                                    (item.lastPaymentDateIso?.let { " · Τελευταία: " + it }.orEmpty()) +
                                    (item.typicalPaymentDay?.let { " · Συνήθης ημέρα: " + it }.orEmpty()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!item.endDateIso.isNullOrBlank()) {
                            Text(
                                "Λήξη/ανανέωση: " + item.endDateIso,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!uniqueId) {
                            Text(
                                "Ιστορικό διπλό ID · Μόνο προβολή, χωρίς αλλαγές ή πληρωμή",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            // Explicitly opens the editable canonical Quick Entry form.
                            // No financial event is created until its Save action.
                            TextButton(
                                enabled = !mutationBlocked && uniqueId &&
                                    item.status == "active" && paymentAccountAvailable,
                                onClick = { onRecordPayment(item) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("recurring_record_payment_" + item.id),
                            ) { Text("Καταχώριση πληρωμής") }
                            TextButton(
                                enabled = !mutationBlocked && uniqueId,
                                onClick = {
                                    onStatusChanged(item.id,
                                        if (item.status == "active") "paused" else "active")
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("recurring_toggle_" + item.id),
                            ) {
                                Text(if (item.status == "active") "Παύση" else "Επανενεργοποίηση")
                            }
                            if (item.status != "stopped") {
                                TextButton(
                                    enabled = !mutationBlocked && uniqueId,
                                    onClick = { onStatusChanged(item.id, "stopped") },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .testTag("recurring_stop_" + item.id),
                                ) { Text("Διακοπή") }
                            }
                        }
                    }
                }
            }
            if (state.recurringManageItems.isEmpty()) {
                item("empty") { Text("Δεν υπάρχουν καταγεγραμμένα πάγια ή συνδρομές.") }
            }
        }
    }
}
