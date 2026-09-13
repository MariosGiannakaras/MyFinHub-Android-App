package app.myfinhub.android.feature.activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubFinanceRow
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSearchField
import app.myfinhub.android.designsystem.MyFinHubSelectorButton
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.myFinHubCategoryIcon
import app.myfinhub.android.feature.quickentry.DateEntryField
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** S3 production Activity surface: one dense ledger, one exact filter sheet and no gesture-only actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLedgerScreen(
    state: ActivityUiState,
    onAction: (ActivityAction) -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenQuickEntry: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var filterSheetOpen by rememberSaveable { mutableStateOf(false) }
    val analyticsScope = state.isAnalyticsScope

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = state.categoryFilter ?: "Κινήσεις",
                subtitle = if (analyticsScope) {
                    "${formatActivityDate(state.dateFrom)} – ${formatActivityDate(state.dateTo)}"
                } else {
                    "Ιστορικό κινήσεων"
                },
                navigation = onBack?.let { back -> { MyFinHubBackButton(back) } },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("activity_list"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = MyFinHubSpacing.xs,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = MyFinHubDesignMetrics.productSnackbarBottomClearance,
            ),
        ) {
            if (analyticsScope) {
                item(key = "analytics-scope-copy") {
                    Text(
                        text = "Ποσά που αντιστοιχούν ακριβώς στην κατηγορία. Άνοιξε μια κίνηση για το πλήρες ποσό και τις λεπτομέρειες.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = MyFinHubSpacing.md),
                    )
                }
            } else {
                item(key = "new-transaction") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = MyFinHubSpacing.sm),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = onOpenQuickEntry,
                            modifier = Modifier.semantics {
                                contentDescription = "Δημιουργία νέας κίνησης"
                            },
                        ) {
                            Text("Νέα κίνηση")
                        }
                    }
                }
                item(key = "search") {
                    MyFinHubSearchField(
                        value = state.query,
                        onValueChange = { onAction(ActivityAction.QueryChanged(it)) },
                        placeholder = "Αναζήτηση κινήσεων",
                    )
                }
                item(key = "filter-button") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MyFinHubSpacing.xs, bottom = MyFinHubSpacing.xxs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { filterSheetOpen = true }) {
                            Text(
                                if (state.activeFilterCount == 0) "Φίλτρα"
                                else "Φίλτρα (${state.activeFilterCount})",
                            )
                        }
                        if (state.activeFilterCount > 0) {
                            TextButton(onClick = { onAction(ActivityAction.ClearFilters) }) {
                                Text("Καθαρισμός")
                            }
                        }
                    }
                }
                if (state.activeFilterCount > 0) {
                    item(key = "scope-summary") {
                        ActivityScopeSummary(state = state, onAction = onAction)
                    }
                }
            }

            when {
                state.items.isEmpty() -> {
                    item(key = "empty-ledger") {
                        ActivityEmptyState(
                            title = "Δεν υπάρχουν καταχωρισμένες κινήσεις.",
                            actionLabel = if (analyticsScope) null else "Νέα κίνηση",
                            onAction = if (analyticsScope) null else onOpenQuickEntry,
                        )
                    }
                }
                state.visibleItems.isEmpty() -> {
                    item(key = "empty-filtered") {
                        ActivityEmptyState(
                            title = "Δεν βρέθηκαν κινήσεις με αυτή την αναζήτηση ή τα φίλτρα.",
                            actionLabel = if (analyticsScope) null else "Καθαρισμός αναζήτησης και φίλτρων",
                            onAction = if (analyticsScope) null else {
                                {
                                    onAction(ActivityAction.QueryChanged(""))
                                    onAction(ActivityAction.ClearFilters)
                                }
                            },
                        )
                    }
                }
                else -> {
                    state.visibleSections.forEachIndexed { sectionIndex, section ->
                        val monthKey = section.date.take(7)
                        val previousMonth = state.visibleSections.getOrNull(sectionIndex - 1)?.date?.take(7)
                        if (sectionIndex == 0 || monthKey != previousMonth) {
                            item(key = "month-$monthKey") { S3ActivityMonthHeader(section.date) }
                        }
                        item(key = "day-${section.date}") { S3ActivityDayHeader(section.date) }
                        items(section.items, key = ActivityItem::id) { item ->
                            ActivityFlatLedgerRow(
                                item = item,
                                accountOptions = state.accountOptions,
                                onClick = { onOpenDetail(item.id) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    if (filterSheetOpen && !analyticsScope) {
        ModalBottomSheet(onDismissRequest = { filterSheetOpen = false }) {
            ActivityFilterSheetContent(
                state = state,
                onApply = { typeId, accountId, category, dateFrom, dateTo ->
                    onAction(
                        ActivityAction.ApplyFilters(
                            type = ActivityFilter.ALL,
                            accountId = accountId,
                            category = category,
                            dateFrom = dateFrom,
                            dateTo = dateTo,
                            typeId = typeId,
                        ),
                    )
                    filterSheetOpen = false
                },
                onReset = {
                    onAction(ActivityAction.ClearFilters)
                    filterSheetOpen = false
                },
            )
        }
    }
}

@Composable
private fun ActivityScopeSummary(
    state: ActivityUiState,
    onAction: (ActivityAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = MyFinHubSpacing.sm),
    ) {
        Text(
            text = "Ενεργό εύρος",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = MyFinHubSpacing.xxs),
        )
        val exactTypeId = state.typeFilterId
        if (exactTypeId != null) {
            val label = state.typeOptions.firstOrNull { it.id == exactTypeId }?.label
                ?: activityTypeLabel(exactTypeId)
            ActivityScopeLine("Τύπος: $label") {
                onAction(ActivityAction.RemoveFilter(ActivityFilterField.TYPE))
            }
        } else if (state.filter != ActivityFilter.ALL) {
            ActivityScopeLine("Τύπος: ${state.filter.label}") {
                onAction(ActivityAction.RemoveFilter(ActivityFilterField.TYPE))
            }
        }
        state.accountFilterId?.let { accountId ->
            val label = state.accountOptions.firstOrNull { it.id == accountId }?.label ?: accountId
            ActivityScopeLine("Λογαριασμός: $label") {
                onAction(ActivityAction.RemoveFilter(ActivityFilterField.ACCOUNT))
            }
        }
        state.ledgerCategoryFilter?.let { category ->
            ActivityScopeLine("Κατηγορία: $category") {
                onAction(ActivityAction.RemoveFilter(ActivityFilterField.CATEGORY))
            }
        }
        if (state.ledgerDateFrom != null || state.ledgerDateTo != null) {
            val from = state.ledgerDateFrom?.let(::formatActivityDate) ?: "αρχή"
            val to = state.ledgerDateTo?.let(::formatActivityDate) ?: "σήμερα"
            ActivityScopeLine("Περίοδος: $from – $to") {
                onAction(ActivityAction.RemoveFilter(ActivityFilterField.DATE))
            }
        }
    }
}

@Composable
private fun ActivityScopeLine(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRemove) { Text("Αφαίρεση") }
    }
}

@Composable
private fun ActivityEmptyState(
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MyFinHubSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun ActivityFlatLedgerRow(
    item: ActivityItem,
    accountOptions: List<ActivityAccountOption>,
    onClick: () -> Unit,
) {
    val tone = item.s3Tone()
    val secondary = if (item.kind == ActivityKind.TRANSFER) {
        activityTransferRouteLabel(item, accountOptions)
    } else {
        item.note
    }
    val meta = when {
        item.pendingSync -> item.pendingLabel ?: "Εκκρεμεί συγχρονισμός"
        item.kind == ActivityKind.TRANSFER -> "Εσωτερική μεταφορά"
        item.canonicalKind == "card_purchase" || item.canonicalKind == "card_payment" ->
            "${item.typeLabel} · ${item.accountLabel}"
        else -> item.accountLabel
    }

    MyFinHubFinanceRow(
        icon = myFinHubCategoryIcon(item.category, item.kind.s3Icon()),
        iconDescription = null,
        title = item.title,
        subtitle = secondary,
        meta = meta,
        amountText = formatSignedEuro(item.amount),
        tone = tone,
        onClick = onClick,
    )
}

@Composable
internal fun ActivityFilterSheetContent(
    state: ActivityUiState,
    onApply: (String?, String?, String?, String?, String?) -> Unit,
    onReset: () -> Unit,
) {
    var typeId by rememberSaveable(state.typeFilterId) { mutableStateOf(state.typeFilterId.orEmpty()) }
    var accountId by rememberSaveable(state.accountFilterId) { mutableStateOf(state.accountFilterId.orEmpty()) }
    var category by rememberSaveable(state.ledgerCategoryFilter) { mutableStateOf(state.ledgerCategoryFilter.orEmpty()) }
    var dateFrom by rememberSaveable(state.ledgerDateFrom) { mutableStateOf(state.ledgerDateFrom.orEmpty()) }
    var dateTo by rememberSaveable(state.ledgerDateTo) { mutableStateOf(state.ledgerDateTo.orEmpty()) }

    val fromParsed = dateFrom.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val toParsed = dateTo.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val invalidFrom = dateFrom.isNotBlank() && fromParsed == null
    val invalidTo = dateTo.isNotBlank() && toParsed == null
    val invertedRange = fromParsed != null && toParsed != null && fromParsed > toParsed
    val dateError = invalidFrom || invalidTo || invertedRange

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = MyFinHubDesignMetrics.screenHorizontalPadding,
                vertical = MyFinHubSpacing.sm,
            ),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
    ) {
        Text("Φίλτρα κινήσεων", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Τα φίλτρα εφαρμόζονται μαζί και διατηρούνται όταν επιστρέφεις στο Ιστορικό.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        S3ChoiceField(
            label = "Τύπος κίνησης",
            selectedId = typeId,
            choices = listOf("" to "Όλοι οι τύποι") + state.typeOptions.map { it.id to it.label },
            onSelected = { typeId = it },
        )
        S3ChoiceField(
            label = "Λογαριασμός",
            selectedId = accountId,
            choices = listOf("" to "Όλοι οι λογαριασμοί") + state.accountOptions.map { it.id to it.label },
            onSelected = { accountId = it },
        )
        S3ChoiceField(
            label = "Κατηγορία",
            selectedId = category,
            choices = listOf("" to "Όλες οι κατηγορίες") + state.availableCategories.map { it to it },
            onSelected = { category = it },
        )
        DateEntryField(
            value = dateFrom,
            onValueChange = { dateFrom = it },
            label = "Από ημερομηνία",
            errorMessage = when {
                invalidFrom -> "Η ημερομηνία δεν είναι έγκυρη."
                invertedRange -> "Η αρχή πρέπει να είναι πριν από το τέλος."
                else -> null
            },
            optional = true,
        )
        if (dateFrom.isNotBlank()) {
            TextButton(onClick = { dateFrom = "" }) { Text("Καθαρισμός αρχικής ημερομηνίας") }
        }
        DateEntryField(
            value = dateTo,
            onValueChange = { dateTo = it },
            label = "Έως ημερομηνία",
            errorMessage = when {
                invalidTo -> "Η ημερομηνία δεν είναι έγκυρη."
                invertedRange -> "Το τέλος πρέπει να είναι μετά από την αρχή."
                else -> null
            },
            optional = true,
        )
        if (dateTo.isNotBlank()) {
            TextButton(onClick = { dateTo = "" }) { Text("Καθαρισμός τελικής ημερομηνίας") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Text("Επαναφορά")
            }
            Button(
                onClick = {
                    onApply(
                        typeId.takeIf(String::isNotBlank),
                        accountId.takeIf(String::isNotBlank),
                        category.takeIf(String::isNotBlank),
                        dateFrom.takeIf(String::isNotBlank),
                        dateTo.takeIf(String::isNotBlank),
                    )
                },
                enabled = !dateError,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Text("Εφαρμογή")
            }
        }
    }
}

@Composable
fun ActivityReadDetailScreen(
    item: ActivityItem?,
    accountOptions: List<ActivityAccountOption>,
    mutationBlocked: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleted: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleteRequested by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(item, deleteRequested) {
        if (deleteRequested && (item == null || item.pendingSync)) onDeleted()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Λεπτομέρειες κίνησης",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        if (item == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(MyFinHubDesignMetrics.screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
            ) {
                Text("Η κίνηση δεν είναι πλέον διαθέσιμη.", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onBack) { Text("Επιστροφή") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    horizontal = MyFinHubDesignMetrics.screenHorizontalPadding,
                    vertical = MyFinHubSpacing.md,
                ),
            ) {
                item(key = "amount") {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                        MyFinHubAmountText(
                            text = formatSignedEuro(item.amount),
                            tone = item.s3Tone(),
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Text(
                            text = item.typeLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (item.pendingSync) item.pendingLabel ?: "Εκκρεμεί συγχρονισμός" else "Καταχωρισμένη",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = MyFinHubSpacing.md))
                }
                item { ActivityReadField("Ημερομηνία", formatActivityDate(item.rawDate.ifBlank { item.dateLabel })) }
                item {
                    ActivityReadField(
                        if (item.kind == ActivityKind.TRANSFER) "Προέλευση / προορισμός" else "Λογαριασμός",
                        if (item.kind == ActivityKind.TRANSFER) activityTransferRouteLabel(item, accountOptions) else item.accountLabel,
                    )
                }
                item { ActivityReadField("Κατηγορία", item.category ?: "Χωρίς κατηγορία") }
                item { ActivityReadField("Υποκατηγορία", item.subcategory ?: "Χωρίς υποκατηγορία") }
                item { ActivityReadField("Σημείωση", item.note.ifBlank { "Χωρίς σημείωση" }) }
                item.cardLabel?.takeIf(String::isNotBlank)?.let { cardLabel ->
                    item { ActivityReadField("Συνδεδεμένη κάρτα", cardLabel) }
                }
                item(key = "actions") {
                    Column(
                        modifier = Modifier.padding(top = MyFinHubSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    ) {
                        if (item.pendingSync) {
                            Text(
                                "Η κίνηση έχει εκκρεμή αλλαγή. Η επεξεργασία και η διαγραφή ενεργοποιούνται μετά την επιβεβαίωση ή την ασφαλή αναίρεση της τοπικής αλλαγής.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Button(
                                onClick = onEdit,
                                enabled = !mutationBlocked,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            ) {
                                Text("Επεξεργασία")
                            }
                            ActivityMoreActions(
                                enabled = !mutationBlocked,
                                onDelete = { confirmDelete = true },
                            )
                        }
                        if (item.kind == ActivityKind.TRANSFER) {
                            Text(
                                "Η εσωτερική μεταφορά δεν μετρά ως έσοδο ή έξοδο.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    val deleteItem = item
    if (confirmDelete && deleteItem != null && !deleteItem.pendingSync) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Διαγραφή κίνησης;") },
            text = {
                Text(
                    "Η «${deleteItem.title}» θα αφαιρεθεί από τα οικονομικά δεδομένα του MyFinHub και θα επανυπολογιστούν τα σχετικά υπόλοιπα. Δεν ακυρώνει συναλλαγή στην τράπεζα.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        deleteRequested = true
                        onDelete()
                    },
                ) { Text("Διαγραφή") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Ακύρωση") }
            },
        )
    }
}

@Composable
private fun ActivityMoreActions(enabled: Boolean, onDelete: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text("Περισσότερα")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Διαγραφή κίνησης") },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun ActivityReadField(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = MyFinHubSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun ActivityEditScreen(
    item: ActivityItem?,
    categoryOptions: List<ActivityCategoryOption>,
    mutationInFlight: Boolean,
    mutationBlocked: Boolean,
    onBack: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
    onSaved: () -> Unit,
) {
    if (item == null) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { MyFinHubScreenHeader(title = "Επεξεργασία κίνησης", navigation = { MyFinHubBackButton(onBack) }) },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(MyFinHubDesignMetrics.screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
            ) {
                Text("Η κίνηση δεν είναι πλέον διαθέσιμη.")
                OutlinedButton(onClick = onBack) { Text("Επιστροφή") }
            }
        }
        return
    }

    var saveRequested by rememberSaveable(item.id) { mutableStateOf(false) }

    if (item.pendingSync && !saveRequested) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { MyFinHubScreenHeader(title = "Επεξεργασία κίνησης", navigation = { MyFinHubBackButton(onBack) }) },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(MyFinHubDesignMetrics.screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
            ) {
                Text("Η κίνηση έχει εκκρεμή αλλαγή και δεν μπορεί να επεξεργαστεί ακόμη.")
                OutlinedButton(onClick = onBack) { Text("Επιστροφή") }
            }
        }
        return
    }

    var date by rememberSaveable(item.id) { mutableStateOf(item.rawDate.take(10)) }
    var note by rememberSaveable(item.id) { mutableStateOf(item.note) }
    var category by rememberSaveable(item.id) { mutableStateOf(item.category.orEmpty()) }
    var subcategory by rememberSaveable(item.id) { mutableStateOf(item.subcategory.orEmpty()) }
    var discardDialogOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var observedMutationInFlight by rememberSaveable(item.id) { mutableStateOf(false) }
    var requestedDate by rememberSaveable(item.id) { mutableStateOf("") }
    var requestedNote by rememberSaveable(item.id) { mutableStateOf("") }
    var requestedCategory by rememberSaveable(item.id) { mutableStateOf("") }
    var requestedSubcategory by rememberSaveable(item.id) { mutableStateOf("") }

    val effectiveCategoryOptions = buildList {
        addAll(categoryOptions)
        item.category?.takeIf(String::isNotBlank)?.let { current ->
            if (none { it.name == current }) add(ActivityCategoryOption(current, listOfNotNull(item.subcategory)))
        }
    }
    val subcategoryOptions = effectiveCategoryOptions.firstOrNull { it.name == category }?.subcategories.orEmpty()
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
    val dateError = date.isNotBlank() && parsedDate == null
    val dirty = date != item.rawDate.take(10) ||
        note != item.note ||
        category != item.category.orEmpty() ||
        subcategory != item.subcategory.orEmpty()
    val valid = parsedDate != null && note.isNotBlank()

    val requestBack = {
        when {
            mutationInFlight || saveRequested -> Unit
            dirty -> discardDialogOpen = true
            else -> onBack()
        }
    }
    BackHandler(onBack = requestBack)

    LaunchedEffect(
        item.rawDate,
        item.note,
        item.category,
        item.subcategory,
        mutationInFlight,
        saveRequested,
        requestedDate,
        requestedNote,
        requestedCategory,
        requestedSubcategory,
    ) {
        val persisted = saveRequested &&
            item.rawDate.take(10) == requestedDate &&
            item.note == requestedNote &&
            item.category.orEmpty() == requestedCategory &&
            item.subcategory.orEmpty() == requestedSubcategory
        when {
            persisted -> {
                saveRequested = false
                observedMutationInFlight = false
                onSaved()
            }
            saveRequested && mutationInFlight -> observedMutationInFlight = true
            saveRequested && observedMutationInFlight && !mutationInFlight -> {
                saveRequested = false
                observedMutationInFlight = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Επεξεργασία κίνησης",
                subtitle = item.title,
                navigation = { MyFinHubBackButton(requestBack) },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                MyFinHubPrimaryAction(
                    label = if (mutationInFlight || saveRequested) "Αποθήκευση…" else "Αποθήκευση αλλαγών",
                    onClick = {
                        requestedDate = date
                        requestedNote = note.trim()
                        requestedCategory = category
                        requestedSubcategory = subcategory
                        saveRequested = true
                        onSave(date, note.trim(), category, subcategory)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MyFinHubDesignMetrics.screenHorizontalPadding, vertical = MyFinHubSpacing.xs)
                        .navigationBarsPadding()
                        .imePadding(),
                    enabled = dirty && valid && !mutationBlocked && !saveRequested && !item.pendingSync,
                    icon = null,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = MyFinHubDesignMetrics.screenHorizontalPadding,
                    vertical = MyFinHubSpacing.md,
                ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            ActivityReadField("Ποσό", formatSignedEuro(item.amount))
            ActivityReadField("Τύπος", item.typeLabel)
            ActivityReadField(
                if (item.kind == ActivityKind.TRANSFER) "Προέλευση / προορισμός" else "Λογαριασμός",
                item.accountLabel,
            )
            Text(
                "Το ποσό, ο τύπος και ο λογαριασμός δεν αλλάζουν από αυτή την επεξεργασία.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DateEntryField(
                value = date,
                onValueChange = { if (!mutationInFlight && !saveRequested && !item.pendingSync) date = it },
                label = "Ημερομηνία",
                errorMessage = if (dateError) "Η ημερομηνία δεν είναι έγκυρη." else null,
            )
            if (item.supportsCategoryEdit() && effectiveCategoryOptions.isNotEmpty()) {
                S3ChoiceField(
                    label = "Κατηγορία",
                    selectedId = category,
                    choices = effectiveCategoryOptions.map { it.name to it.name },
                    enabled = !mutationInFlight && !saveRequested && !item.pendingSync,
                    onSelected = { selected ->
                        category = selected
                        val allowed = effectiveCategoryOptions.firstOrNull { it.name == selected }?.subcategories.orEmpty()
                        if (subcategory !in allowed) subcategory = ""
                    },
                )
                if (subcategoryOptions.isNotEmpty()) {
                    S3ChoiceField(
                        label = "Υποκατηγορία",
                        selectedId = subcategory,
                        choices = listOf("" to "Χωρίς υποκατηγορία") + subcategoryOptions.map { it to it },
                        enabled = !mutationInFlight && !saveRequested && !item.pendingSync,
                        onSelected = { subcategory = it },
                    )
                }
            }
            MyFinHubOutlinedField(
                value = note,
                onValueChange = { note = it },
                label = "Σημείωση",
                singleLine = false,
                enabled = !mutationInFlight && !saveRequested && !item.pendingSync,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }

    if (discardDialogOpen) {
        AlertDialog(
            onDismissRequest = { discardDialogOpen = false },
            title = { Text("Απόρριψη αλλαγών;") },
            text = { Text("Οι αλλαγές αυτής της κίνησης δεν έχουν αποθηκευτεί.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        discardDialogOpen = false
                        onBack()
                    },
                ) { Text("Απόρριψη") }
            },
            dismissButton = {
                TextButton(onClick = { discardDialogOpen = false }) { Text("Συνέχεια επεξεργασίας") }
            },
        )
    }
}

@Composable
private fun S3ChoiceField(
    label: String,
    selectedId: String,
    choices: List<Pair<String, String>>,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.first == selectedId }?.second
        ?: choices.firstOrNull()?.second
        ?: "Δεν υπάρχει διαθέσιμη επιλογή"
    Box(modifier = Modifier.fillMaxWidth()) {
        MyFinHubSelectorButton(
            label = label,
            onClick = { if (enabled && choices.isNotEmpty()) expanded = true },
            enabled = enabled && choices.isNotEmpty(),
        ) {
            Text(selectedLabel, modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelected(id)
                    },
                )
            }
        }
    }
}

@Composable
private fun S3ActivityMonthHeader(rawDate: String) {
    val date = runCatching { LocalDate.parse(rawDate.take(10)) }.getOrNull()
    val locale = Locale.forLanguageTag("el-GR")
    val label = date?.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        ?: rawDate
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(top = MyFinHubSpacing.md, bottom = MyFinHubSpacing.xxs),
    )
}

@Composable
private fun S3ActivityDayHeader(rawDate: String) {
    val date = runCatching { LocalDate.parse(rawDate.take(10)) }.getOrNull()
    val today = LocalDate.now()
    val locale = Locale.forLanguageTag("el-GR")
    val label = when (date) {
        today -> "Σήμερα"
        today.minusDays(1) -> "Χθες"
        null -> rawDate
        else -> date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = MyFinHubSpacing.xxs),
    )
}

private fun formatActivityDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    return runCatching {
        LocalDate.parse(raw.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("el-GR")))
    }.getOrDefault(raw)
}

private fun ActivityItem.s3Tone(): FinanceTone = when {
    pendingSync -> FinanceTone.Neutral
    kind == ActivityKind.TRANSFER -> FinanceTone.Neutral
    kind == ActivityKind.EXPENSE -> FinanceTone.Expense
    kind == ActivityKind.INCOME -> FinanceTone.Income
    kind == ActivityKind.CARD_PAYMENT -> FinanceTone.Transfer
    else -> FinanceTone.Neutral
}

private fun ActivityKind.s3Icon() = when (this) {
    ActivityKind.EXPENSE -> MyFinHubIcons.Expense
    ActivityKind.INCOME -> MyFinHubIcons.Income
    ActivityKind.TRANSFER -> MyFinHubIcons.Transfer
    ActivityKind.CARD_PAYMENT -> MyFinHubIcons.Card
}
