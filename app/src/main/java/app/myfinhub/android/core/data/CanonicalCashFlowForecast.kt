package app.myfinhub.android.core.data

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonObject

/** Read-only Web forecast.ts finance projection, not a second state store. */
data class CanonicalForecastMovement(
    val id: String, val date: LocalDate, val source: String,
    val label: String, val legs: List<CanonicalLedgerLeg>, val portfolioDelta: Double,
)
data class CanonicalForecastPoint(val date: LocalDate, val portfolio: Double, val balances: Map<String, Double>)
data class CanonicalForecastAccount(
    val accountId: String, val current: Double, val projected: Double,
    val minimum: Double, val firstLowDate: LocalDate?, val firstNegativeDate: LocalDate?,
)
data class CanonicalCashFlowForecast(
    val asOf: LocalDate, val endDate: LocalDate, val horizonDays: Int,
    val currentPortfolio: Double, val projectedPortfolio: Double,
    val minimumPortfolio: Double, val minimumPortfolioDate: LocalDate,
    val points: List<CanonicalForecastPoint>, val movements: List<CanonicalForecastMovement>,
    val accounts: List<CanonicalForecastAccount>, val omitted: List<String>,
    val sourceCounts: Map<String, Int>,
) {
    val insufficientData: Boolean get() = movements.isEmpty()
}

private fun forecastDate(raw: String?): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()
private fun LocalDate.afterMonths(count: Int): LocalDate {
    val target = YearMonth.from(this).plusMonths(count.toLong())
    return target.atDay(min(dayOfMonth, target.lengthOfMonth()))
}
private fun LocalDate.atForecastDay(day: Int) =
    YearMonth.from(this).atDay(min(day, YearMonth.from(this).lengthOfMonth()))

/**
 * The portfolio includes every non-credit account (including excluded savings); two valid
 * portfolio legs of a transfer net to zero. Omitted unsafe references are surfaced, not guessed.
 */
fun CanonicalFinanceDocument.cashFlowForecast(asOf: LocalDate, horizonDays: Int): CanonicalCashFlowForecast {
    require(horizonDays in setOf(30, 60, 90)) { "Ο ορίζοντας πρέπει να είναι 30, 60 ή 90 ημέρες." }
    val end = asOf.plusDays(horizonDays.toLong())
    val accounts = canonicalAccounts().filter { it.kind != "credit" }
    val ids = accounts.mapTo(mutableSetOf(), CanonicalAccount::id)
    val actual = accountBalances(asOf.toString())
    val balances = accounts.associate { it.id to (actual[it.id] ?: 0.0) }.toMutableMap()
    val start = balances.values.sum()
    val rows = mutableListOf<CanonicalForecastMovement>()
    val omitted = mutableListOf<String>()
    fun leg(id: String, amount: Double) = CanonicalLedgerLeg(id, amount)
    fun add(id: String, date: LocalDate, source: String, label: String, legs: List<CanonicalLedgerLeg>) {
        rows += CanonicalForecastMovement(id, date, source, label, legs,
            legs.filter { it.accountId in ids }.sumOf(CanonicalLedgerLeg::amount))
    }
    canonicalScheduled().filter { it.status == "pending" }.forEach { scheduled ->
        val due = forecastDate(scheduled.dueDate) ?: run {
            omitted += "Άγνωστη ημερομηνία προγραμματισμένης κίνησης: ${scheduled.id}"
            return@forEach
        }
        if (due.isAfter(end)) return@forEach
        val date = if (due.isBefore(asOf)) asOf else due
        if (scheduled.kind == "transfer") {
            val from = scheduled.fromAccountId
            val to = scheduled.toAccountId
            if (from !in ids || to !in ids || from == to) {
                omitted += "Άκυρη προγραμματισμένη μεταφορά: ${scheduled.id}"
                return@forEach
            }
            add("forecast-scheduled-${scheduled.id}", date, "scheduled", scheduled.note,
                listOf(leg(from!!, -abs(scheduled.amount)), leg(to!!, abs(scheduled.amount))))
        } else {
            val id = scheduled.accountId
            if (id !in ids) {
                omitted += "Μη διαθέσιμος λογαριασμός κίνησης: ${scheduled.id}"
                return@forEach
            }
            add("forecast-scheduled-${scheduled.id}", date, "scheduled", scheduled.note,
                listOf(leg(id!!, if (scheduled.kind == "income") abs(scheduled.amount) else -abs(scheduled.amount))))
        }
    }
    activeCanonicalRecurringItems().forEach { item ->
        if (item.accountId !in ids) {
            omitted += "Μη διαθέσιμος λογαριασμός παγίου: ${item.id}"
            return@forEach
        }
        var next = nextCanonicalRecurringDate(item, asOf)
        if (next == null) {
            omitted += "Άγνωστη ημερομηνία παγίου: ${item.id}"
            return@forEach
        }
        var guard = 0
        while (next != null && !next.isAfter(end) && guard <= 120) {
            add("forecast-recurring-${item.id}-$next", next, "recurring", item.name,
                listOf(leg(item.accountId, -abs(item.amount))))
            val following = next.afterMonths(item.recurrenceMonths)
            if (!following.isAfter(next)) break
            next = following
            guard++
        }
    }
    val overrideLoans = state.obj("loanOverrides")
    val loans = seed.array("loans").mapNotNull { it as? JsonObject }
        .map { (overrideLoans[it.string("id").orEmpty()] as? JsonObject) ?: it } +
        state.array("customLoans").mapNotNull { it as? JsonObject }
    loans.forEach { loan ->
        val loanId = loan.string("id") ?: return@forEach
        val name = loan.string("name").orEmpty()
        val totalInstallments = (loan.number("installments") ?: 0.0).toInt().coerceAtLeast(0)
        val installment = abs(loan.number("installment") ?: 0.0)
        if (totalInstallments == 0 || installment <= 0.0) return@forEach
        val self = loan.string("kind") == "self-loan" || loan.string("source") == "self-loan" ||
            Regex("\\bHELP\\b|ΒΟΗΘΕΙΑ", RegexOption.IGNORE_CASE)
                .containsMatchIn(name + " " + loan.string("provider").orEmpty())
        if (self) {
            omitted += "Εσωτερικό δάνειο χωρίς ουδέτερο λογαριασμό: $loanId"
            return@forEach
        }
        val accountId = loan.string("defaultAccountId") ?: state.obj("settings").string("defaultLoanAccount")
        if (accountId !in ids) {
            omitted += "Μη διαθέσιμος λογαριασμός δόσης: $loanId"
            return@forEach
        }
        val payments = state.array("events").mapNotNull { it as? JsonObject }.filter {
            it.string("loanId") == loanId && it.string("date").orEmpty() <= asOf.toString()
        }
        val baseline = (loan.number("paidCount") ?: 0.0).toInt() +
            (state.obj("loanExtra").number(loanId) ?: 0.0).toInt()
        val linkedCount = payments.sumOf {
            val count = it.number("installmentCount") ?: 1.0
            if (count > 0 && count % 1.0 == 0.0) count.toInt() else 1
        }
        val left = (totalInstallments - baseline - linkedCount).coerceAtLeast(0)
        val paid = baseline.coerceIn(0, totalInstallments) * installment +
            payments.sumOf { (it.number("amount") ?: 0.0).coerceAtLeast(0.0) }
        val outstanding = ((loan.number("total") ?: totalInstallments * installment) -
            paid - (loan.number("forgivenAmount") ?: 0.0)).coerceAtLeast(0.0)
        if (left <= 0 || outstanding <= 0.005) return@forEach
        val knownDates = payments.mapNotNull { forecastDate(it.string("date")) }
        val typical = if (knownDates.isNotEmpty())
            (knownDates.sumOf(LocalDate::getDayOfMonth).toDouble() / knownDates.size)
                .roundToInt().coerceIn(1, 31)
        else forecastDate(loan.string("firstExpectedDate"))?.dayOfMonth
            ?: loan.string("day")?.toIntOrNull()?.takeIf { it in 1..31 }
        if (typical == null) {
            omitted += "Άγνωστη ημέρα δόσης: $loanId"
            return@forEach
        }
        val anchor = forecastDate(loan.string("firstExpectedDate"))
        var next = if (anchor != null && !anchor.isBefore(asOf)) anchor else
            asOf.atForecastDay(typical).let { if (it.isBefore(asOf)) it.afterMonths(1) else it }
        if (YearMonth.from(next) == YearMonth.from(asOf) &&
            knownDates.any { YearMonth.from(it) == YearMonth.from(asOf) }) next = next.afterMonths(1)
        var count = 0
        while (!next.isAfter(end) && count < left) {
            add("forecast-loan-$loanId-$next-$count", next, "loan", name,
                listOf(leg(accountId!!, -installment)))
            next = next.afterMonths(1)
            count++
        }
    }
    canonicalEvents().forEach { event ->
        val date = forecastDate(event.date) ?: return@forEach
        if (!date.isAfter(asOf) || date.isAfter(end)) return@forEach
        val legs = event.legs.filter { it.accountId in ids }
        if (legs.isNotEmpty()) add("forecast-event-${event.id}", date, "dated_event", event.note, legs)
    }
    rows.sortWith(compareBy<CanonicalForecastMovement> { it.date }.thenBy { it.source }.thenBy { it.id })
    val initial = balances.toMap()
    val minimum = balances.toMutableMap()
    val low = balances.mapValues { if (it.value < 100.0) asOf else null }.toMutableMap()
    val negative = balances.mapValues { if (it.value < 0.0) asOf else null }.toMutableMap()
    var portfolio = start
    var portfolioMin = start
    var portfolioMinDate = asOf
    val points = mutableListOf(CanonicalForecastPoint(asOf, portfolio, balances.toMap()))
    rows.groupBy(CanonicalForecastMovement::date).forEach { (date, daily) ->
        daily.forEach { item -> item.legs.forEach { l ->
            if (l.accountId in ids) balances[l.accountId] = (balances[l.accountId] ?: 0.0) + l.amount
        } }
        portfolio = balances.values.sum()
        if (portfolio < portfolioMin) { portfolioMin = portfolio; portfolioMinDate = date }
        accounts.forEach { account ->
            val id = account.id
            val value = balances[id] ?: 0.0
            minimum[id] = min(minimum[id] ?: value, value)
            if (low[id] == null && value < 100.0) low[id] = date
            if (negative[id] == null && value < 0.0) negative[id] = date
        }
        val current = CanonicalForecastPoint(date, portfolio, balances.toMap())
        if (points.last().date == date) points[points.lastIndex] = current else points += current
    }
    if (points.last().date != end) points += CanonicalForecastPoint(end, portfolio, balances.toMap())
    return CanonicalCashFlowForecast(
        asOf, end, horizonDays, start, portfolio, portfolioMin, portfolioMinDate, points, rows,
        accounts.map { account ->
            CanonicalForecastAccount(account.id, initial[account.id] ?: 0.0,
                balances[account.id] ?: 0.0, minimum[account.id] ?: 0.0,
                low[account.id], negative[account.id])
        }, omitted, listOf("scheduled", "recurring", "loan", "dated_event")
            .associateWith { source -> rows.count { it.source == source } },
    )
}
