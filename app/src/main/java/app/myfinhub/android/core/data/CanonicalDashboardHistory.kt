package app.myfinhub.android.core.data

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.round
import kotlinx.serialization.json.JsonObject

/**
 * Native equivalent of Web dashboardAccountHistory: use movement-shaped daily balances and
 * re-anchor on every authoritative snapshot. Never fabricate zero balances ahead of a
 * first snapshot when later movement/snapshot evidence can reconstruct the opening period.
 */
fun dashboardHistoryStart(month: YearMonth): LocalDate =
    month.minusMonths(1).atEndOfMonth().minusDays(7)

fun CanonicalFinanceDocument.dashboardAccountHistory(
    accountIds: List<String>,
    start: LocalDate,
    end: LocalDate,
): Map<String, List<Double>> {
    val rows = accountIds.associateWith { emptyList<Double>() }
    if (accountIds.isEmpty() || end.isBefore(start)) return rows
    val snapshots = seed.array("snapshots").mapNotNull { it as? JsonObject }
        .filter { it.string("date").orEmpty() <= end.toString() }
        .sortedBy { it.string("date").orEmpty() }
    val prior = snapshots.lastOrNull { it.string("date").orEmpty() < start.toString() }
    val inRange = snapshots.filter { it.string("date").orEmpty() >= start.toString() }
    val first = inRange.firstOrNull()
    val deltaStart = prior?.string("date")?.let { LocalDate.parse(it).plusDays(1) } ?: start
    val eligible = accountIds.toSet()
    val deltas = accountIds.associateWith { mutableMapOf<String, Double>() }

    fun add(date: String, id: String?, delta: Double) {
        if (id !in eligible || date < deltaStart.toString() || date > end.toString() || delta == 0.0) return
        val bucket = deltas[id] ?: return
        bucket[date] = (bucket[date] ?: 0.0) + delta
    }
    effectiveLegacyTransactions().forEach { tx ->
        when (tx.type) {
            "income", "adjustment" -> add(tx.date, tx.accountId, tx.amount)
            "expense" -> add(tx.date, tx.accountId, -tx.amount)
            "transfer" -> {
                add(tx.date, tx.fromAccountId, -tx.amount)
                add(tx.date, tx.toAccountId, tx.amount)
            }
        }
    }
    canonicalEvents().forEach { event ->
        event.legs.forEach { leg -> add(event.date, leg.accountId, leg.amount) }
    }
    val closing = accountBalances(end.toString())
    val anchored = inRange.associate { snap ->
        val date = snap.string("date").orEmpty()
        date to accountBalances(date)
    }
    val priorBalances = prior?.string("date")?.let(::accountBalances)

    return accountIds.associateWith { id ->
        val changes = deltas.getValue(id)
        var running = when {
            prior != null -> (priorBalances?.get(id) ?: 0.0) +
                changes.filterKeys { it < start.toString() }.values.sum()
            first != null -> {
                val date = first.string("date").orEmpty()
                (anchored[date]?.get(id) ?: 0.0) -
                    changes.filterKeys { it <= date }.values.sum()
            }
            else -> (closing[id] ?: 0.0) - changes.values.sum()
        }
        val series = mutableListOf<Double>()
        var day = start
        while (!day.isAfter(end)) {
            val key = day.toString()
            running += changes[key] ?: 0.0
            anchored[key]?.get(id)?.let { running = it }
            if (day == end) running = closing[id] ?: running
            series.add(round(running * 100.0) / 100.0)
            day = day.plusDays(1)
        }
        series
    }
}

fun CanonicalFinanceDocument.dashboardPreviousMonthHistory(
    accountId: String,
    month: YearMonth,
    asOf: LocalDate,
): List<Double> {
    val previous = month.minusMonths(1)
    val end = previous.atDay(minOf(asOf.dayOfMonth, previous.lengthOfMonth()))
    return dashboardAccountHistory(listOf(accountId), previous.atDay(1), end)[accountId].orEmpty()
}

fun CanonicalFinanceDocument.dashboardBalanceChange(
    accountId: String,
    asOf: LocalDate,
    days: Long = 30,
): Double {
    val values = dashboardAccountHistory(listOf(accountId), asOf.minusDays(days.coerceAtLeast(1)), asOf)[accountId].orEmpty()
    return if (values.size < 2) 0.0 else round((values.last() - values.first()) * 100.0) / 100.0
}
