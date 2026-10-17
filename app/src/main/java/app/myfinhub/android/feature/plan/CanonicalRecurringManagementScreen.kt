package app.myfinhub.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.designsystem.MyFinHubBackButton
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
) {
    val showAmounts = rememberAmountVisibilityPreference()
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
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
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
                                enabled = !mutationBlocked && uniqueId,
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
