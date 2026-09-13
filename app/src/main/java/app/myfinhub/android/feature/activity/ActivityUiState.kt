package app.myfinhub.android.feature.activity

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
)

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
    val dateFrom: String? = null,
    val dateTo: String? = null,
) {
    val isAnalyticsScope: Boolean = categoryFilter != null

    val availableCategories: List<String> = buildList {
        expenseCategories.forEach { add(it.name) }
        incomeCategories.forEach { add(it.name) }
        items.forEach { item ->
            item.category?.takeIf(String::isNotBlank)?.let(::add)
            item.categoryContributions?.keys?.filter(String::isNotBlank)?.forEach(::add)
        }
    }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

    val typeOptions: List<ActivityTypeOption> = buildList {
        items.forEach { item ->
            add(ActivityTypeOption(item.canonicalKind, item.typeLabel))
        }
        typeFilterId?.takeIf(String::isNotBlank)?.let { selected ->
            if (none { it.id == selected }) add(ActivityTypeOption(selected, activityTypeLabel(selected)))
        }
    }.filter { it.id.isNotBlank() }
        .distinctBy(ActivityTypeOption::id)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, ActivityTypeOption::label))

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
        val normalizedNumericNeedle = needle.replace(',', '.')
        val exactCategory = if (isAnalyticsScope) categoryFilter else ledgerCategoryFilter
        val effectiveDateFrom = if (isAnalyticsScope) dateFrom else ledgerDateFrom
        val effectiveDateTo = if (isAnalyticsScope) dateTo else ledgerDateTo
        items.filter { item ->
            val matchesFilter = typeFilterId?.let { item.canonicalKind == it } ?: when (filter) {
                ActivityFilter.ALL -> true
                ActivityFilter.EXPENSE -> item.kind == ActivityKind.EXPENSE || item.kind == ActivityKind.CARD_PAYMENT
                ActivityFilter.INCOME -> item.kind == ActivityKind.INCOME
                ActivityFilter.TRANSFER -> item.kind == ActivityKind.TRANSFER
            }
            val matchesAccount = accountFilterId == null ||
                item.accountId == accountFilterId ||
                item.fromAccountId == accountFilterId ||
                item.toAccountId == accountFilterId
            val searchableAmount = item.amount.toString()
            val matchesQuery = needle.isBlank() ||
                item.title.contains(needle, ignoreCase = true) ||
                item.subtitle.contains(needle, ignoreCase = true) ||
                item.note.contains(needle, ignoreCase = true) ||
                item.kind.label.contains(needle, ignoreCase = true) ||
                item.typeLabel.contains(needle, ignoreCase = true) ||
                item.category?.contains(needle, ignoreCase = true) == true ||
                item.subcategory?.contains(needle, ignoreCase = true) == true ||
                item.accountLabel.contains(needle, ignoreCase = true) ||
                item.dateLabel.contains(needle, ignoreCase = true) ||
                item.rawDate.contains(needle, ignoreCase = true) ||
                searchableAmount.contains(normalizedNumericNeedle, ignoreCase = true)
            val matchesCategory = exactCategory == null ||
                (item.categoryContributions?.takeIf { it.isNotEmpty() }?.containsKey(exactCategory)
                    ?: ((item.category?.takeIf(String::isNotBlank) ?: "Άλλο") == exactCategory))
            val matchesDate = (effectiveDateFrom == null && effectiveDateTo == null) ||
                (item.rawDate.length >= 10 &&
                    (effectiveDateFrom == null || item.rawDate.take(10) >= effectiveDateFrom) &&
                    (effectiveDateTo == null || item.rawDate.take(10) <= effectiveDateTo))
            matchesFilter && matchesAccount && matchesQuery && matchesCategory && matchesDate
        }
    }

    /**
     * Read-only summary for the current search/filter projection. Transfers are intentionally
     * excluded from income/expense/net so the UI never double-counts internal money movement.
     */
    val visibleIncome: Double = visibleItems
        .asSequence()
        .filter { it.kind == ActivityKind.INCOME }
        .sumOf { it.amount.coerceAtLeast(0.0) }

    val visibleExpense: Double = visibleItems
        .asSequence()
        .filter { it.kind == ActivityKind.EXPENSE || it.kind == ActivityKind.CARD_PAYMENT }
        .sumOf { kotlin.math.abs(it.amount) }

    val visibleNet: Double = visibleIncome - visibleExpense
    val visiblePendingCount: Int = visibleItems.count(ActivityItem::pendingSync)

    /** Input order is already canonical newest-first; grouping must never reorder rows. */
    val visibleSections: List<ActivitySection> = buildList {
        visibleItems.forEach { item ->
            val key = item.rawDate.take(10).ifBlank { item.dateLabel }
            val last = lastOrNull()
            if (last?.date == key) {
                this[lastIndex] = last.copy(items = last.items + item)
            } else {
                add(ActivitySection(key, listOf(item)))
            }
        }
    }

    val selectedItem: ActivityItem? = items.firstOrNull { it.id == selectedId }

    fun categoryOptionsFor(item: ActivityItem): List<ActivityCategoryOption> = when (item.canonicalKind) {
        "income" -> incomeCategories
        "expense", "refund", "lending", "repayment", "card_purchase" -> expenseCategories
        else -> emptyList()
    }

    /** Isolated read view: opening analysis never replaces the user's global ledger filters. */
    fun forCategory(category: String, start: String, end: String): ActivityUiState = copy(
        query = "",
        filter = ActivityFilter.ALL,
        accountFilterId = null,
        selectedId = null,
        ledgerCategoryFilter = null,
        ledgerDateFrom = null,
        ledgerDateTo = null,
        typeFilterId = null,
        categoryFilter = category,
        dateFrom = start,
        dateTo = end,
        items = items.map { item ->
            val contribution = item.categoryContributions?.get(category)
            if (contribution == null) item else item.copy(amount = -contribution)
        },
    )
}

sealed interface ActivityAction {
    data class QueryChanged(val value: String) : ActivityAction
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
    is ActivityAction.RemoveFilter -> when (action.field) {
        ActivityFilterField.TYPE -> state.copy(filter = ActivityFilter.ALL, typeFilterId = null)
        ActivityFilterField.ACCOUNT -> state.copy(accountFilterId = null)
        ActivityFilterField.CATEGORY -> state.copy(ledgerCategoryFilter = null)
        ActivityFilterField.DATE -> state.copy(ledgerDateFrom = null, ledgerDateTo = null)
    }
    is ActivityAction.Select -> state.copy(selectedId = action.id)
    is ActivityAction.SaveEdit -> state.copy(
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
    is ActivityAction.Delete -> state.copy(
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

internal fun activityTypeLabel(canonicalKind: String): String = when (canonicalKind) {
    "expense" -> "Έξοδο"
    "income" -> "Έσοδο"
    "transfer" -> "Μεταφορά"
    "saving_cash_offset" -> "Αποταμίευση"
    "withdrawal" -> "Ανάληψη"
    "refund" -> "Επιστροφή"
    "lending" -> "Δανεισμός"
    "repayment" -> "Αποπληρωμή"
    "card_purchase" -> "Αγορά με κάρτα"
    "card_payment" -> "Πληρωμή κάρτας"
    "reconciliation" -> "Συμφωνία υπολοίπου"
    "split" -> "Σύνθετη αγορά"
    "adjustment" -> "Προσαρμογή"
    else -> canonicalKind.ifBlank { "Κίνηση" }
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
