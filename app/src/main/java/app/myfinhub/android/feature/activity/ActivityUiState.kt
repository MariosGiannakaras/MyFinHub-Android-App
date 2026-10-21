package app.myfinhub.android.feature.activity

import java.text.Collator
import java.util.Locale
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A canonical split event's original, read-only constituent; never infer parts from totals. */
data class ActivitySplitPart(
    val label: String,
    val category: String,
    val subcategory: String?,
    val amount: Double,
) {
    val categoryPath: String get() = if (subcategory.isNullOrEmpty()) category else "$category › $subcategory"
    val displayLabel: String get() = label.trim().ifBlank { categoryPath }
}

data class ActivityItem(
    val id: String,
    val dateLabel: String,
    val kind: ActivityKind,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val accountLabel: String,
    val category: String?,
    val pendingSync: Boolean = false,
    /** Canonical YYYY-MM-DD date used for deterministic grouping; never infer it back from dateLabel. */
    val rawDate: String = "",
    val subcategory: String? = null,
    val accountId: String? = null,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
    /** Signed expense amounts by exact canonical category; null only for legacy preview fixtures. */
    val categoryContributions: Map<String, Double>? = null,
    /** Raw canonical note, separate from any presentation fallback text. */
    val note: String = subtitle,
    val cardId: String? = null,
    val cardLabel: String? = null,
    /** Exact shared-schema kind. Keep this separate from the broad amount-tone grouping. */
    val canonicalKind: String = kind.defaultCanonicalKind(),
    /** Human-readable exact type used by filters and read-first detail. */
    val typeLabel: String = activityTypeLabel(canonicalKind),
    /** Operation-aware durable state, for example pending edit versus pending deletion. */
    val pendingLabel: String? = null,
    /** Web flowImpact signed values; null only in isolated UI preview fixtures. */
    val financeIncomeContribution: Double? = null,
    val financeExpenseContribution: Double? = null,
    /** Every real asset account named in event ledger legs; excludes synthetic credit-card debt. */
    val legAccountIds: Set<String> = emptySet(),
    /** Web TransactionsPage distinguishes historic imported rows from canonical events. */
    val isLegacyTransaction: Boolean = false,
    /** Ephemeral ordinal in this canonical source list (never a persisted finance ID). */
    val canonicalSourceOrdinal: Int = -1,
    /** Whether this imported transaction is represented by a canonical override. */
    val isLegacyOverridden: Boolean = false,
    /** Canonical event split parts in server order, not analytics-only aggregates. */
    val splitParts: List<ActivitySplitPart> = emptyList(),
)

/** Web noteParts: first nonblank line is the description; following lines are comments. */
data class ActivityNoteParts(val title: String, val comment: String)

internal fun ActivityItem.webNoteParts(): ActivityNoteParts {
    val lines = note.replace("\r\n", "\n").lines()
        .map { it.trim() }.filter(String::isNotBlank)
    return ActivityNoteParts(
        title = lines.firstOrNull() ?: "Χωρίς περιγραφή",
        comment = lines.drop(1).joinToString("\n"),
    )
}

/** Distinguish actual imported rows and their saved review overrides from events. */
internal fun ActivityItem.legacyHistoryLabel(): String? = when {
    !isLegacyTransaction -> null
    isLegacyOverridden -> "Ιστορικό · override"
    else -> "Ιστορικό"
}

data class ActivityCategoryOption(
    val name: String,
    val subcategories: List<String> = emptyList(),
)

data class ActivityAccountOption(
    val id: String,
    val label: String,
)

data class ActivityTypeOption(
    val id: String,
    val label: String,
)

/** Web localeCompare('el') ordering for taxonomy and type selectors. */
internal fun greekActivityLabelOrder(): Comparator<String> {
    val collator = Collator.getInstance(Locale.forLanguageTag("el-GR"))
    return Comparator { left, right -> collator.compare(left, right) }
}

/**
 * Web TransactionsPage search index is exactly:
 * note + category + subcategory + raw kind + split part label/category/subcategory.
 * Do not index account names, visible dates, derived titles or display labels:
 * those are separate presentation fields and cause unexpected false matches.
 * Screenshots with hand-authored preview records have no canonical ordinal;
 * preserve their wider illustrative search behavior without leaking amounts.
 */
internal fun cleanLegacyActivityNote(note: String): String {
    // Web format.ts cleanNote; presentation only, never mutate canonical data.
    val marker = "Comment:\n"
    if (marker in note) {
        return note.substringAfter(marker).replace("\nReply:\n", "\n").trim()
    }
    return note.replace(
        Regex("""\[Threaded comment\][\s\S]*?Comment:\n""", RegexOption.IGNORE_CASE),
        "",
    ).trim()
}

private val greekLedgerSearchLocale = Locale.forLanguageTag("el-GR")

internal fun ActivityItem.matchesWebLedgerSearch(needle: String): Boolean {
    if (needle.isBlank()) return true
    // TransactionsPage searches a single space-separated string, allowing
    // phrases spanning fields. Locale case-folding must preserve accents.
    val webCategory = when {
        !isLegacyTransaction && canonicalKind == "split" -> "Διαχωρισμός"
        !category.isNullOrEmpty() -> category
        isLegacyTransaction -> "Άλλο"
        else -> canonicalKind
    }
    val parts = splitParts.joinToString(" ") { part ->
        part.label + " " + part.category + " " + part.subcategory.orEmpty()
    }
    val webIndex = listOf(note, webCategory, subcategory.orEmpty(), canonicalKind, parts).joinToString(" ")
    val sourceMatch = webIndex.lowercase(greekLedgerSearchLocale)
        .contains(needle.lowercase(greekLedgerSearchLocale))
    // Synthetic screenshot fixtures intentionally retain their preview search.
    return sourceMatch || (canonicalSourceOrdinal < 0 && (
        title.contains(needle, ignoreCase = true) ||
            subtitle.contains(needle, ignoreCase = true) ||
            typeLabel.contains(needle, ignoreCase = true) ||
            kind.label.contains(needle, ignoreCase = true) ||
            accountLabel.contains(needle, ignoreCase = true) ||
            dateLabel.contains(needle, ignoreCase = true) ||
            rawDate.contains(needle, ignoreCase = true)
    ))
}
enum class ActivityKind(val label: String) {
    EXPENSE("Έξοδα"),
    INCOME("Έσοδα"),
    TRANSFER("Μεταφορές"),
    CARD_PAYMENT("Πληρωμές κάρτας"),
}

enum class ActivityFilter(val label: String) {
    ALL("Όλοι οι τύποι"),
    EXPENSE("Έξοδα"),
    INCOME("Έσοδα"),
    TRANSFER("Μεταφορές"),
}

enum class ActivityFilterField {
    TYPE,
    ACCOUNT,
    CATEGORY,
    DATE,
}

data class ActivitySection(val date: String, val items: List<ActivityItem>)

data class ActivityUiState(
    val query: String = "",
    val filter: ActivityFilter = ActivityFilter.ALL,
    /** Read-only ledger sort; Web offers newest/oldest ordering independently of filters. */
    val sortOldestFirst: Boolean = false,
    val accountFilterId: String? = null,
    val selectedId: String? = null,
    val items: List<ActivityItem> = syntheticActivityItems(),
    val expenseCategories: List<ActivityCategoryOption> = emptyList(),
    val incomeCategories: List<ActivityCategoryOption> = emptyList(),
    val accountOptions: List<ActivityAccountOption> = emptyList(),
    /** Exact category scope chosen from the main ledger filter sheet. */
    val ledgerCategoryFilter: String? = null,
    /** Inclusive canonical YYYY-MM-DD ledger range. */
    val ledgerDateFrom: String? = null,
    val ledgerDateTo: String? = null,
    /** Exact canonical type selected from the S3 filter sheet. */
    val typeFilterId: String? = null,
    /** Isolated analytics drill-down scope. Never reuse this for the main ledger filters. */
    val categoryFilter: String? = null,
    /** Exact canonical category identities represented by an analytics row such as "Λοιπά". */
    val categoryFilterIds: Set<String>? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
) {
    val isAnalyticsScope: Boolean = categoryFilter != null || !categoryFilterIds.isNullOrEmpty()

    /** Fail closed when a historic import collides with any other transaction ID. */
    val ambiguousIds: Set<String> = items.groupingBy(ActivityItem::id)
        .eachCount().filterValues { it > 1 }.keys

    /** The same exact target check is required at the navigation and durable mutation boundaries. */
    fun isUniqueMutationTarget(id: String): Boolean = id.isNotBlank() &&
        id !in ambiguousIds && items.any { it.id == id }

    fun uniqueItem(id: String?): ActivityItem? = id?.takeIf(::isUniqueMutationTarget)
        ?.let { exact -> items.firstOrNull { it.id == exact } }

    /**
     * Web TransactionsPage sourceRows deliberately excludes canonical card_purchase
     * and card_payment events. Both remain in the canonical document and the
     * dedicated card activity, and category Analytics must still be able to
     * drill into purchase contributions. Hand-authored preview rows with no
     * source ordinal remain visible only as illustrative screen fixtures.
     */
    val webLedgerItems: List<ActivityItem> = items.filter { item ->
        item.isLegacyTransaction || item.canonicalSourceOrdinal < 0 ||
            item.canonicalKind !in setOf("card_purchase", "card_payment")
    }
    val scopeSourceItems: List<ActivityItem> = if (isAnalyticsScope) items else webLedgerItems

    private val greekLabelOrder = greekActivityLabelOrder()

    // Web TransactionsPage: available taxonomy paths plus historic *display*
    // labels. The main ledger is different from category analytics contribution
    // drill-downs: a split belongs under "Επιμέρους κατηγορίες" in the ledger.
    val availableCategories: List<String> = buildList {
        (expenseCategories + incomeCategories).forEach { option ->
            add(option.name)
            option.subcategories.forEach { subcategory ->
                add("${option.name} › $subcategory")
            }
        }
        webLedgerItems.forEach { item -> add(item.webLedgerCategoryLabel()) }
    }.distinct().sortedWith(greekLabelOrder)

    val typeOptions: List<ActivityTypeOption> = buildList {
        webLedgerItems.forEach { item ->
            add(ActivityTypeOption(item.canonicalKind, item.typeLabel))
        }
        typeFilterId?.takeIf(String::isNotBlank)?.let { selected ->
            if (selected !in setOf("card_purchase", "card_payment") &&
                none { it.id == selected }
            ) add(ActivityTypeOption(selected, activityTypeLabel(selected)))
        }
    }.filter { it.id.isNotBlank() }
        .distinctBy(ActivityTypeOption::id)
        .sortedWith { left, right -> greekLabelOrder.compare(left.label, right.label) }

    val activeFilterCount: Int = if (isAnalyticsScope) 0 else listOf(
        typeFilterId != null || filter != ActivityFilter.ALL,
        accountFilterId != null,
        ledgerCategoryFilter != null,
        ledgerDateFrom != null || ledgerDateTo != null,
    ).count { it }

    val hasActiveListScope: Boolean = !isAnalyticsScope && (query.isNotBlank() || activeFilterCount > 0)

    // Activity can contain hundreds of canonical events. Compute immutable projections once per
    // state instance instead of re-filtering every time Compose reads them.
    val visibleItems: List<ActivityItem> = run {
        val needle = query.trim()
        val selectedAnalyticsCategories = categoryFilterIds.orEmpty()
        val effectiveDateFrom = if (isAnalyticsScope) dateFrom else ledgerDateFrom
        val effectiveDateTo = if (isAnalyticsScope) dateTo else ledgerDateTo
        val matching = scopeSourceItems.filter { item ->
            val matchesFilter = typeFilterId?.let { item.canonicalKind == it } ?: when (filter) {
                ActivityFilter.ALL -> true
                ActivityFilter.EXPENSE -> item.kind == ActivityKind.EXPENSE || item.kind == ActivityKind.CARD_PAYMENT
                ActivityFilter.INCOME -> item.kind == ActivityKind.INCOME
                ActivityFilter.TRANSFER -> item.kind == ActivityKind.TRANSFER
            }
            // Web TransactionsPage: an imported row filters on its persisted
            // account endpoints; a canonical event filters EXCLUSIVELY on
            // non-credit ledger legs. Endpoint hints can be stale after review,
            // so they must not invent account attribution for real events.
            // Unbacked screenshot fixtures retain their illustrative routes.
            val matchesAccount = when {
                accountFilterId == null -> true
                item.isLegacyTransaction || item.canonicalSourceOrdinal < 0 ->
                    item.accountId == accountFilterId ||
                        item.fromAccountId == accountFilterId ||
                        item.toAccountId == accountFilterId ||
                        item.legAccountIds.contains(accountFilterId)
                else -> item.legAccountIds.contains(accountFilterId)
            }
            val matchesQuery = item.matchesWebLedgerSearch(needle)
            val matchesCategory = when {
                !isAnalyticsScope -> ledgerCategoryFilter == null ||
                    item.webLedgerCategoryLabel() == ledgerCategoryFilter
                // Exact analytics category IDs always use *signed* financial
                // contributions, never the presentation path from the ledger.
                selectedAnalyticsCategories.isEmpty() -> false
                item.categoryContributions != null ->
                    kotlin.math.abs(selectedAnalyticsCategories.sumOf { category ->
                        item.categoryContributions[category] ?: 0.0
                    }) > 0.005
                // Only legacy synthetic preview rows without canonical contribution
                // metadata may use their original category for this read-only view.
                else -> (item.category?.takeIf(String::isNotBlank) ?: "Άλλο") in
                    selectedAnalyticsCategories
            }
            val matchesDate = (effectiveDateFrom == null && effectiveDateTo == null) ||
                (item.rawDate.length >= 10 &&
                    (effectiveDateFrom == null || item.rawDate.take(10) >= effectiveDateFrom) &&
                    (effectiveDateTo == null || item.rawDate.take(10) <= effectiveDateTo))
            matchesFilter && matchesAccount && matchesQuery && matchesCategory && matchesDate
        }
        // Current Web sorting uses (date, id), ascending or descending. Keep the
        // canonical newest-first ledger unchanged by default and never reorder
        // category analytics drill-downs merely because the ledger was toggled.
        if (sortOldestFirst && !isAnalyticsScope) {
            matching.sortedWith(compareBy<ActivityItem>(
                { it.rawDate.take(10).ifBlank { "9999-12-31" } },
                ActivityItem::id,
            ))
        } else {
            matching
        }
    }

    /**
     * Read-only summary for the current search/filter projection. Transfers are intentionally
     * excluded from income/expense/net so the UI never double-counts internal money movement.
     */
    // Follow Web flowImpactEvent/flowImpactLegacy. Refunds reduce expenses (and can
    // make a narrowly filtered expense total negative); card repayments/transfers
    // are not spending. Real canonical rows carry explicit signed contributions,
    // while the kind-aware fallback also keeps isolated UI fixtures faithful.
    val visibleIncome: Double = visibleItems.sumOf { item ->
        item.financeIncomeContribution ?: if (item.canonicalKind == "income")
            item.amount.coerceAtLeast(0.0) else 0.0
    }

    val visibleExpense: Double = visibleItems.sumOf { item ->
        item.financeExpenseContribution ?: when (item.canonicalKind) {
            "expense", "card_purchase", "split" -> kotlin.math.abs(item.amount)
            "refund" -> -kotlin.math.abs(item.amount)
            else -> 0.0
        }
    }

    val visibleNet: Double = visibleIncome - visibleExpense
    val visiblePendingCount: Int = visibleItems.count(ActivityItem::pendingSync)

    /**
     * Keep the original canonical newest-first row order and *contiguous* day
     * boundaries (even for malformed out-of-order history). Build each day's
     * mutable list once, rather than copying an ever-growing immutable list per
     * transaction, which was quadratic for long real-bank activity days.
     */
    val visibleSections: List<ActivitySection> = buildList {
        var currentDate: String? = null
        var currentItems = mutableListOf<ActivityItem>()
        for (item in visibleItems) {
            val key = item.rawDate.take(10).ifBlank { item.dateLabel }
            if (currentDate != null && currentDate != key) {
                add(ActivitySection(currentDate, currentItems.toList()))
                currentItems = mutableListOf()
            }
            currentDate = key
            currentItems.add(item)
        }
        if (currentDate != null) add(ActivitySection(currentDate, currentItems.toList()))
    }

    val selectedItem: ActivityItem? = uniqueItem(selectedId)

    fun categoryOptionsFor(item: ActivityItem): List<ActivityCategoryOption> = when (item.canonicalKind) {
        "income" -> incomeCategories
        "expense", "refund", "lending", "repayment", "card_purchase" -> expenseCategories
        else -> emptyList()
    }

    /** Isolated read view: opening analysis never replaces the user's global ledger filters. */
    fun forCategory(category: String, start: String, end: String): ActivityUiState =
        forCategories(listOf(category), category, start, end)

    fun forCategories(categories: List<String>, label: String, start: String, end: String): ActivityUiState {
        val exact = categories.filter(String::isNotBlank).toSet()
        return copy(
            query = "",
            filter = ActivityFilter.ALL,
            sortOldestFirst = false,
            accountFilterId = null,
            selectedId = null,
            ledgerCategoryFilter = null,
            ledgerDateFrom = null,
            ledgerDateTo = null,
            typeFilterId = null,
            categoryFilter = label,
            categoryFilterIds = exact,
            dateFrom = start,
            dateTo = end,
            items = items.map { item ->
                val contributions = item.categoryContributions
                if (contributions.isNullOrEmpty()) {
                    item
                } else {
                    val contribution = exact.sumOf { category -> contributions[category] ?: 0.0 }
                    if (kotlin.math.abs(contribution) <= 0.005) item else item.copy(
                        amount = -contribution,
                        financeIncomeContribution = 0.0,
                        financeExpenseContribution = contribution,
                    )
                }
            },
        )
    }
}

sealed interface ActivityAction {
    data class QueryChanged(val value: String) : ActivityAction
    data object ToggleSortDirection : ActivityAction
    data class FilterChanged(val value: ActivityFilter) : ActivityAction
    data class AccountFilterChanged(val accountId: String?) : ActivityAction
    data class ApplyFilters(
        val type: ActivityFilter,
        val accountId: String?,
        val category: String?,
        val dateFrom: String?,
        val dateTo: String?,
        val typeId: String? = null,
    ) : ActivityAction
    data object ClearFilters : ActivityAction
    /** Web resetFilters: clears query and every ledger filter in one state transition. */
    data object ClearSearchAndFilters : ActivityAction
    data class RemoveFilter(val field: ActivityFilterField) : ActivityAction
    data class Select(val id: String?) : ActivityAction
    data class SaveEdit(
        val id: String,
        val note: String,
        val category: String,
        val date: String? = null,
        val subcategory: String? = null,
    ) : ActivityAction
    data class Delete(val id: String) : ActivityAction
}

fun reduceActivity(state: ActivityUiState, action: ActivityAction): ActivityUiState = when (action) {
    is ActivityAction.QueryChanged -> state.copy(query = action.value)
    ActivityAction.ToggleSortDirection -> if (state.isAnalyticsScope) state
        else state.copy(sortOldestFirst = !state.sortOldestFirst)
    is ActivityAction.FilterChanged -> state.copy(filter = action.value, typeFilterId = null)
    is ActivityAction.AccountFilterChanged -> state.copy(accountFilterId = action.accountId)
    is ActivityAction.ApplyFilters -> state.copy(
        filter = if (action.typeId.isNullOrBlank()) action.type else ActivityFilter.ALL,
        typeFilterId = action.typeId?.trim()?.takeIf(String::isNotBlank),
        accountFilterId = action.accountId,
        ledgerCategoryFilter = action.category?.trim()?.takeIf(String::isNotBlank),
        ledgerDateFrom = action.dateFrom?.trim()?.takeIf(String::isNotBlank),
        ledgerDateTo = action.dateTo?.trim()?.takeIf(String::isNotBlank),
    )
    ActivityAction.ClearFilters -> state.copy(
        filter = ActivityFilter.ALL,
        typeFilterId = null,
        accountFilterId = null,
        ledgerCategoryFilter = null,
        ledgerDateFrom = null,
        ledgerDateTo = null,
    )
    ActivityAction.ClearSearchAndFilters -> state.copy(
        query = "",
        filter = ActivityFilter.ALL,
        typeFilterId = null,
        accountFilterId = null,
        ledgerCategoryFilter = null,
        ledgerDateFrom = null,
        ledgerDateTo = null,
    )
    is ActivityAction.RemoveFilter -> when (action.field) {
        ActivityFilterField.TYPE -> state.copy(filter = ActivityFilter.ALL, typeFilterId = null)
        ActivityFilterField.ACCOUNT -> state.copy(accountFilterId = null)
        ActivityFilterField.CATEGORY -> state.copy(ledgerCategoryFilter = null)
        ActivityFilterField.DATE -> state.copy(ledgerDateFrom = null, ledgerDateTo = null)
    }
    is ActivityAction.Select -> state.copy(
        selectedId = action.id?.takeIf(state::isUniqueMutationTarget),
    )
    is ActivityAction.SaveEdit -> if (!state.isUniqueMutationTarget(action.id)) state else state.copy(
        items = state.items.map { item ->
            if (item.id == action.id) {
                item.copy(
                    rawDate = action.date ?: item.rawDate,
                    dateLabel = action.date ?: item.dateLabel,
                    subtitle = action.note,
                    note = action.note,
                    category = action.category.takeIf(String::isNotBlank),
                    subcategory = if (action.subcategory != null) {
                        action.subcategory.takeIf(String::isNotBlank)
                    } else {
                        item.subcategory
                    },
                )
            } else {
                item
            }
        },
    )
    is ActivityAction.Delete -> if (!state.isUniqueMutationTarget(action.id)) state else state.copy(
        selectedId = state.selectedId.takeUnless { it == action.id },
        items = state.items.filterNot { it.id == action.id },
    )
}

fun ActivityItem.supportsCategoryEdit(): Boolean = canonicalKind in setOf(
    "income",
    "expense",
    "refund",
    "lending",
    "repayment",
    "card_purchase",
)

/** Exact Web TransactionsPage categoryLabel/categoryPath for main-ledger filters. */
/** A transfer route exists only for actual transfers, not withdrawals or corrections. */
internal fun ActivityItem.isInternalTransfer(): Boolean = canonicalKind == "transfer"

internal fun ActivityItem.webLedgerCategoryLabel(): String {
    if (canonicalKind == "split") return "Επιμέρους κατηγορίες"
    val selectedCategory = category?.takeIf(String::isNotBlank)
        ?: if (isLegacyTransaction) "Άλλο" else canonicalKind
    if (selectedCategory == canonicalKind) return when (canonicalKind) {
        "refund" -> "Επιστροφή χρημάτων"
        "lending" -> "Δανεικά προς άλλον"
        "repayment" -> "Επιστροφή δανεικών"
        "card_purchase" -> "Αγορά με πιστωτική"
        "card_payment" -> "Εξόφληση πιστωτικής"
        "reconciliation" -> "Διόρθωση υπολοίπου"
        "split" -> "Σύνθετη κίνηση"
        else -> activityTypeLabel(canonicalKind)
    }
    return if (subcategory.isNullOrEmpty()) selectedCategory
    else "$selectedCategory › $subcategory"
}

/** Web ui.ts eventKindLabel including its raw-kind fallback. */
internal fun activityTypeLabel(canonicalKind: String): String = when (canonicalKind) {
    "expense" -> "Έξοδο"
    "income" -> "Έσοδο"
    "transfer" -> "Μεταφορά"
    "saving_cash_offset" -> "Αποταμίευση"
    "withdrawal" -> "Ανάληψη"
    "refund" -> "Επιστροφή χρημάτων"
    "lending" -> "Δανεικά προς άλλον"
    "repayment" -> "Επιστροφή δανεικών"
    "card_purchase" -> "Αγορά με πιστωτική"
    "card_payment" -> "Εξόφληση πιστωτικής"
    "reconciliation" -> "Διόρθωση υπολοίπου"
    "split" -> "Σύνθετη κίνηση"
    else -> canonicalKind
}

private fun ActivityKind.defaultCanonicalKind(): String = when (this) {
    ActivityKind.EXPENSE -> "expense"
    ActivityKind.INCOME -> "income"
    ActivityKind.TRANSFER -> "transfer"
    ActivityKind.CARD_PAYMENT -> "card_payment"
}

class ActivityViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = mutableState.asStateFlow()

    fun onAction(action: ActivityAction) {
        mutableState.update { reduceActivity(it, action) }
    }
}

fun syntheticActivityItems(): List<ActivityItem> = listOf(
    ActivityItem("evt-1", "Σήμερα, 09:42", ActivityKind.EXPENSE, "Σούπερ μάρκετ", "Εβδομαδιαία ψώνια", -63.48, "Κύριος λογαριασμός", "Τρόφιμα", rawDate = "2026-08-22"),
    ActivityItem("evt-2", "Χθες, 18:10", ActivityKind.TRANSFER, "Μεταφορά στην αποταμίευση", "Κύριος → Αποταμίευση", 250.00, "Εσωτερική μεταφορά", "Αποταμίευση", rawDate = "2026-08-21"),
    ActivityItem("evt-3", "21 Αυγ, 10:00", ActivityKind.INCOME, "Μισθός", "Μηνιαία πίστωση", 1840.00, "Κύριος λογαριασμός", "Μισθός", rawDate = "2026-08-21"),
    ActivityItem("evt-4", "20 Αυγ, 14:25", ActivityKind.CARD_PAYMENT, "Πληρωμή πιστωτικής", "Πιστωτική • 4242", -312.20, "Κύριος λογαριασμός", "Κάρτες", rawDate = "2026-08-20"),
    ActivityItem("evt-5", "19 Αυγ, 20:15", ActivityKind.EXPENSE, "Δείπνο", "Μοίρασμα λογαριασμού", -38.50, "Μετρητά", "Έξοδος", rawDate = "2026-08-19"),
)
