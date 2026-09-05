package app.myfinhub.android.feature.activity

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubDestructiveTextAction
import app.myfinhub.android.designsystem.MyFinHubFilterChip
import app.myfinhub.android.designsystem.MyFinHubFinanceRow
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSearchField
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSelectorButton
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.myFinHubCategoryIcon
import app.myfinhub.android.feature.quickentry.DateEntryField
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onAction: (ActivityAction) -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenQuickEntry: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Κινήσεις",
                subtitle = "Η οικονομική σου δραστηριότητα",
            )
        },
        floatingActionButton = {
            MyFinHubPrimaryAction(
                label = "Νέα κίνηση",
                onClick = onOpenQuickEntry,
                modifier = Modifier.semantics {
                    contentDescription = "Δημιουργία νέας κίνησης"
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            if (maxWidth >= 840.dp) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    ActivityList(
                        state = state,
                        onAction = onAction,
                        onSelect = { id -> onAction(ActivityAction.Select(id)) },
                        modifier = Modifier.weight(1.15f),
                    )
                    val selected = state.selectedItem ?: state.visibleItems.firstOrNull()
                    if (selected != null) {
                        ActivityDetailContent(
                            item = selected,
                            categoryOptions = state.categoryOptionsFor(selected),
                            onSave = { date, note, category, subcategory ->
                                onAction(
                                    ActivityAction.SaveEdit(
                                        id = selected.id,
                                        note = note,
                                        category = category,
                                        date = date,
                                        subcategory = subcategory,
                                    ),
                                )
                            },
                            onDelete = { onAction(ActivityAction.Delete(selected.id)) },
                            modifier = Modifier.weight(0.85f),
                        )
                    }
                }
            } else {
                ActivityList(
                    state = state,
                    onAction = onAction,
                    onSelect = onOpenDetail,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ActivityList(
    state: ActivityUiState,
    onAction: (ActivityAction) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    var contextItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = MyFinHubDesignMetrics.screenHorizontalPadding,
            top = MyFinHubSpacing.xs,
            end = MyFinHubDesignMetrics.screenHorizontalPadding,
            bottom = MyFinHubDesignMetrics.navigationContentBottomClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
    ) {
        item {
            MyFinHubSearchField(
                value = state.query,
                onValueChange = { onAction(ActivityAction.QueryChanged(it)) },
                placeholder = "Αναζήτηση κινήσεων",
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                ActivityFilter.entries.forEach { filter ->
                    MyFinHubFilterChip(
                        selected = state.filter == filter,
                        onClick = { onAction(ActivityAction.FilterChanged(filter)) },
                        label = filter.label,
                        icon = filter.icon(),
                        tone = filter.tone(),
                    )
                }
            }
        }
        if (state.visibleItems.isEmpty()) {
            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Δεν βρέθηκαν κινήσεις με αυτά τα φίλτρα.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            state.visibleSections.forEachIndexed { sectionIndex, section ->
        val monthKey = section.date.take(7)
        val previousMonth = state.visibleSections.getOrNull(sectionIndex - 1)?.date?.take(7)
        if (sectionIndex == 0 || monthKey != previousMonth) {
            item(key = "month-$monthKey") { ActivityMonthHeader(section.date) }
        }
        item(key = "day-${section.date}") { ActivityDayHeader(section.date) }
        items(section.items, key = ActivityItem::id) { item ->
            Box {
                MyFinHubFinanceRow(
                    icon = myFinHubCategoryIcon(item.category, item.kind.icon()),
                    iconDescription = item.category ?: item.kind.label,
                    title = item.title,
                    subtitle = item.subtitle,
                    meta = if (item.pendingSync) "Εκκρεμεί επιβεβαίωση" else item.accountLabel,
                    amountText = formatSignedEuro(item.amount),
                    tone = if (item.pendingSync) FinanceTone.Neutral else item.kind.tone(),
                    onClick = { onSelect(item.id) },
                    onLongClick = { contextItemId = item.id },
                    modifier = if (item.pendingSync) Modifier.alpha(0.74f) else Modifier,
                )
                DropdownMenu(
                    expanded = contextItemId == item.id,
                    onDismissRequest = { contextItemId = null },
                ) {
                    DropdownMenuItem(
                        text = { Text("Λεπτομέρειες / επεξεργασία") },
                        onClick = {
                            contextItemId = null
                            onSelect(item.id)
                        },
                    )
                    if (!item.pendingSync) {
                        DropdownMenuItem(
                            text = { Text("Διαγραφή") },
                            onClick = {
                                contextItemId = null
                                confirmDeleteId = item.id
                            },
                        )
                    }
                }
            }
        }
    }
        }
    }

    val deleteItem = state.items.firstOrNull { it.id == confirmDeleteId }
    if (deleteItem != null && !deleteItem.pendingSync) {
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("Διαγραφή κίνησης;") },
            text = { Text("Η κίνηση θα αφαιρεθεί από τα οικονομικά δεδομένα και θα ενημερωθούν τα σχετικά υπόλοιπα.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = deleteItem.id
                    confirmDeleteId = null
                    onAction(ActivityAction.Delete(id))
                }) { Text("Διαγραφή") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text("Πίσω") }
            },
        )
    }
}


@Composable
private fun ActivityMonthHeader(rawDate: String) {
    val date = runCatching { LocalDate.parse(rawDate.take(10)) }.getOrNull()
    val locale = Locale.forLanguageTag("el-GR")
    val label = date?.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        ?: rawDate
    Text(
        text = label,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(top = MyFinHubSpacing.sm, bottom = MyFinHubSpacing.xxs),
    )
}

@Composable
private fun ActivityDayHeader(rawDate: String) {
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
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = MyFinHubSpacing.xs),
    )
}

@Composable
fun ActivityDetailScreen(
    item: ActivityItem?,
    categoryOptions: List<ActivityCategoryOption>,
    onBack: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
    onDelete: () -> Unit,
) {
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
            MyFinHubSectionCard(
                modifier = Modifier.padding(padding).padding(MyFinHubSpacing.lg).fillMaxWidth(),
            ) {
                Text("Η κίνηση δεν είναι πλέον διαθέσιμη.")
            }
        } else {
            ActivityDetailContent(
                item = item,
                categoryOptions = categoryOptions,
                onSave = onSave,
                onDelete = onDelete,
                modifier = Modifier.padding(padding).padding(MyFinHubSpacing.lg),
            )
        }
    }
}

@Composable
private fun ActivityDetailContent(
    item: ActivityItem,
    categoryOptions: List<ActivityCategoryOption>,
    onSave: (String, String, String, String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    var date by rememberSaveable(item.id, item.rawDate) { mutableStateOf(item.rawDate.take(10)) }
    var note by rememberSaveable(item.id, item.subtitle) { mutableStateOf(item.subtitle) }
    var category by rememberSaveable(item.id, item.category) { mutableStateOf(item.category.orEmpty()) }
    var subcategory by rememberSaveable(item.id, item.subcategory) { mutableStateOf(item.subcategory.orEmpty()) }
    var confirmDelete by rememberSaveable(item.id) { mutableStateOf(false) }
    val effectiveCategoryOptions = categoryOptions.ifEmpty {
        item.category?.takeIf(String::isNotBlank)?.let { listOf(ActivityCategoryOption(it)) }.orEmpty()
    }
    val subcategoryOptions = effectiveCategoryOptions.firstOrNull { it.name == category }?.subcategories.orEmpty()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
    ) {
        MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            ) {
                MyFinHubIconBadge(
                    icon = myFinHubCategoryIcon(item.category, item.kind.icon()),
                    tone = if (item.pendingSync) FinanceTone.Neutral else item.kind.tone(),
                    contentDescription = item.category ?: item.kind.label,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    MyFinHubAmountText(
                        text = formatSignedEuro(item.amount),
                        tone = if (item.pendingSync) FinanceTone.Neutral else item.kind.tone(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "${item.kind.label} · ${item.dateLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.pendingSync) {
                        Text(
                            text = "Εκκρεμεί επιβεβαίωση από τον server",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DateEntryField(
            value = date,
            onValueChange = { date = it },
            label = "Ημερομηνία",
            errorMessage = null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (effectiveCategoryOptions.isNotEmpty()) {
            ActivityChoiceField(
                label = "Κατηγορία",
                selectedId = category,
                choices = effectiveCategoryOptions.map { it.name to it.name },
                enabled = !item.pendingSync,
                onSelected = { selected ->
                    category = selected
                    val allowed = effectiveCategoryOptions.firstOrNull { it.name == selected }?.subcategories.orEmpty()
                    if (subcategory !in allowed) subcategory = ""
                },
            )
            if (subcategoryOptions.isNotEmpty()) {
                ActivityChoiceField(
                    label = "Υποκατηγορία",
                    selectedId = subcategory,
                    choices = listOf("" to "Χωρίς υποκατηγορία") + subcategoryOptions.map { it to it },
                    enabled = !item.pendingSync,
                    onSelected = { subcategory = it },
                )
            }
        }
        MyFinHubOutlinedField(
            value = note,
            onValueChange = { note = it },
            label = "Σημείωση",
            singleLine = false,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            enabled = !item.pendingSync,
        )
        MyFinHubPrimaryAction(
            label = "Αποθήκευση αλλαγών",
            onClick = { onSave(date, note, category, subcategory) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !item.pendingSync && note.isNotBlank() && date.isNotBlank() && (
                date != item.rawDate.take(10) ||
                    note != item.subtitle ||
                    category != item.category.orEmpty() ||
                    subcategory != item.subcategory.orEmpty()
            ),
            icon = null,
        )
        if (item.pendingSync) {
            Text(
                "Δεν επιτρέπεται νέα επεξεργασία ή διαγραφή μέχρι να επιβεβαιωθεί η εκκρεμής αλλαγή. Αν η τελευταία αλλαγή δεν έχει σταλεί ακόμη, η ασφαλής αναίρεση εμφανίζεται στην κεντρική ένδειξη εκκρεμών αλλαγών.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MyFinHubDestructiveTextAction(
                label = "Διαγραφή κίνησης",
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
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

    if (confirmDelete && !item.pendingSync) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text("Διαγραφή κίνησης;")
            },
            text = {
                Text(
                    "Η κίνηση θα αφαιρεθεί από τα οικονομικά δεδομένα και θα ενημερωθούν τα σχετικά υπόλοιπα.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("Διαγραφή")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Πίσω")
                }
            },
        )
    }
}


@Composable
private fun ActivityChoiceField(
    label: String,
    selectedId: String,
    choices: List<Pair<String, String>>,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    var expanded by rememberSaveable(label, selectedId) { mutableStateOf(false) }
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

private fun ActivityFilter.icon(): ImageVector = when (this) {
    ActivityFilter.ALL -> MyFinHubIcons.All
    ActivityFilter.EXPENSE -> MyFinHubIcons.Expense
    ActivityFilter.INCOME -> MyFinHubIcons.Income
    ActivityFilter.TRANSFER -> MyFinHubIcons.Transfer
}

private fun ActivityFilter.tone(): FinanceTone = when (this) {
    ActivityFilter.ALL -> FinanceTone.Neutral
    ActivityFilter.EXPENSE -> FinanceTone.Expense
    ActivityFilter.INCOME -> FinanceTone.Income
    ActivityFilter.TRANSFER -> FinanceTone.Transfer
}

private fun ActivityKind.icon(): ImageVector = when (this) {
    ActivityKind.EXPENSE -> MyFinHubIcons.Expense
    ActivityKind.INCOME -> MyFinHubIcons.Income
    ActivityKind.TRANSFER -> MyFinHubIcons.Transfer
    ActivityKind.CARD_PAYMENT -> MyFinHubIcons.Card
}

private fun ActivityKind.tone(): FinanceTone = when (this) {
    ActivityKind.EXPENSE -> FinanceTone.Expense
    ActivityKind.INCOME -> FinanceTone.Income
    ActivityKind.TRANSFER -> FinanceTone.Transfer
    ActivityKind.CARD_PAYMENT -> FinanceTone.Transfer
}

private fun formatSignedEuro(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(amount)
