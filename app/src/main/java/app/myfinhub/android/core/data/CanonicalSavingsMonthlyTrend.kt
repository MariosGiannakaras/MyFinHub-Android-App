package app.myfinhub.android.core.data

import java.time.YearMonth
import kotlin.math.max

/** Web SavingsPage actual/goal savings trend sampling on days 1, 8, 15, 22 and month-end. */
data class CanonicalSavingsMonthlyTrend(
    val month: String,
    val days: List<Int>,
    val actual: List<Double>,
    val target: List<Double>,
    val scaleMax: Double,
)

fun CanonicalFinanceDocument.webSavingsMonthlyTrend(
    month: String,
    asOf: String? = null,
): CanonicalSavingsMonthlyTrend {
    val daysInMonth = YearMonth.parse(month).lengthOfMonth()
    val days = listOf(1, 8, 15, 22, daysInMonth).distinct().sorted()
    val flow = canonicalOperationalMonthlyFlow(month, asOf)
    val rate = settingsObject().number("savingsTargetRate") ?: 0.2
    val targetTotal = if (rate > 0.0 && flow.income > 0.0) rate * flow.income else 0.0
    val rows = canonicalSavingsBreakdown(month, asOf).rows
    val actual = days.map { day ->
        rows.filter { it.date.takeLast(2).toIntOrNull()?.let { d -> d <= day } == true }
            .sumOf(CanonicalSavingsHistoryRow::amount)
    }
    val target = days.map { day -> targetTotal * day / daysInMonth }
    return CanonicalSavingsMonthlyTrend(
        month = month,
        days = days,
        actual = actual,
        target = target,
        scaleMax = max(1.0, max(targetTotal, max(flow.saving, actual.maxOrNull() ?: 0.0))),
    )
}
