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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
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
import app.myfinhub.android.core.data.moneyToCents
import app.myfinhub.android.feature.quickentry.DateEntryField
import app.myfinhub.android.feature.utilities.amountVisibilityText
import app.myfinhub.android.feature.utilities.rememberAmountVisibilityPreference
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun activityTransferRouteLabel(
    item: ActivityItem,
    accountOptions: List<ActivityAccountOption>,
): String {
    // Real Web TransactionsPage details show accountText(row.accountIds),
    // not a reconstructed from/to transfer route. Using optional endpoint
    // hints here can erase an imported third account or disagree with actual
    // canonical event legs. Keep the explicit demo wording for synthetic UI
    // previews which do not have a persisted source ordinal.
    if (item.isLegacyTransaction || item.canonicalSourceOrdinal >= 0) return item.accountLabel

    val from = item.fromAccountId?.let { id -> accountOptions.firstOrNull { it.id == id }?.label }
    val to = item.toAccountId?.let { id -> accountOptions.firstOrNull { it.id == id }?.label }
    if (!from.isNullOrBlank() && !to.isNullOrBlank()) return "Από $from → Προς $to"

    val route = item.accountLabel.replace("->", "→").split("→", limit = 2).map(String::trim)
    if (route.size == 2 && route.all(String::isNotBlank)) return "Από ${route[0]} → Προς ${route[1]}"
    return item.subtitle
}

/** Match the Web transaction detail's categoryLabel for persisted rows.
 * Preview-only fixtures retain their stand-alone "Χωρίς κατηγορία" fallback.
 */
internal fun activityDetailCategoryLabel(item: ActivityItem): String =
    if (item.isLegacyTransaction || item.canonicalSourceOrdinal >= 0) {
        item.webLedgerCategoryLabel()
    } else {
        item.category?.takeIf(String::isNotBlank) ?: "Χωρίς κατηγορία"
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

/**
 * Web TransactionsPage amountPrefix/amountClass derive from flowImpact, not
 * the original legacy bank type. Reviewed historic expenses can become refunds
 * or non-operational transfers. Format without changing raw canonical amounts.
 */
internal fun formatActivityFlowEuro(item: ActivityItem): String {
    val income = item.financeIncomeContribution
    val expense = item.financeExpenseContribution
    if (income == null || expense == null) return formatSignedEuro(item.amount)
    val money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR"))
        .format(kotlin.math.abs(item.amount))
    return when {
        item.canonicalKind == "transfer" -> "↔ $money"
        income > 0.0 || expense < 0.0 -> "+$money"
        expense > 0.0 -> "−$money"
        else -> money
    }
}

/** S3 production Activity surface: one dense ledger, one exact filter sheet and no gesture-only actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLedgerScreen(
    state: ActivityUiState,
    onAction: (ActivityAction) -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenQuickEntry: () -> Unit,
    onBack: (() -> Unit)? = null,
    categoryIconColors: Map<String, String> = emptyMap(),
    subcategoryIconColors: Map<String, String> = emptyMap(),
) {
    val amountsVisible = rememberAmountVisibilityPreference()
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
                state.monthOverview?.let { overview ->
                    item(key = "monthly-overview") {
                        ActivityMonthlyOverviewSurface(
                            overview = overview,
                            amountsVisible = amountsVisible,
                            showingMonth = state.showingOverviewMonth,
                            onToggleMonth = {
                                onAction(
                                    if (state.showingOverviewMonth) ActivityAction.ClearSearchAndFilters
                                    else ActivityAction.ShowOverviewMonth,
                                )
                            },
                            onNavigateMonth = { delta ->
                                onAction(ActivityAction.NavigateOverviewMonth(delta))
                            },
                        )
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
                        if (state.hasActiveListScope && (state.visibleItems.isNotEmpty() || state.showingOverviewMonth)) {
                            TextButton(
                                onClick = { onAction(ActivityAction.ClearFilters) },
                                modifier = Modifier.testTag("activity_clear_all"),
                            ) {
                                Text("Καθαρισμός")
                            }
                        }
                    }
                }
                // Keep short histories visually compact; expose Web-style date
                // sorting and total/filtered counts for large real-bank ledgers.
                if (state.scopeSourceItems.size > 20) {
                    item(key = "history-order-and-count") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Εμφανίζονται ${state.visibleItems.size} από ${state.scopeSourceItems.size} κινήσεις",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { onAction(ActivityAction.ToggleSortDirection) },
                                modifier = Modifier.testTag("activity_sort_toggle"),
                            ) {
                                Text(if (state.sortOldestFirst) "Παλαιότερες πρώτα" else "Νεότερες πρώτα")
                            }
                        }
                    }
                }
                if (state.hasActiveListScope && state.visibleItems.isNotEmpty()) {
                    item(key = "filtered-flow-summary") {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .testTag("activity_filtered_summary")
                                .padding(vertical = MyFinHubSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                        ) {
                            Text(
                                "Βρέθηκαν ${state.visibleItems.size} από ${state.scopeSourceItems.size} κινήσεις",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "Έσοδα: ${amountVisibilityText(formatSignedEuro(state.visibleIncome), amountsVisible)}" +
                                    " · Έξοδα: ${amountVisibilityText(formatSignedEuro(state.visibleExpense), amountsVisible)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Καθαρό: ${amountVisibilityText(formatSignedEuro(state.visibleNet), amountsVisible)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                state.scopeSourceItems.isEmpty() -> {
                    item(key = "empty-ledger") {
                        ActivityEmptyState(
                            title = if (state.showingOverviewMonth) {
                                "Δεν υπάρχουν συναλλαγές σε αυτή την περίοδο."
                            } else "Δεν υπάρχουν καταχωρισμένες κινήσεις.",
                            actionLabel = when {
                                analyticsScope -> null
                                state.hasActiveListScope -> "Επαναφορά φίλτρων"
                                else -> "Νέα κίνηση"
                            },
                            onAction = when {
                                analyticsScope -> null
                                state.hasActiveListScope -> { { onAction(ActivityAction.ClearFilters) } }
                                else -> onOpenQuickEntry
                            },
                        )
                    }
                }
                state.visibleItems.isEmpty() -> {
                    item(key = "empty-filtered") {
                        ActivityEmptyState(
                            title = "Δεν βρέθηκαν κινήσεις με αυτή την αναζήτηση ή τα φίλτρα.",
                            actionLabel = if (analyticsScope) null else "Καθαρισμός αναζήτησης και φίλτρων",
                            onAction = if (analyticsScope) null else {
                                { onAction(ActivityAction.ClearFilters) }
                            },
                        )
                    }
                }
                else -> {
                    state.visibleSections.forEachIndexed { sectionIndex, section ->
                        val monthKey = section.date.take(7)
                        val previousMonth = state.visibleSections.getOrNull(sectionIndex - 1)?.date?.take(7)
                        if (sectionIndex == 0 || monthKey != previousMonth) {
                            item(key = "month-$sectionIndex-$monthKey") { S3ActivityMonthHeader(section.date) }
                        }
                        item(key = "day-$sectionIndex-${section.date}") { S3ActivityDayHeader(section.date) }
                        // Original legacy seed rows can share an ID. The canonical
                        // projection retains both (financial totals must not lose
                        // historic duplicates), so LazyColumn keys must be unique.
                        itemsIndexed(
                            section.items,
                            key = { itemIndex, item -> "row-$sectionIndex-$itemIndex-${item.id}" },
                        ) { _, item ->
                            ActivityFlatLedgerRow(
                                item = item,
                                accountOptions = state.accountOptions,
                                amountsVisible = amountsVisible,
                                ambiguousIdentity = item.id in state.ambiguousIds,
                                categoryIconColors = categoryIconColors,
                                subcategoryIconColors = subcategoryIconColors,
                                onClick = {
                                    if (item.id !in state.ambiguousIds) onOpenDetail(item.id)
                                },
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


/** Unfiltered Web month and previous-month comparisons, separate from
 * the native long-history filtered amount summary. Hidden totals stay hidden.
 */
@Composable
private fun ActivityMonthlyOverviewSurface(
    overview: ActivityMonthlyOverview,
    amountsVisible: Boolean,
    showingMonth: Boolean,
    onToggleMonth: () -> Unit,
    onNavigateMonth: (Int) -> Unit,
) {
    val monthLabel = runCatching {
        YearMonth.parse(overview.month).atDay(1)
            .format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("el-GR")))
    }.getOrDefault(overview.month)
    fun trend(current: Double, previous: Double): String {
        val change = activityMonthPercentChange(current, previous)
            ?: return "— έναντι προηγ. μήνα"
        val arrow = when {
            change > 0 -> "↑"
            change < 0 -> "↓"
            else -> "→"
        }
        return "$arrow ${kotlin.math.abs(change)}% από προηγ. μήνα"
    }
    val countChange = overview.transactionCount - overview.previousTransactionCount
    val countComparison = if (countChange == 0) "→ ίδιο με προηγ. μήνα"
        else "${if (countChange > 0) "↑" else "↓"} ${kotlin.math.abs(countChange)} από προηγ. μήνα"
    Surface(
        modifier = Modifier.fillMaxWidth()
            .padding(bottom = MyFinHubSpacing.md)
            .testTag("activity_monthly_overview"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(MyFinHubSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Σύνοψη μήνα · $monthLabel", style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(
                        onClick = { onNavigateMonth(-1) },
                        modifier = Modifier.testTag("activity_previous_month")
                            .semantics { contentDescription = "Προηγούμενος μήνας" },
                    ) { Text("‹") }
                    TextButton(
                        onClick = { onNavigateMonth(1) },
                        modifier = Modifier.testTag("activity_next_month")
                            .semantics { contentDescription = "Επόμενος μήνας" },
                    ) { Text("›") }
                }
            }
            Text(
                "Έσοδα: ${amountVisibilityText(formatSignedEuro(overview.income), amountsVisible)}" +
                    " · Έξοδα: ${amountVisibilityText(formatSignedEuro(overview.expense), amountsVisible)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Καθαρό αποτέλεσμα: ${amountVisibilityText(formatSignedEuro(overview.net), amountsVisible)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (amountsVisible) {
                Text(
                    "Έσοδα: ${trend(overview.income, overview.previousIncome)}" +
                        " · Έξοδα: ${trend(overview.expense, overview.previousExpense)}" +
                        " · Καθαρό: ${trend(overview.net, overview.previousNet)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Συναλλαγές μήνα: ${overview.transactionCount} · $countComparison",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onToggleMonth,
                modifier = Modifier.testTag("activity_month_scope_toggle"),
            ) {
                Text(if (showingMonth) "Όλο το ιστορικό" else "Κινήσεις αυτού του μήνα")
            }
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
        if (state.hasActiveDateFilter) {
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
    amountsVisible: Boolean,
    ambiguousIdentity: Boolean,
    categoryIconColors: Map<String, String>,
    subcategoryIconColors: Map<String, String>,
    onClick: () -> Unit,
) {
    val tone = item.s3Tone()
    val noteParts = item.webNoteParts()
    val realWebRow = item.isLegacyTransaction || item.canonicalSourceOrdinal >= 0
    val secondary = when {
        realWebRow && noteParts.comment.isNotEmpty() -> noteParts.comment
        realWebRow && item.canonicalKind == "split" && item.splitParts.size >= 2 ->
            "${item.webLedgerCategoryLabel()} · ${item.splitParts.size} μέρη"
        realWebRow -> item.webLedgerCategoryLabel()
        // Keep synthetic preview screenshots' existing standalone wording.
        item.isInternalTransfer() -> activityTransferRouteLabel(item, accountOptions)
        item.canonicalKind == "split" && item.splitParts.size >= 2 ->
            "${item.splitParts.size} μέρη" +
                (if (item.note.isBlank()) "" else " · ${noteParts.title}")
        noteParts.comment.isNotEmpty() -> noteParts.comment
        else -> item.note
    }
    val meta = when {
        ambiguousIdentity -> "Διπλό αναγνωριστικό · απαιτεί έλεγχο"
        item.pendingSync -> item.pendingLabel ?: "Εκκρεμεί συγχρονισμός"
        item.isInternalTransfer() -> if (realWebRow)
            "${item.typeLabel} · ${item.accountLabel}" else "Εσωτερική μεταφορά"
        item.canonicalKind == "card_purchase" || item.canonicalKind == "card_payment" ->
            "${item.typeLabel} · ${item.accountLabel}"
        item.isLegacyTransaction ->
            "${item.accountLabel} · ${item.legacyHistoryLabel()}"
        else -> item.accountLabel
    }

    // Web preference key: kind:category[:subcategory]. Invalid colors preserve tone.
    val categoryKind = if (item.canonicalKind == "income") "income" else "expense"
    val parentKey = item.category?.takeIf(String::isNotBlank)?.let {
        "$categoryKind:$it"
    }
    val childKey = item.subcategory?.takeIf(String::isNotBlank)?.let { child ->
        parentKey?.let { "$it:$child" }
    }
    val preferredHex = childKey?.let(subcategoryIconColors::get)
        ?: parentKey?.let(categoryIconColors::get)
    val selectedColor = preferredHex?.takeIf { Regex("^#[0-9A-Fa-f]{6}$").matches(it) }
        ?.let { Color(("FF" + it.drop(1)).toLong(16).toInt()) }

    MyFinHubFinanceRow(
        icon = myFinHubCategoryIcon(item.category, item.kind.s3Icon()),
        categoryAccent = selectedColor,
        iconDescription = null,
        title = item.webLedgerDisplayTitle(),
        subtitle = secondary,
        meta = meta,
        amountText = amountVisibilityText(formatActivityFlowEuro(item), amountsVisible),
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
    // Web AppDateInput constrains each date to the selected reporting month.
    // The native all-history extension deliberately has no such limit.
    val monthBounds = state.monthOverview?.month
        ?.takeIf { state.showingOverviewMonth }
        ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
    val outsideFromMonth = monthBounds != null && fromParsed != null &&
        (fromParsed < monthBounds.atDay(1) || fromParsed > monthBounds.atEndOfMonth())
    val outsideToMonth = monthBounds != null && toParsed != null &&
        (toParsed < monthBounds.atDay(1) || toParsed > monthBounds.atEndOfMonth())
    val dateError = invalidFrom || invalidTo || invertedRange ||
        outsideFromMonth || outsideToMonth

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
        monthBounds?.let { selectedMonth ->
            Text(
                "Περίοδος: ${selectedMonth.atDay(1)} – ${selectedMonth.atEndOfMonth()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                outsideFromMonth -> "Η ημερομηνία πρέπει να είναι μέσα στον επιλεγμένο μήνα."
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
                outsideToMonth -> "Η ημερομηνία πρέπει να είναι μέσα στον επιλεγμένο μήνα."
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
    historicalBalance: Double? = null,
    counterpartyRows: List<app.myfinhub.android.core.data.CanonicalCounterpartyRow> = emptyList(),
    onChooseCounterparty: ((String?) -> Unit)? = null,
) {
    val amountsVisible = rememberAmountVisibilityPreference()
    var counterpartyMenuOpen by remember { mutableStateOf(false) }
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
                modifier = Modifier.fillMaxSize().padding(padding).testTag("activity_detail_list"),
                contentPadding = PaddingValues(
                    horizontal = MyFinHubDesignMetrics.screenHorizontalPadding,
                    vertical = MyFinHubSpacing.md,
                ),
            ) {
                item(key = "amount") {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                        MyFinHubAmountText(
                            text = amountVisibilityText(formatActivityFlowEuro(item), amountsVisible),
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
                        if (item.isInternalTransfer()) "Προέλευση / προορισμός" else "Λογαριασμός",
                        if (item.isInternalTransfer()) activityTransferRouteLabel(item, accountOptions) else item.accountLabel,
                    )
                }
                // Web's as-of balance is a read-only daily statement and must
                // obey the same account privacy preference as transaction amounts.
                historicalBalance?.takeIf { !item.pendingSync }?.let { balance ->
                    item(key = "historical-account-balance") {
                        ActivityReadField(
                            "Υπόλοιπο λογαριασμού την ημέρα",
                            amountVisibilityText(
                                NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(balance),
                                amountsVisible,
                            ),
                        )
                    }
                }
                item { ActivityReadField("Κατηγορία", activityDetailCategoryLabel(item)) }
                item { ActivityReadField("Υποκατηγορία", item.subcategory ?: "Χωρίς υποκατηγορία") }
                val counterpartyKind = when (item.canonicalKind) {
                    "expense", "card_purchase", "split", "refund" -> "merchant"
                    "income", "lending", "repayment", "lending_writeoff" -> "beneficiary"
                    else -> null
                }
                if (!item.isLegacyTransaction && item.canonicalSourceOrdinal >= 0 &&
                    counterpartyKind != null
                ) {
                    item(key = "counterparty-identity") {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            val matches = counterpartyRows.filter { it.id == item.counterpartyId }
                            val selected = matches.singleOrNull()
                            ActivityReadField(
                                if (counterpartyKind == "merchant") "Έμπορος" else "Δικαιούχος",
                                selected?.displayName ?: if (item.counterpartyId.isNullOrBlank())
                                    "Δεν έχει επιλεγεί" else "Ιστορική ταυτότητα",
                            )
                            if (onChooseCounterparty != null) {
                                TextButton(
                                    onClick = { counterpartyMenuOpen = true },
                                    enabled = !mutationBlocked,
                                    modifier = Modifier.testTag("activity_choose_counterparty"),
                                ) { Text("Αλλαγή ταυτότητας") }
                                DropdownMenu(
                                    expanded = counterpartyMenuOpen,
                                    onDismissRequest = { counterpartyMenuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Καμία ταυτότητα") },
                                        onClick = {
                                            counterpartyMenuOpen = false
                                            onChooseCounterparty(null)
                                        },
                                    )
                                    counterpartyRows.filter { it.kind == counterpartyKind }
                                        .filter { row ->
                                            counterpartyRows.count { it.id == row.id } == 1
                                        }.forEach { candidate ->
                                            DropdownMenuItem(
                                                text = { Text(candidate.displayName) },
                                                onClick = {
                                                    counterpartyMenuOpen = false
                                                    onChooseCounterparty(candidate.id)
                                                },
                                            )
                                        }
                                }
                            }
                        }
                    }
                }
                if (item.isLegacyTransaction || item.canonicalSourceOrdinal >= 0) {
                    // Match Web's first-line note title and separate multiline comment.
                    item { ActivityReadField("Περιγραφή", item.webLedgerDisplayTitle()) }
                    if (item.webNoteParts().comment.isNotEmpty()) {
                        item { ActivityReadField("Σχόλιο", item.webNoteParts().comment) }
                    }
                } else if (item.webNoteParts().comment.isNotEmpty()) {
                    item { ActivityReadField("Περιγραφή", item.webNoteParts().title) }
                    item { ActivityReadField("Σχόλιο", item.webNoteParts().comment) }
                } else {
                    item { ActivityReadField("Σημείωση", item.note.ifBlank { "Χωρίς σημείωση" }) }
                }
                item.legacyHistoryLabel()?.let { label ->
                    item { ActivityReadField("Προέλευση", label) }
                }
                item.cardLabel?.takeIf(String::isNotBlank)?.let { cardLabel ->
                    item { ActivityReadField("Συνδεδεμένη κάρτα", cardLabel) }
                }
                if (item.canonicalKind == "split" && item.splitParts.isNotEmpty()) {
                    item(key = "split-heading") {
                        Text(
                            "Ανάλυση σύνθετης κίνησης · ${item.splitParts.size} μέρη",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = MyFinHubSpacing.md),
                        )
                    }
                    items(count = item.splitParts.size, key = { "split-part-$it" }) { index ->
                        val part = item.splitParts[index]
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = MyFinHubSpacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                            ) {
                                Text(part.displayLabel, style = MaterialTheme.typography.bodyLarge)
                                // An unlabeled part already uses the category path as its
                                // primary title; don't duplicate it in the same row.
                                if (part.displayLabel != part.categoryPath) {
                                    Text(
                                        part.categoryPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                amountVisibilityText(
                                    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(part.amount),
                                    amountsVisible,
                                ),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
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
                        if (item.isInternalTransfer()) {
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
                    if (deleteItem.isLegacyTransaction) {
                        "Η «${deleteItem.webLedgerDisplayTitle()}» θα εξαιρεθεί από υπολογισμούς και λίστες μέσω tombstone. " +
                            "Η αρχική εισαγόμενη εγγραφή παραμένει ανέπαφη. Δεν ακυρώνει συναλλαγή στην τράπεζα."
                    } else {
                        "Η «${deleteItem.webLedgerDisplayTitle()}» θα αφαιρεθεί από τα οικονομικά δεδομένα του MyFinHub και θα επανυπολογιστούν τα σχετικά υπόλοιπα. Δεν ακυρώνει συναλλαγή στην τράπεζα."
                    },
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
    accountOptions: List<ActivityAccountOption> = emptyList(),
    incomeCategoryOptions: List<ActivityCategoryOption> = emptyList(),
    expenseCategoryOptions: List<ActivityCategoryOption> = emptyList(),
    mutationInFlight: Boolean,
    mutationBlocked: Boolean,
    onBack: () -> Unit,
    onSave: (String, String, String, String, ActivityLegacyEdit?) -> Unit,
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
    val amountsVisible = rememberAmountVisibilityPreference()

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
    val isLegacy = item.isLegacyTransaction
    var legacyType by rememberSaveable(item.id) { mutableStateOf(item.canonicalKind) }
    var legacyAmountText by rememberSaveable(item.id) {
        mutableStateOf((if (item.canonicalKind == "expense") -item.amount else item.amount).toString())
    }
    var legacyAccountId by rememberSaveable(item.id) {
        mutableStateOf(item.accountId ?: accountOptions.firstOrNull()?.id.orEmpty())
    }
    var legacyFromAccountId by rememberSaveable(item.id) {
        mutableStateOf(item.fromAccountId ?: item.accountId ?: accountOptions.firstOrNull()?.id.orEmpty())
    }
    var legacyToAccountId by rememberSaveable(item.id) {
        mutableStateOf(item.toAccountId ?: accountOptions.firstOrNull {
            it.id != (item.fromAccountId ?: item.accountId)
        }?.id.orEmpty())
    }
    var requestedLegacyType by rememberSaveable(item.id) { mutableStateOf("") }
    var requestedLegacyAmount by rememberSaveable(item.id) { mutableStateOf("") }
    var requestedLegacyAccountId by rememberSaveable(item.id) { mutableStateOf("") }
    var requestedLegacyFromAccountId by rememberSaveable(item.id) { mutableStateOf("") }
    var requestedLegacyToAccountId by rememberSaveable(item.id) { mutableStateOf("") }
    var discardDialogOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var observedMutationInFlight by rememberSaveable(item.id) { mutableStateOf(false) }
    var requestedDate by rememberSaveable(item.id) { mutableStateOf("") }
    var requestedNote by rememberSaveable(item.id) { mutableStateOf("") }
    var requestedCategory by rememberSaveable(item.id) { mutableStateOf("") }
    var requestedSubcategory by rememberSaveable(item.id) { mutableStateOf("") }

    val selectableCategories = if (isLegacy && legacyType != item.canonicalKind) {
        if (legacyType == "income") incomeCategoryOptions else expenseCategoryOptions
    } else categoryOptions
    val effectiveCategoryOptions = buildList {
        addAll(selectableCategories)
        item.category?.takeIf(String::isNotBlank)?.let { current ->
            if (none { it.name == current }) add(ActivityCategoryOption(current, listOfNotNull(item.subcategory)))
        }
    }
    val subcategoryOptions = effectiveCategoryOptions.firstOrNull { it.name == category }?.subcategories.orEmpty()
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
    val dateError = date.isNotBlank() && parsedDate == null
    val parsedLegacyAmount = legacyAmountText.trim().replace(',', '.').toDoubleOrNull()
    val legacyCents = parsedLegacyAmount?.let { runCatching { moneyToCents(it) }.getOrNull() }
    val legacyAmountValid = legacyCents != null &&
        (if (legacyType == "adjustment") legacyCents != 0L else legacyCents > 0L)
    val legacyAccountValid = if (legacyType == "transfer") {
        legacyFromAccountId != legacyToAccountId &&
            accountOptions.any { it.id == legacyFromAccountId } &&
            accountOptions.any { it.id == legacyToAccountId }
    } else accountOptions.any { it.id == legacyAccountId }
    val originalLegacyAmount = if (item.canonicalKind == "expense") -item.amount else item.amount
    val legacyDirty = isLegacy && (
        legacyType != item.canonicalKind ||
            parsedLegacyAmount != originalLegacyAmount ||
            (if (legacyType == "transfer") {
                legacyFromAccountId != item.fromAccountId || legacyToAccountId != item.toAccountId
            } else legacyAccountId != item.accountId)
        )
    val dirty = date != item.rawDate.take(10) ||
        note != item.note ||
        category != item.category.orEmpty() ||
        subcategory != item.subcategory.orEmpty() || legacyDirty
    val valid = parsedDate != null && note.isNotBlank() &&
        (!isLegacy || (legacyAmountValid && legacyAccountValid))

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
        requestedLegacyType,
        requestedLegacyAmount,
        requestedLegacyAccountId,
        requestedLegacyFromAccountId,
        requestedLegacyToAccountId,
    ) {
        val legacyPersisted = !isLegacy || (
            item.canonicalKind == requestedLegacyType &&
                runCatching { moneyToCents(
                    if (item.canonicalKind == "expense") -item.amount else item.amount,
                ) }.getOrNull() == requestedLegacyAmount.toDoubleOrNull()
                    ?.let { runCatching { moneyToCents(it) }.getOrNull() } &&
                (if (requestedLegacyType == "transfer") {
                    item.fromAccountId == requestedLegacyFromAccountId &&
                        item.toAccountId == requestedLegacyToAccountId
                } else item.accountId == requestedLegacyAccountId)
        )
        val persisted = saveRequested &&
            item.rawDate.take(10) == requestedDate &&
            item.note == requestedNote &&
            item.category.orEmpty() == requestedCategory &&
            item.subcategory.orEmpty() == requestedSubcategory && legacyPersisted
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
                subtitle = item.webLedgerDisplayTitle(),
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
                        requestedLegacyType = legacyType
                        requestedLegacyAmount = legacyAmountText.trim().replace(',', '.')
                        requestedLegacyAccountId = legacyAccountId
                        requestedLegacyFromAccountId = legacyFromAccountId
                        requestedLegacyToAccountId = legacyToAccountId
                        saveRequested = true
                        onSave(
                            date, note.trim(), category, subcategory,
                            if (isLegacy) ActivityLegacyEdit(
                                type = legacyType,
                                amount = parsedLegacyAmount!!,
                                accountId = legacyAccountId.takeIf { legacyType != "transfer" },
                                fromAccountId = legacyFromAccountId.takeIf { legacyType == "transfer" },
                                toAccountId = legacyToAccountId.takeIf { legacyType == "transfer" },
                            ) else null,
                        )
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
            if (isLegacy) {
                Text(
                    "Ιστορική κίνηση · οι αλλαγές αποθηκεύονται ως override. " +
                        "Το αρχικό εισαγόμενο αρχείο παραμένει ανέπαφο.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                S3ChoiceField(
                    label = "Τύπος ιστορικής κίνησης",
                    selectedId = legacyType,
                    choices = listOf(
                        "expense" to "Έξοδο",
                        "income" to "Έσοδο",
                        "transfer" to "Μεταφορά",
                        "adjustment" to "Διόρθωση υπολοίπου",
                    ),
                    enabled = !mutationInFlight && !saveRequested && !item.pendingSync,
                    onSelected = { next ->
                        legacyType = next
                        if (next == "transfer") {
                            if (legacyFromAccountId.isBlank()) legacyFromAccountId = legacyAccountId
                            if (legacyToAccountId == legacyFromAccountId) {
                                legacyToAccountId = accountOptions.firstOrNull {
                                    it.id != legacyFromAccountId
                                }?.id.orEmpty()
                            }
                        } else if (legacyAccountId.isBlank()) {
                            legacyAccountId = legacyFromAccountId
                        }
                        val allowed = if (next == "income") incomeCategoryOptions else expenseCategoryOptions
                        if (category.isNotBlank() && allowed.none { it.name == category }) {
                            category = ""
                            subcategory = ""
                        }
                    },
                )
                MyFinHubOutlinedField(
                    value = legacyAmountText,
                    onValueChange = { legacyAmountText = it },
                    label = "Ποσό ιστορικής κίνησης",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    visualTransformation = if (amountsVisible || LocalInspectionMode.current)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    errorMessage = if (legacyAmountValid) null else if (legacyType == "adjustment")
                        "Η διόρθωση δεν μπορεί να έχει μηδενικό ποσό."
                    else "Συμπλήρωσε έγκυρο ποσό μεγαλύτερο από μηδέν.",
                    enabled = !mutationInFlight && !saveRequested && !item.pendingSync,
                )
                if (legacyType == "transfer") {
                    S3ChoiceField(
                        label = "Από λογαριασμό",
                        selectedId = legacyFromAccountId,
                        choices = accountOptions.map { it.id to it.label },
                        enabled = !mutationInFlight && !saveRequested && !item.pendingSync,
                        onSelected = { legacyFromAccountId = it },
                    )
                    S3ChoiceField(
                        label = "Προς λογαριασμό",
                        selectedId = legacyToAccountId,
                        choices = accountOptions.map { it.id to it.label },
                        enabled = !mutationInFlight && !saveRequested && !item.pendingSync,
                        onSelected = { legacyToAccountId = it },
                    )
                    if (!legacyAccountValid) Text(
                        "Η μεταφορά χρειάζεται δύο διαφορετικούς διαθέσιμους λογαριασμούς.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    S3ChoiceField(
                        label = "Λογαριασμός ιστορικής κίνησης",
                        selectedId = legacyAccountId,
                        choices = accountOptions.map { it.id to it.label },
                        enabled = !mutationInFlight && !saveRequested && !item.pendingSync,
                        onSelected = { legacyAccountId = it },
                    )
                    if (!legacyAccountValid) Text(
                        "Επίλεξε διαθέσιμο λογαριασμό.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                ActivityReadField(
                    "Ποσό",
                    // Existing screenshot references render a visible demo amount;
                    // installed device privacy remains controlled by the preference.
                    amountVisibilityText(
                        formatActivityFlowEuro(item),
                        amountsVisible || LocalInspectionMode.current,
                    ),
                )
                ActivityReadField("Τύπος", item.typeLabel)
                ActivityReadField(
                    if (item.isInternalTransfer()) "Προέλευση / προορισμός" else "Λογαριασμός",
                    item.accountLabel,
                )
                Text(
                    "Το ποσό, ο τύπος και ο λογαριασμός δεν αλλάζουν από αυτή την επεξεργασία.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    // Use signed canonical flow, not the original import type. A confirmed
    // expense-as-refund must render positive, while neutral reviews stay neutral.
    financeIncomeContribution != null && financeExpenseContribution != null -> when {
        canonicalKind == "transfer" -> FinanceTone.Neutral
        financeIncomeContribution > 0.0 || financeExpenseContribution < 0.0 -> FinanceTone.Income
        financeExpenseContribution > 0.0 -> FinanceTone.Expense
        else -> FinanceTone.Neutral
    }
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
