package app.myfinhub.android.feature.activity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.ui.financialProvider
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubDestructiveTextAction
import app.myfinhub.android.designsystem.MyFinHubFilterChip
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubProviderMark
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
                subtitle = "Όλες οι καταχωρισμένες κινήσεις",
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MyFinHubDesignMetrics.screenHorizontalPadding,
                            vertical = MyFinHubSpacing.xs,
                        ),
                    horizontalArrangement = Arrangement.End,
                ) {
                    MyFinHubPrimaryAction(
                        label = "Νέα κίνηση",
                        onClick = onOpenQuickEntry,
                        modifier = Modifier.semantics {
                            contentDescription = "Δημιουργία νέας κίνησης"
                        },
                    )
                }
            }
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
        modifier = modifier.testTag("activity_list"),
        contentPadding = PaddingValues(
            start = MyFinHubDesignMetrics.screenHorizontalPadding,
            top = MyFinHubSpacing.xxs,
            end = MyFinHubDesignMetrics.screenHorizontalPadding,
            bottom = MyFinHubDesignMetrics.productSnackbarBottomClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
    ) {
        item {
            MyFinHubSearchField(
                value = state.query,
                onValueChange = { onAction(ActivityAction.QueryChanged(it)) },
                placeholder = "Αναζήτηση κινήσεων",
            )
        }
        item {
            ActivityTypeFilters(
                selected = state.filter,
                onSelected = { onAction(ActivityAction.FilterChanged(it)) },
            )
        }
        if (state.accountOptions.isNotEmpty()) {
            item {
                ActivityAccountFilterSelector(
                    selectedId = state.accountFilterId,
                    options = state.accountOptions,
                    onSelected = { onAction(ActivityAction.AccountFilterChanged(it)) },
                )
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
                        ActivityLedgerRow(
                            item = item,
                            accountOptions = state.accountOptions,
                            onClick = { onSelect(item.id) },
                            onLongClick = { contextItemId = item.id },
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
private fun ActivityTypeFilters(
    selected: ActivityFilter,
    onSelected: (ActivityFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
    ) {
        ActivityFilter.values().forEach { filter ->
            MyFinHubFilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = filter.label,
                icon = filter.icon(),
                tone = filter.tone(),
            )
        }
    }
}

@Composable
private fun ActivityLedgerRow(
    item: ActivityItem,
    accountOptions: List<ActivityAccountOption>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tone = when {
        item.pendingSync -> FinanceTone.Neutral
        item.kind == ActivityKind.TRANSFER -> FinanceTone.Neutral
        else -> item.kind.tone()
    }
    val expandedTransferRoute = item.kind == ActivityKind.TRANSFER && LocalDensity.current.fontScale >= 1.3f
    val subtitle = if (item.kind == ActivityKind.TRANSFER) {
        val route = activityTransferRouteLabel(item, accountOptions)
        if (expandedTransferRoute) route.replace(" → Προς ", "\nΠρος ") else route
    } else {
        item.subtitle
    }
    val meta = when {
        item.pendingSync -> "Εκκρεμεί επιβεβαίωση"
        item.kind == ActivityKind.TRANSFER -> "Εσωτερική μεταφορά"
        else -> item.accountLabel
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (item.pendingSync) Modifier.alpha(0.74f) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = MyFinHubDesignMetrics.cardElevation),
        border = BorderStroke(
            MyFinHubDesignMetrics.cardBorderWidth,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MyFinHubDesignMetrics.rowHorizontalPadding,
                vertical = MyFinHubSpacing.xs,
            ),
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MyFinHubIconBadge(
                icon = myFinHubCategoryIcon(item.category, item.kind.icon()),
                tone = tone,
                contentDescription = item.category ?: item.kind.label,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (expandedTransferRoute) {
                    val routeLines = subtitle.split("\n", limit = 2)
                    Text(
                        text = routeLines.first(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (routeLines.size == 2) {
                        Text(
                            text = routeLines[1],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(MyFinHubSpacing.xxs))
            MyFinHubAmountText(
                text = formatSignedEuro(item.amount),
                tone = tone,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityAccountFilterSelector(
    selectedId: String?,
    options: List<ActivityAccountOption>,
    onSelected: (String?) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.id == selectedId }?.label ?: "Όλοι οι λογαριασμοί"

    MyFinHubSelectorButton(
        label = "Λογαριασμός",
        onClick = { expanded = true },
        enabled = options.isNotEmpty(),
    ) {
        Text(
            selectedLabel,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
        ) {
            ActivityAccountFilterSheetContent(
                selectedId = selectedId,
                options = options,
                onSelected = { id ->
                    expanded = false
                    onSelected(id)
                },
            )
        }
    }
}

@Composable
internal fun ActivityAccountFilterSheetContent(
    selectedId: String?,
    options: List<ActivityAccountOption>,
    onSelected: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = MyFinHubSpacing.md),
    ) {
        Text(
            text = "Λογαριασμός",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = MyFinHubSpacing.xxs,
            ),
        )
        Text(
            text = "Εμφάνισε κινήσεις από έναν λογαριασμό ή όλες μαζί.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = MyFinHubSpacing.xs,
            ),
        )
        ActivityAccountFilterRow(
            label = "Όλοι οι λογαριασμοί",
            option = null,
            selected = selectedId == null,
            onClick = { onSelected(null) },
        )
        options.forEach { option ->
            ActivityAccountFilterRow(
                label = option.label,
                option = option,
                selected = option.id == selectedId,
                onClick = { onSelected(option.id) },
            )
        }
    }
}

@Composable
private fun ActivityAccountFilterRow(
    label: String,
    option: ActivityAccountOption?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val provider = option?.let { financialProvider(it.id, it.label) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(
                horizontal = MyFinHubDesignMetrics.screenHorizontalPadding,
                vertical = MyFinHubSpacing.xs,
            ),
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            provider != null -> {
                MyFinHubProviderMark(
                    provider = provider,
                    modifier = Modifier.size(MyFinHubDesignMetrics.iconBadgeSize),
                    contentDescription = provider.institutionLabel,
                )
            }
            option == null -> {
                MyFinHubIconBadge(
                    icon = MyFinHubIcons.All,
                    tone = FinanceTone.Neutral,
                    contentDescription = null,
                )
            }
            else -> {
                MyFinHubIconBadge(
                    icon = MyFinHubIcons.Account,
                    tone = FinanceTone.Neutral,
                    contentDescription = null,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            provider?.let {
                Text(
                    text = it.institutionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
    }
}

internal fun activityTransferRouteLabel(
    item: ActivityItem,
    accountOptions: List<ActivityAccountOption>,
): String {
    val from = item.fromAccountId?.let { id -> accountOptions.firstOrNull { it.id == id }?.label }
    val to = item.toAccountId?.let { id -> accountOptions.firstOrNull { it.id == id }?.label }
    if (!from.isNullOrBlank() && !to.isNullOrBlank()) {
        return "Από $from → Προς $to"
    }

    val route = item.accountLabel
        .replace("->", "→")
        .split("→", limit = 2)
        .map(String::trim)
    if (route.size == 2 && route.all(String::isNotBlank)) {
        return "Από ${route[0]} → Προς ${route[1]}"
    }
    return item.subtitle
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
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MyFinHubSpacing.xs),
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
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = MyFinHubSpacing.xxs),
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
    val detailTone = if (item.pendingSync || item.kind == ActivityKind.TRANSFER) {
        FinanceTone.Neutral
    } else {
        item.kind.tone()
    }

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
                    tone = detailTone,
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
                        tone = detailTone,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "${item.kind.label} · ${item.dateLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.kind == ActivityKind.TRANSFER) {
                        Text(
                            text = activityTransferRouteLabel(item, emptyList()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
    ActivityFilter.TRANSFER -> FinanceTone.Neutral
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
    ActivityKind.TRANSFER -> FinanceTone.Neutral
    ActivityKind.CARD_PAYMENT -> FinanceTone.Transfer
}

internal fun formatSignedEuro(amount: Double): String {
    val formatted = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR"))
        .format(kotlin.math.abs(amount))
    return when {
        amount > 0.0 -> "+$formatted"
        amount < 0.0 -> "−$formatted"
        else -> formatted
    }
}

private fun formatUnsignedEuro(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(kotlin.math.abs(amount))