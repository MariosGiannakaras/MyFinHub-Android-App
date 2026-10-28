package app.myfinhub.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MOBILE_SCHEDULED_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu")

internal fun mobileScheduledDateLabel(raw: String): String =
    runCatching { LocalDate.parse(raw).format(MOBILE_SCHEDULED_DATE_FORMAT) }
        .getOrElse { raw.ifBlank { "Χωρίς ημερομηνία" } }

internal fun mobileScheduledStatusLabel(status: String): String = when (status) {
    "completed" -> "Ολοκληρώθηκε"
    "skipped" -> "Παραλείφθηκε"
    "cancelled" -> "Ακυρώθηκε"
    "due" -> "Εκκρεμεί"
    "upcoming" -> "Προσεχώς"
    else -> "Άγνωστη κατάσταση"
}

/** Imported duplicate IDs, unknown money kinds or statuses stay visible but non-writable. */
internal fun mobileScheduledActionable(row: ScheduledManageItem, idCount: Int): Boolean =
    row.id.isNotBlank() && idCount == 1 &&
        row.status in setOf("due", "upcoming") &&
        row.kind in setOf("expense", "income", "transfer")

internal fun mobileScheduledAmountLabel(amount: Double, visible: Boolean): String {
    if (!visible) return "•••• €"
    if (!amount.isFinite() || amount <= 0) return "Μη έγκυρο προγραμματισμένο ποσό"
    return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(amount)
}

/**
 * No scheduled payment is posted by navigation or viewing. Confirmation is
 * mandatory and goes through the atomic canonical scheduled completion.
 */
@Composable
fun CanonicalScheduledManagementScreen(
    state: PlanUiState,
    onBack: () -> Unit,
    onCreate: (ScheduledCreateRequest) -> Unit = {},
    onComplete: (ScheduledCompletionRequest) -> Unit,
    onClose: (String, String) -> Unit,
    mutationBlocked: Boolean = false,
) {
    val showAmounts = rememberAmountVisibilityPreference()
    val idCounts = remember(state.scheduledManageItems) {
        state.scheduledManageItems.groupingBy { it.id }.eachCount()
    }
    var selected by remember { mutableStateOf<ScheduledManageItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    var createKind by remember { mutableStateOf("expense") }
    var createDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var createAmount by remember { mutableStateOf("") }
    var createNote by remember { mutableStateOf("") }
    var sourceId by remember { mutableStateOf("") }
    var destinationId by remember { mutableStateOf("") }
    var sourceMenu by remember { mutableStateOf(false) }
    var destinationMenu by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf("complete") }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var amountText by remember { mutableStateOf("") }
    var actualAccountId by remember { mutableStateOf("") }
    var actualFromAccountId by remember { mutableStateOf("") }
    var actualToAccountId by remember { mutableStateOf("") }
    var actualSourceMenu by remember { mutableStateOf(false) }
    var actualDestinationMenu by remember { mutableStateOf(false) }
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
            if(state.scheduledAccounts.isNotEmpty()) {
                item("create") {
                    TextButton(
                        enabled = !mutationBlocked,
                        modifier = Modifier.fillMaxWidth().heightIn(min=48.dp)
                            .testTag("scheduled_create_open"),
                        onClick = {
                            createKind = "expense"
                            createDate = LocalDate.now().toString()
                            createAmount = ""
                            createNote = ""
                            sourceId = state.scheduledAccounts.first().id
                            destinationId = state.scheduledAccounts.drop(1).firstOrNull()?.id
                                ?: state.scheduledAccounts.first().id
                            creating = true
                        },
                    ) { Text("Νέα προγραμματισμένη κίνηση") }
                }
            }
            itemsIndexed(state.scheduledManageItems, key = { index, item ->
                item.id + "#row#" + index
            }) { index, item ->
                // Duplicate historical IDs remain visible, never writable.
                val uniqueId = idCounts[item.id] == 1 && item.id.isNotBlank()
                val actionable = mobileScheduledActionable(item, idCounts[item.id] ?: 0)
                val rowSuffix = if (uniqueId) "" else "_"+index
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()
                    .testTag("scheduled_item_" + item.id + rowSuffix)) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            mobileScheduledDateLabel(item.dueDateIso) + " · " +
                                mobileScheduledStatusLabel(item.status),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(mobileScheduledAmountLabel(item.amount, showAmounts))
                        if (!uniqueId) {
                            Text(
                                "Ιστορικό διπλό ID · Μόνο προβολή, χωρίς πληρωμή ή αλλαγή κατάστασης",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if(item.status in setOf("due","upcoming")) {
                            if (!actionable) {
                                Text(
                                    "Ασαφής προγραμματισμένη κίνηση · Μόνο προβολή",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                                TextButton(
                                    enabled = !mutationBlocked && actionable,
                                    onClick = {
                                        selected = item
                                        action = "complete"
                                        dateText = LocalDate.now().toString()
                                        amountText = item.amount.toString()
                                        val available=state.scheduledAccounts.map { it.id }
                                        actualAccountId=item.accountId?.takeIf { it in available }
                                            ?: available.firstOrNull().orEmpty()
                                        actualFromAccountId=item.fromAccountId?.takeIf { it in available }
                                            ?: available.firstOrNull().orEmpty()
                                        actualToAccountId=item.toAccountId?.takeIf {
                                            it in available && it!=actualFromAccountId
                                        } ?: available.firstOrNull { it!=actualFromAccountId }.orEmpty()
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min=48.dp)
                                        .testTag("scheduled_complete_" + item.id + rowSuffix),
                                ) { Text("Καταχώριση") }
                                TextButton(enabled = !mutationBlocked && actionable,
                                    onClick = { selected=item;action="skipped" },
                                    modifier = Modifier.weight(1f).heightIn(min=48.dp)
                                        .testTag("scheduled_skip_" + item.id + rowSuffix),
                                ) { Text("Παράλειψη") }
                                TextButton(enabled = !mutationBlocked && actionable,
                                    onClick = { selected=item;action="cancelled" },
                                    modifier = Modifier.weight(1f).heightIn(min=48.dp)
                                        .testTag("scheduled_cancel_" + item.id + rowSuffix),
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
    if(creating) {
        val value=createAmount.replace(',', '.').toDoubleOrNull()
        val dateValid=runCatching { LocalDate.parse(createDate) }.isSuccess
        val isTransfer=createKind=="transfer"
        val availableIds = state.scheduledAccounts.mapTo(mutableSetOf()) { it.id }
        val ready=value!=null && value.isFinite() && value>0 && dateValid &&
            sourceId in availableIds &&
            (!isTransfer || (destinationId in availableIds && destinationId!=sourceId))
        AlertDialog(
            onDismissRequest={if(!mutationBlocked)creating=false},
            title={Text("Νέα προγραμματισμένη κίνηση")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        for(kind in listOf("expense","income","transfer")) {
                            TextButton(
                                onClick={createKind=kind},
                                modifier=Modifier.testTag("scheduled_new_kind_"+kind),
                            ) {Text(when(kind) {
                                "income"->"Έσοδο"
                                "transfer"->"Μεταφορά"
                                else->"Έξοδο"
                            } + if(createKind==kind)" ✓" else "")}
                        }
                    }
                    OutlinedTextField(value=createDate,onValueChange={createDate=it},
                        label={Text("Ημερομηνία (ΕΕΕΕ-ΜΜ-ΗΗ)")},
                        singleLine=true,modifier=Modifier.fillMaxWidth().testTag("scheduled_new_date"))
                    OutlinedTextField(value=createAmount,onValueChange={createAmount=it},
                        label={Text("Ποσό")},singleLine=true,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                        modifier=Modifier.fillMaxWidth().testTag("scheduled_new_amount"))
                    OutlinedTextField(value=createNote,onValueChange={createNote=it},
                        label={Text("Περιγραφή")},singleLine=true,
                        modifier=Modifier.fillMaxWidth().testTag("scheduled_new_note"))
                    Box {
                        TextButton(onClick={sourceMenu=true},
                            modifier=Modifier.fillMaxWidth().testTag("scheduled_new_source")) {
                            Text((if(isTransfer)"Από: " else "Λογαριασμός: ") +
                                (state.scheduledAccounts.firstOrNull { it.id==sourceId }?.label ?: "Επιλογή"))
                        }
                        DropdownMenu(expanded=sourceMenu,onDismissRequest={sourceMenu=false}) {
                            state.scheduledAccounts.forEach { account ->
                                DropdownMenuItem(text={Text(account.label)},onClick={
                                    sourceId=account.id; sourceMenu=false
                                })
                            }
                        }
                    }
                    if(isTransfer) {
                        Box {
                            TextButton(onClick={destinationMenu=true},
                                modifier=Modifier.fillMaxWidth().testTag("scheduled_new_destination")) {
                                Text("Προς: " + (state.scheduledAccounts.firstOrNull {
                                    it.id==destinationId }?.label ?: "Επιλογή"))
                            }
                            DropdownMenu(expanded=destinationMenu,
                                onDismissRequest={destinationMenu=false}) {
                                state.scheduledAccounts.forEach { account ->
                                    DropdownMenuItem(text={Text(account.label)},onClick={
                                        destinationId=account.id;destinationMenu=false
                                    })
                                }
                            }
                        }
                    }
                    Text("Το ποσό θα επηρεάσει το υπόλοιπο μόνο όταν καταχωρίσεις την εκτέλεση.",
                        style=MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton={
                TextButton(enabled=ready && !mutationBlocked,
                    modifier=Modifier.testTag("scheduled_new_confirm"),
                    onClick={
                        if(value!=null && ready && !mutationBlocked) onCreate(ScheduledCreateRequest(
                            kind=createKind,dueDate=createDate,amount=value,
                            note=createNote,
                            accountId=if(isTransfer)null else sourceId,
                            fromAccountId=if(isTransfer)sourceId else null,
                            toAccountId=if(isTransfer)destinationId else null,
                        ))
                        creating=false
                    }) {Text("Προγραμματισμός")}
            },
            dismissButton={TextButton(onClick={creating=false}) {Text("Πίσω")}},
        )
    }

    selected?.let { item ->
        // A changed canonical row invalidates an open confirmation dialog.
        val currentSource = state.scheduledManageItems.singleOrNull { it.id == item.id }
        val stillCurrent = currentSource == item &&
            mobileScheduledActionable(item, idCounts[item.id] ?: 0)
        val amount = amountText.replace(',', '.').toDoubleOrNull()
        val dateValid = runCatching { LocalDate.parse(dateText) }.isSuccess
        val routeValid = scheduledCompletionAccountIsValid(
            item.kind,state.scheduledAccounts.mapTo(mutableSetOf()) { it.id },
            actualAccountId,actualFromAccountId,actualToAccountId,
        )
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
                        // Web scheduledToEvent accepts real account overrides at confirmation.
                        // The scheduled source is only an initial suggestion, not a binding.
                        val transfer=item.kind=="transfer"
                        Box {
                            TextButton(onClick={actualSourceMenu=true},enabled=!mutationBlocked,
                                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                                    .testTag("scheduled_confirm_source")) {
                                val selectedId=if(transfer)actualFromAccountId else actualAccountId
                                Text((if(transfer)"Από: " else "Λογαριασμός: ") +
                                    (state.scheduledAccounts.firstOrNull { it.id==selectedId }?.label
                                        ?: "Επιλογή"))
                            }
                            DropdownMenu(expanded=actualSourceMenu,
                                onDismissRequest={actualSourceMenu=false}) {
                                state.scheduledAccounts.forEach { account ->
                                    DropdownMenuItem(text={Text(account.label)},onClick={
                                        if(transfer)actualFromAccountId=account.id
                                        else actualAccountId=account.id
                                        actualSourceMenu=false
                                    })
                                }
                            }
                        }
                        if(transfer) {
                            Box {
                                TextButton(onClick={actualDestinationMenu=true},enabled=!mutationBlocked,
                                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                                        .testTag("scheduled_confirm_destination")) {
                                    Text("Προς: " + (state.scheduledAccounts.firstOrNull {
                                        it.id==actualToAccountId }?.label ?: "Επιλογή"))
                                }
                                DropdownMenu(expanded=actualDestinationMenu,
                                    onDismissRequest={actualDestinationMenu=false}) {
                                    state.scheduledAccounts.forEach { account ->
                                        DropdownMenuItem(text={Text(account.label)},onClick={
                                            actualToAccountId=account.id
                                            actualDestinationMenu=false
                                        })
                                    }
                                }
                            }
                        }
                    } else Text("Η προγραμματισμένη κίνηση θα κλείσει χωρίς μεταβολή χρημάτων.")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !mutationBlocked && stillCurrent &&
                        (action!="complete" || (dateValid && amount!=null && amount.isFinite() && amount>0 && routeValid)),
                    onClick = {
                        if (mutationBlocked || !stillCurrent) return@TextButton
                        if(action=="complete") {
                            if(amount!=null && amount.isFinite() && amount > 0 && dateValid && routeValid)onComplete(
                                ScheduledCompletionRequest(
                                    id=item.id,date=dateText,amount=amount,
                                    accountId=if(item.kind=="transfer")null else actualAccountId,
                                    fromAccountId=if(item.kind=="transfer")actualFromAccountId else null,
                                    toAccountId=if(item.kind=="transfer")actualToAccountId else null,
                                )
                            )
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
