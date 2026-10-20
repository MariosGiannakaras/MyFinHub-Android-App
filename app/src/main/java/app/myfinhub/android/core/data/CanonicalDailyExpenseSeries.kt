package app.myfinhub.android.core.data

import java.time.YearMonth

/** Web domain.ts dailyExpenseSeries: chronological days with net expense floored per day. */
data class CanonicalDailyExpense(val dayLabel: String, val value: Double)

fun CanonicalFinanceDocument.canonicalWebDailyExpenseSeries(
    month: String,
    asOf: String? = null,
): List<CanonicalDailyExpense> {
    // Fail closed for malformed month strings, and never infer a calendar period.
    val range = runCatching { YearMonth.parse(month) }.getOrNull()
        ?: return emptyList()
    val start = range.atDay(1).toString()
    val end = range.atEndOfMonth().toString()
    val cutoff = if (asOf?.startsWith(month + "-") == true) minOf(asOf, end) else end
    val totals = linkedMapOf<String, Double>()
    fun add(date: String, expense: Double) {
        if (date < start || date > cutoff) return
        totals[date] = (totals[date] ?: 0.0) + expense
    }
    effectiveLegacyTransactions().forEach { tx ->
        add(tx.date, legacyFlowImpact(tx).expense)
    }
    canonicalEvents().forEach { event ->
        add(event.date, eventFlowImpact(event).expense)
    }
    return totals.entries.sortedBy { it.key }.map { (date, sum) ->
        CanonicalDailyExpense(dayLabel = date.takeLast(2), value = sum.coerceAtLeast(0.0))
    }
}
