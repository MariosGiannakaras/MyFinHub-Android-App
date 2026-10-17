package app.myfinhub.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.amountVisibilityText
import app.myfinhub.android.feature.utilities.rememberAmountVisibilityPreference
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * No scheduled payment is posted by navigation or viewing. Confirmation is
 * mandatory and goes through the atomic canonical scheduled completion.
 */
@Composable
fun CanonicalScheduledManagementScreen(
    state: PlanUiState,
    onBack: () -> Unit,
    onComplete: (String, String, Double) -> Unit,
    onClose: (String, String) -> Unit,
    mutationBlocked: Boolean = false,
) {
    val showAmounts = rememberAmountVisibilityPreference()
    val money = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")) }
    var selected by remember { mutableStateOf<ScheduledManageItem?>(null) }
    var action by remember { mutableStateOf("complete") }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var amountText by remember { mutableStateOf("") }
    Scaffold(topBar = {
        MyFinHubScreenHeader(
            title = "Προγραμματισμένες κινήσεις",
            subtitle = "Πληρωμές, παραλείψεις και ιστορικό",
            navigation = { MyFinHubBackButton(onBack) },
        )
    }) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("scheduled_management_root"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = insets.calculateTopPadding() + MyFinHubSpacing.sm,
                bottom = MyFinHubDesignMetrics.navigationContentBottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            items(state.scheduledManageItems, key = { it.id }) { item ->
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()
                    .testTag("scheduled_item_" + item.id)) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            item.dueDateIso + " · " + when(item.status) {
                                "completed" -> "Ολοκληρώθηκε"
                                "skipped" -> "Παραλείφθηκε"
                                "cancelled" -> "Ακυρώθηκε"
                                "due" -> "Εκκρεμεί"
                                else -> "Προσεχώς"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(amountVisibilityText(money.format(item.amount), showAmounts))
                        if(item.status in setOf("due","upcoming")) {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                                TextButton(
                                    enabled = !mutationBlocked,
                                    onClick = {
                                        selected = item
                                        action = "complete"
                                        dateText = LocalDate.now().toString()
                                        amountText = item.amount.toString()
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min=48.dp)
                                        .testTag("scheduled_complete_" + item.id),
                                ) { Text("Καταχώριση") }
                                TextButton(enabled = !mutationBlocked,
                                    onClick = { selected=item;action="skipped" },
                                    modifier = Modifier.weight(1f).heightIn(min=48.dp)
                                        .testTag("scheduled_skip_" + item.id),
                                ) { Text("Παράλειψη") }
                                TextButton(enabled = !mutationBlocked,
                                    onClick = { selected=item;action="cancelled" },
                                    modifier = Modifier.weight(1f).heightIn(min=48.dp)
                                        .testTag("scheduled_cancel_" + item.id),
                                ) { Text("Ακύρωση") }
                            }
                        }
                    }
                }
            }
            if(state.scheduledManageItems.isEmpty()) {
                item("empty") { Text("Δεν υπάρχουν προγραμματισμένες κινήσεις.") }
            }
        }
    }
    selected?.let { item ->
        val amount = amountText.replace(',', '.').toDoubleOrNull()
        val dateValid = runCatching { LocalDate.parse(dateText) }.isSuccess
        AlertDialog(
            onDismissRequest = { if(!mutationBlocked)selected=null },
            title = { Text(when(action) {
                "complete" -> "Επιβεβαίωση πραγματικής κίνησης"
                "skipped" -> "Παράλειψη"
                else -> "Ακύρωση"
            }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text(item.title)
                    if(action=="complete") {
                        Text("Η ενέργεια θα ενημερώσει οριστικά τα υπόλοιπα και θα κλείσει την προγραμματισμένη κίνηση.")
                        OutlinedTextField(
                            value=dateText,onValueChange={dateText=it},
                            label={Text("Ημερομηνία (ΕΕΕΕ-ΜΜ-ΗΗ)")},
                            singleLine=true,
                            modifier=Modifier.fillMaxWidth().testTag("scheduled_confirm_date"),
                        )
                        OutlinedTextField(
                            value=amountText,onValueChange={amountText=it},
                            label={Text("Πραγματικό ποσό")},singleLine=true,
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                            modifier=Modifier.fillMaxWidth().testTag("scheduled_confirm_amount"),
                        )
                    } else Text("Η προγραμματισμένη κίνηση θα κλείσει χωρίς μεταβολή χρημάτων.")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !mutationBlocked &&
                        (action!="complete" || (dateValid && amount!=null && amount.isFinite() && amount>0)),
                    onClick = {
                        if(action=="complete") {
                            if(amount!=null && dateValid)onComplete(item.id,dateText,amount)
                        } else onClose(item.id,action)
                        selected=null
                    },
                    modifier=Modifier.testTag("scheduled_confirm_action"),
                ) { Text("Επιβεβαίωση") }
            },
            dismissButton = { TextButton(onClick={selected=null}) { Text("Πίσω") } },
        )
    }
}
