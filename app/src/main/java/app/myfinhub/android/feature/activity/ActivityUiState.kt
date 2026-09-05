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
)

data class ActivityCategoryOption(
    val name: String,
    val subcategories: List<String> = emptyList(),
)

enum class ActivityKind(val label: String) {
    EXPENSE("Έξοδα"),
    INCOME("Έσοδα"),
    TRANSFER("Μεταφορές"),
    CARD_PAYMENT("Πληρωμές κάρτας"),
}

enum class ActivityFilter(val label: String) {
    ALL("Όλα"),
    EXPENSE("Έξοδα"),
    INCOME("Έσοδα"),
    TRANSFER("Μεταφορές"),
}

data class ActivitySection(val date: String, val items: List<ActivityItem>)

data class ActivityUiState(
    val query: String = "",
    val filter: ActivityFilter = ActivityFilter.ALL,
    val selectedId: String? = null,
    val items: List<ActivityItem> = syntheticActivityItems(),
    val expenseCategories: List<ActivityCategoryOption> = emptyList(),
    val incomeCategories: List<ActivityCategoryOption> = emptyList(),
) {
    // Activity can contain hundreds of canonical events. Compute immutable projections once per
    // state instance instead of re-filtering every time Compose reads them.
    val visibleItems: List<ActivityItem> = run {
        val needle = query.trim()
        items.filter { item ->
            val matchesFilter = when (filter) {
                ActivityFilter.ALL -> true
                ActivityFilter.EXPENSE -> item.kind == ActivityKind.EXPENSE || item.kind == ActivityKind.CARD_PAYMENT
                ActivityFilter.INCOME -> item.kind == ActivityKind.INCOME
                ActivityFilter.TRANSFER -> item.kind == ActivityKind.TRANSFER
            }
            val matchesQuery = needle.isBlank() ||
                item.title.contains(needle, ignoreCase = true) ||
                item.subtitle.contains(needle, ignoreCase = true) ||
                item.category?.contains(needle, ignoreCase = true) == true ||
                item.subcategory?.contains(needle, ignoreCase = true) == true ||
                item.accountLabel.contains(needle, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

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

    fun categoryOptionsFor(item: ActivityItem): List<ActivityCategoryOption> =
        if (item.kind == ActivityKind.INCOME) incomeCategories else expenseCategories
}

sealed interface ActivityAction {
    data class QueryChanged(val value: String) : ActivityAction
    data class FilterChanged(val value: ActivityFilter) : ActivityAction
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
    is ActivityAction.FilterChanged -> state.copy(filter = action.value)
    is ActivityAction.Select -> state.copy(selectedId = action.id)
    is ActivityAction.SaveEdit -> state.copy(
        items = state.items.map { item ->
            if (item.id == action.id) {
                item.copy(
                    rawDate = action.date ?: item.rawDate,
                    dateLabel = action.date ?: item.dateLabel,
                    subtitle = action.note,
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
