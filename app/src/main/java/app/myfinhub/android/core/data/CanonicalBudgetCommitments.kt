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
    // Web pendingScheduled sorts by dueDate and createdAt before selecting
    // future budget obligations; imported rows need not be stored in due order.
    val scheduled = state.array("scheduled").filterIsInstance<JsonObject>()
        .sortedWith(compareBy<JsonObject> { it.string("dueDate").orEmpty() }
            .thenBy { it.string("createdAt").orEmpty() })
        .filter { row ->
            val due = row.string("dueDate").orEmpty()
            row.string("status") == "pending" && row.string("kind") == "expense" &&
                due > asOf && due <= endIso && due.startsWith("$month-") &&
                runCatching { LocalDate.parse(due) }.isSuccess &&
                // Web budgets.ts uses !item.completedEventId. Whitespace
                // is truthy in JavaScript and therefore *not* an unlinked due.
                // Treating it as blank invents a second scheduled obligation.
                row.string("completedEventId").isNullOrEmpty() &&
                // Web item.category is a JavaScript truthiness check:
                // nonempty whitespace is truthy even if no budget category matches.
                !row.string("category").isNullOrEmpty() &&
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

    // Web budgets.ts targets the requested future month rather than only the
    // next payment from today. Estimates are advisory, not booked commitments.
    val targetAsOf = maxOf(today, period.atDay(1))
    val active = activeCanonicalRecurringItems()
    active.forEach { item ->
        if (item.isVariableEstimate) return@forEach
        val next = nextCanonicalRecurringDate(item, targetAsOf)?.toString() ?: return@forEach
        if (next <= asOf || next > endIso || !next.startsWith("$month-") ||
            item.category.isEmpty() || item.amount <= 0.0 || !item.amount.isFinite() ||
            item.id in linkedThisMonth ||
            !runCatching { moneyToCents(item.amount) > 0L }.getOrDefault(false)
        ) return@forEach

        // Web budgets.ts projects each physical recurring row, even if imported
        // data reuses an ID. The subcategory is an intrinsic property of this
        // precise row; resolving it by ID loses/merges unrelated obligations.
        // Mutation/replay still rejects ambiguous duplicate IDs elsewhere.
        // Scheduled and recurring records are distinct liabilities unless linked
        // by a real canonical relationship; equal dates/names/amounts prove nothing.
        result += CanonicalBudgetCommitment(
            id = item.id, date = next, category = item.category,
            subcategory = item.subcategory, amount = item.amount, source = "recurring",
        )
    }
    return result
}
