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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.time.LocalDate
import kotlin.math.abs

/** Native real-state management for Web recurringOverrides/custom lifecycle. */
@Composable
fun CanonicalRecurringManagementScreen(
    state: PlanUiState,
    onBack: () -> Unit,
    onStatusChanged: (String, String) -> Unit,
    onCreate: (RecurringCreateRequest) -> Unit = {},
    onEdit: (String, RecurringCreateRequest) -> Unit = { _, _ -> },
    onRecordPayment: (RecurringManageItem) -> Unit = {},
    mutationBlocked: Boolean = false,
    paymentAccountAvailable: Boolean = true,
) {
    val showAmounts = rememberAmountVisibilityPreference()
    val serviceAssetCatalog = LocalRecurringServiceAssets.current
    val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR"))
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringManageItem?>(null) }
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
            item("new-recurring") {
                TextButton(
                    enabled = !mutationBlocked && state.scheduledAccounts.isNotEmpty(),
                    onClick = { creating = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("recurring_create_open"),
                ) { Text("Νέο πάγιο / συνδρομή") }
            }

            // Duplicate historical IDs remain independent display rows. Index
            // contributes to stable Compose identity; finance actions are gated.
            itemsIndexed(state.recurringManageItems, key = { index, item ->
                item.id + "#row#" + index
            }) { index, item ->
                val uniqueId = state.recurringManageItems.count { it.id == item.id } == 1
                val visibleActualRows = rememberSaveable { mutableIntStateOf(3) }
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
                        // Canonical recorded expenses are never projected estimates.
                        // Privacy masking applies per row, including Compose semantics.
                        if (uniqueId && item.actualPaymentHistory.isNotEmpty()) {
                            Text(
                                "Πραγματικές πληρωμές (" + item.actualPaymentHistory.size + ")",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            item.actualPaymentHistory.take(visibleActualRows.intValue)
                                .forEachIndexed { paymentIndex, payment ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            payment.dateIso,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            amountVisibilityText(currency.format(payment.amount), showAmounts),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.testTag(
                                                "recurring_actual_history_" + item.id + "_" + paymentIndex,
                                            ),
                                        )
                                    }
                                }
                            if (item.actualPaymentHistory.size > visibleActualRows.intValue) {
                                val remaining = item.actualPaymentHistory.size - visibleActualRows.intValue
                                TextButton(
                                    onClick = { visibleActualRows.intValue += 10 },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .testTag("recurring_more_actual_" + item.id),
                                ) { Text("Περισσότερες πληρωμές ($remaining)") }
                            }
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
                            TextButton(
                                enabled = !mutationBlocked && uniqueId,
                                onClick = { editing = item },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("recurring_edit_" + item.id),
                            ) { Text("Επεξεργασία παγίου") }
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
    if (creating || editing != null) {
        val activeEdit = editing
        CanonicalRecurringCreateDialog(
            accounts = state.scheduledAccounts,
            initial = activeEdit,
            mutationBlocked = mutationBlocked,
            onDismiss = { creating = false; editing = null },
            onSave = { request ->
                if (activeEdit != null) onEdit(activeEdit.id, request) else onCreate(request)
                creating = false; editing = null
            },
        )
    }
}


/** Web recurring editor subset: canonical amount type, cadence, account and dates.
 * The optional Web logo upload intentionally remains a separate service workflow.
 */
@Composable
private fun CanonicalRecurringCreateDialog(
    accounts: List<ScheduledAccountOption>,
    initial: RecurringManageItem? = null,
    mutationBlocked: Boolean,
    onDismiss: () -> Unit,
    onSave: (RecurringCreateRequest) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var amountText by remember(initial?.id) {
        mutableStateOf(initial?.amount?.toString().orEmpty())
    }
    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: "Πάγια") }
    var accountId by remember(initial?.id) {
        // Existing orphaned payer stays unresolved until explicitly replaced.
        mutableStateOf(initial?.accountId ?: accounts.firstOrNull()?.id.orEmpty())
    }
    var accountMenu by remember { mutableStateOf(false) }
    var mode by remember(initial?.id) { mutableStateOf(initial?.amountMode ?: "fixed") }
    var estimateMin by remember(initial?.id) { mutableStateOf(initial?.estimateMin?.toString().orEmpty()) }
    var estimateMax by remember(initial?.id) { mutableStateOf(initial?.estimateMax?.toString().orEmpty()) }
    var unit by remember(initial?.id) { mutableStateOf(initial?.recurrenceUnit ?: "month") }
    var intervalText by remember(initial?.id) {
        mutableStateOf(initial?.recurrenceInterval?.toString() ?: "1")
    }
    var dayText by remember(initial?.id) { mutableStateOf(initial?.day?.toString().orEmpty()) }
    var firstDate by remember(initial?.id) {
        // Editing a legacy monthly bill must not invent a missing cadence anchor.
        mutableStateOf(initial?.firstExpectedDateIso
            ?: if (initial == null) LocalDate.now().toString() else "")
    }
    var endDate by remember(initial?.id) { mutableStateOf(initial?.endDateIso.orEmpty()) }
    fun parseMoney(raw: String) = raw.replace(',', '.').toDoubleOrNull()
    fun validMoney(value: Double?) = value != null && value.isFinite() && value > 0.0 &&
        abs(value * 100.0 - kotlin.math.round(value * 100.0)) < 0.000001
    fun validDate(raw: String) = raw.isBlank() || runCatching {
        LocalDate.parse(raw).toString() == raw
    }.getOrDefault(false)
    val amount = parseMoney(amountText)
    val lower = parseMoney(estimateMin)
    val upper = parseMoney(estimateMax)
    val interval = intervalText.toIntOrNull()
    val day = dayText.toIntOrNull()
    val variable = mode == "variable"
    val boundsValid = if (!variable) true
        else if (estimateMin.isBlank() && estimateMax.isBlank()) true
        else validMoney(lower) && validMoney(upper) && lower!! <= amount.orZero() &&
            amount.orZero() <= upper!!
    val valid = name.trim().isNotEmpty() && name.trim().length <= 500 &&
        validMoney(amount) && boundsValid && accountId in accounts.map { it.id } &&
        interval != null && interval in 1..120 &&
        (dayText.isBlank() || day in 1..31) && validDate(firstDate) &&
        validDate(endDate) && (firstDate.isNotBlank() || (unit == "month" && interval == 1))
    AlertDialog(
        onDismissRequest = { if (!mutationBlocked) onDismiss() },
        title = { Text(if (initial == null) "Νέο πάγιο / συνδρομή" else "Επεξεργασία παγίου") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            ) {
                Text("Η εκτίμηση δεν καταχωρίζει πραγματική πληρωμή.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Όνομα") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("recurring_create_name"))
                OutlinedTextField(value = amountText, onValueChange = { amountText = it },
                    label = { Text(if (variable) "Εκτιμώμενο ποσό" else "Σταθερό ποσό") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("recurring_create_amount"))
                TextButton(onClick = {
                    mode = if (variable) "fixed" else "variable"
                    estimateMin = ""; estimateMax = ""
                }, modifier = Modifier.fillMaxWidth().testTag("recurring_create_mode")) {
                    Text(if (variable) "Μεταβλητό · εκτίμηση" else "Σταθερό ποσό · αλλαγή τύπου")
                }
                if (variable) {
                    OutlinedTextField(value = estimateMin, onValueChange = { estimateMin = it },
                        label = { Text("Κατώτερη εκτίμηση (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                            .testTag("recurring_create_min"))
                    OutlinedTextField(value = estimateMax, onValueChange = { estimateMax = it },
                        label = { Text("Ανώτερη εκτίμηση (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                            .testTag("recurring_create_max"))
                }
                OutlinedTextField(value = category, onValueChange = { category = it },
                    label = { Text("Κατηγορία") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("recurring_create_category"))
                Box {
                    TextButton(onClick = { accountMenu = true },
                        modifier = Modifier.fillMaxWidth().testTag("recurring_create_account")) {
                        Text("Λογαριασμός: " +
                            (accounts.firstOrNull { it.id == accountId }?.label
                                ?: if (initial == null) "Επιλογή"
                                else "Μη διαθέσιμος — επίλεξε λογαριασμό"))
                    }
                    DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(text = { Text(account.label) }, onClick = {
                                accountId = account.id; accountMenu = false
                            })
                        }
                    }
                }
                TextButton(onClick = { unit = if (unit == "month") "year" else "month" },
                    modifier = Modifier.fillMaxWidth().testTag("recurring_create_unit")) {
                    Text(if (unit == "month") "Επανάληψη: μήνες" else "Επανάληψη: χρόνια")
                }
                OutlinedTextField(value = intervalText, onValueChange = { intervalText = it },
                    label = { Text("Κάθε (1–120)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("recurring_create_interval"))
                OutlinedTextField(value = dayText, onValueChange = { dayText = it },
                    label = { Text("Ημέρα μήνα (προαιρετικά)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("recurring_create_day"))
                OutlinedTextField(value = firstDate, onValueChange = { firstDate = it },
                    label = { Text("Πρώτη ημερομηνία (ΕΕΕΕ-ΜΜ-ΗΗ)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("recurring_create_first_date"))
                OutlinedTextField(value = endDate, onValueChange = { endDate = it },
                    label = { Text("Λήξη / ανανέωση (προαιρετικά)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("recurring_create_end_date"))
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !mutationBlocked,
                modifier = Modifier.testTag("recurring_create_save"),
                onClick = {
                    if (valid && amount != null && interval != null) onSave(
                        RecurringCreateRequest(
                            name = name.trim(), amount = amount, accountId = accountId,
                            category = category.trim(), amountMode = mode,
                            estimateMin = if (variable) lower else null,
                            estimateMax = if (variable) upper else null,
                            recurrenceUnit = unit, recurrenceInterval = interval,
                            day = day, firstExpectedDate = firstDate.ifBlank { null },
                            endDate = endDate.ifBlank { null },
                        ),
                    )
                },
            ) { Text(if (initial == null) "Αποθήκευση παγίου" else "Αποθήκευση αλλαγών") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !mutationBlocked) { Text("Ακύρωση") }
        },
    )
}

private fun Double?.orZero(): Double = this ?: 0.0
