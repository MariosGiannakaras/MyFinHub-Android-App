package app.myfinhub.android.core.data

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.json.JsonObject

/**
 * Web budgets.ts budgetProjectedCommitments. Read-only, non-posting projection
 * of next explicitly known expenses, with exactly the Web recurring/scheduled
 * identity guard. No inferred subscriptions, receipts or transaction posting.
 */
data class CanonicalBudgetCommitment(
    val id: String,
    val date: String,
    val category: String,
    val subcategory: String?,
    val amount: Double,
    val source: String,
)

fun CanonicalFinanceDocument.canonicalBudgetProjectedCommitments(
    month: String,
    asOf: String,
): List<CanonicalBudgetCommitment> {
    val period = runCatching { YearMonth.parse(month) }.getOrNull() ?: return emptyList()
    val today = runCatching { LocalDate.parse(asOf) }.getOrNull() ?: return emptyList()
    val end = period.atEndOfMonth()
    if (!end.isAfter(today)) return emptyList()
    val endIso = end.toString()
    val scheduled = state.array("scheduled").filterIsInstance<JsonObject>()
        .filter { row ->
            val due = row.string("dueDate").orEmpty()
            row.string("status") == "pending" && row.string("kind") == "expense" &&
                due > asOf && due <= endIso && due.startsWith("$month-") &&
                runCatching { LocalDate.parse(due) }.isSuccess &&
                row.string("completedEventId").isNullOrBlank() &&
                !row.string("category").isNullOrBlank() &&
                row.number("amount")?.let { it.isFinite() && it > 0.0 } == true
        }
    val result = scheduled.mapNotNull { row ->
        val amount = row.number("amount") ?: return@mapNotNull null
        if (runCatching { moneyToCents(amount) > 0L }.getOrDefault(false).not()) return@mapNotNull null
        CanonicalBudgetCommitment(
            id = row.string("id").orEmpty(),
            date = row.string("dueDate").orEmpty(),
            category = row.string("category").orEmpty(),
            subcategory = row.string("subcategory"),
            amount = amount, source = "scheduled",
        )
    }.toMutableList()

    val linkedThisMonth = state.array("events").filterIsInstance<JsonObject>()
        .filter { it.string("date")?.startsWith("$month-") == true }
        .mapNotNull { it.string("recurringId")?.takeIf(String::isNotBlank) }.toSet()

    val active = activeCanonicalRecurringItems()
    active.forEach { item ->
        val next = nextCanonicalRecurringDate(item, today)?.toString() ?: return@forEach
        if (next <= asOf || next > endIso || !next.startsWith("$month-") ||
            item.category.isBlank() || item.amount <= 0.0 || !item.amount.isFinite() ||
            item.id in linkedThisMonth ||
            !runCatching { moneyToCents(item.amount) > 0L }.getOrDefault(false)
        ) return@forEach

        // Native projection uses the same canonical override precedence as allCanonicalRecurringItems.
        // A duplicated ID cannot safely supply its optional subcategory identity.
        val sourceRows = allCanonicalRecurringItems().filter { it.id == item.id }
        if (sourceRows.size != 1) return@forEach
        val source = canonicalRecurringEditSource(item.id)?.row
        val subcategory = source?.string("subcategory")
        val duplicate = scheduled.any { scheduledRow ->
            scheduledRow.string("dueDate") == next &&
                scheduledRow.string("accountId") == item.accountId &&
                webBudgetCategoryKey(scheduledRow.string("note").orEmpty()) ==
                    webBudgetCategoryKey(item.name) &&
                runCatching {
                    moneyToCents(scheduledRow.number("amount") ?: Double.NaN) ==
                        moneyToCents(item.amount)
                }.getOrDefault(false) &&
                webBudgetCategoryKey(scheduledRow.string("category").orEmpty()) ==
                    webBudgetCategoryKey(item.category) &&
                webBudgetCategoryKey(scheduledRow.string("subcategory").orEmpty()) ==
                    webBudgetCategoryKey(subcategory.orEmpty())
        }
        if (!duplicate) result += CanonicalBudgetCommitment(
            id = item.id, date = next, category = item.category,
            subcategory = subcategory, amount = item.amount, source = "recurring",
        )
    }
    return result
}
