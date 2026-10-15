package app.myfinhub.android.core.data

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonObject

/** Read-only Web forecast.ts portfolio movement, independent of Home's restricted hero. */
data class CanonicalForecastMovement(
    val id: String,
    val date: String,
    val label: String,
    val source: String,
    val legs: List<CanonicalLedgerLeg>,
    val portfolioDelta: Double,
)
data class CanonicalForecastAccount(
    val accountId: String,
    val current: Double,
    val projected: Double,
    val minimum: Double,
    val firstLowDate: String?,
    val firstNegativeDate: String?,
)
data class CanonicalForecastPoint(
    val date: String,
    val portfolio: Double,
    val balances: Map<String, Double>,
)
data class CanonicalCashFlowForecast(
    val asOf: String,
    val endDate: String,
    val horizon: Int,
    val currentPortfolio: Double,
    val projectedPortfolio: Double,
    val minimumPortfolio: Double,
    val minimumPortfolioDate: String,
    val points: List<CanonicalForecastPoint>,
    val movements: List<CanonicalForecastMovement>,
    val accounts: List<CanonicalForecastAccount>,
    val omitted: List<String>,
    val sourceCounts: Map<String, Int>,
    val insufficientData: Boolean,
)

private fun forecastDate(value: String?): LocalDate? =
    value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun forecastMonthDate(yearMonth: YearMonth, day: Int): LocalDate =
    yearMonth.atDay(min(day, yearMonth.lengthOfMonth()))

private fun followingForecastDate(date: LocalDate, day: Int): LocalDate =
    forecastMonthDate(YearMonth.from(date).plusMonths(1),day)

private fun advanceForecastRecurring(date: LocalDate, months: Int): LocalDate =
    forecastMonthDate(YearMonth.from(date).plusMonths(months.toLong()),date.dayOfMonth)

/**
 * Portfolio includes every non-credit canonical asset account, unlike Home's semantic total.
 * All forecast movements are synthetic projections only; none modify state.events.
 */
fun CanonicalFinanceDocument.canonicalCashFlowForecast(
    asOf: String,
    horizon: Int,
): CanonicalCashFlowForecast {
    require(horizon in setOf(30,60,90)) { "Μη υποστηριζόμενο χρονικό παράθυρο." }
    val today = requireNotNull(forecastDate(asOf)) { "Μη έγκυρη ημερομηνία πρόβλεψης." }
    val end = today.plusDays(horizon.toLong())
    val accounts = canonicalAccounts().filter { it.kind != "credit" }
    val ids = accounts.map { it.id }.toSet()
    val present = accountBalances(asOf)
    val balances = linkedMapOf<String,Double>()
    accounts.forEach { balances[it.id] = present[it.id] ?: 0.0 }
    val current = balances.values.sum()
    val omissions = mutableListOf<String>()
    val movements = mutableListOf<CanonicalForecastMovement>()
    fun record(id: String, on: LocalDate, label: String, source: String, legs: List<CanonicalLedgerLeg>) {
        val included = legs.filter { it.accountId in ids }
        movements += CanonicalForecastMovement(id,on.toString(),label,source,included,included.sumOf { it.amount })
    }

    canonicalScheduled().filter { it.status == "pending" }.forEach { item ->
        val due = forecastDate(item.dueDate) ?: return@forEach
        if (due.isAfter(end)) return@forEach
        val posting = if(due.isBefore(today)) today else due
        if (item.kind == "transfer") {
            val from = item.fromAccountId
            val to = item.toAccountId
            if (from == null || to == null || from !in ids || to !in ids || from == to) {
                omissions += "Μη διαθέσιμοι λογαριασμοί προγραμματισμένης μεταφοράς: " + item.note
                return@forEach
            }
            record("forecast-scheduled-" + item.id,posting,item.note,"scheduled",
                listOf(CanonicalLedgerLeg(from,-abs(item.amount)),CanonicalLedgerLeg(to,abs(item.amount))))
        } else {
            val account = item.accountId
            if (account == null || account !in ids) {
                omissions += "Μη διαθέσιμος λογαριασμός προγραμματισμένης κίνησης: " + item.note
                return@forEach
            }
            record("forecast-scheduled-" + item.id,posting,item.note,"scheduled",
                listOf(CanonicalLedgerLeg(account,if(item.kind=="income") abs(item.amount) else -abs(item.amount))))
        }
    }

    activeCanonicalRecurringItems().forEach { item ->
        if (item.accountId !in ids) {
            omissions += "Μη διαθέσιμος λογαριασμός παγίου: " + item.name
            return@forEach
        }
        var due = nextCanonicalRecurringDate(item,today)
        if (due == null) {
            omissions += "Άγνωστη επόμενη ημερομηνία παγίου: " + item.name
            return@forEach
        }
        var count = 0
        while (due != null && !due.isAfter(end) && count <= 120) {
            record("forecast-recurring-" + item.id + "-" + due.toString(),due,item.name,"recurring",
                listOf(CanonicalLedgerLeg(item.accountId,-abs(item.amount))))
            val next = advanceForecastRecurring(due,item.recurrenceMonths)
            if (!next.isAfter(due)) break
            due = next
            count++
        }
    }

    val loanOverrides = state.obj("loanOverrides")
    val loans = seed.array("loans").filterIsInstance<JsonObject>().mapNotNull { loan ->
        val id = loan.string("id") ?: return@mapNotNull null
        (loanOverrides[id] as? JsonObject) ?: loan
    } + state.array("customLoans").filterIsInstance<JsonObject>()
    loans.forEach { loan ->
        val id = loan.string("id") ?: return@forEach
        val name = loan.string("name").orEmpty()
        val nominal = loan.number("installment") ?: 0.0
        val installments = loan.number("installments")?.toInt() ?: 0
        if (loan.string("kind") == "self-loan" || loan.string("source") == "self-loan" ||
            Regex("\\bHELP\\b|ΒΟΗΘΕΙΑ",RegexOption.IGNORE_CASE)
                .containsMatchIn(name + " " + loan.string("provider").orEmpty())) {
            omissions += "Εσωτερικό δάνειο χωρίς ουδέτερη διαδρομή: " + name
            return@forEach
        }
        val paidEvents = state.array("events").filterIsInstance<JsonObject>()
            .filter { it.string("loanId") == id && it.string("date").orEmpty() <= asOf }
            .sortedWith(compareByDescending<JsonObject> { it.string("date").orEmpty() }
                .thenByDescending { it.string("createdAt").orEmpty() })
        val legacyPaid = (loan.number("paidCount") ?: 0.0) +
            (state.obj("loanExtra").number(id) ?: 0.0)
        val linkedCount = paidEvents.sumOf { payment ->
            val n = payment.number("installmentCount") ?: 1.0
            if (n>0 && n%1.0==0.0) n else 1.0
        }
        val remaining = (installments - min(installments.toDouble(),legacyPaid+linkedCount)
            .toInt()).coerceAtLeast(0)
        if (remaining<=0 || nominal<=0) return@forEach
        val account = loan.string("defaultAccountId")?.takeIf(String::isNotBlank)
            ?: state.obj("settings").string("defaultLoanAccount")
        if (account == null || account !in ids) {
            omissions += "Μη διαθέσιμος λογαριασμός δόσης: " + name
            return@forEach
        }
        val anchor = forecastDate(loan.string("firstExpectedDate"))
        val paymentDates = paidEvents.mapNotNull { forecastDate(it.string("date")) }
        val day = when {
            paymentDates.isNotEmpty() -> paymentDates.map { it.dayOfMonth }.average().roundToInt().coerceIn(1,31)
            anchor != null -> anchor.dayOfMonth
            else -> loan.string("day")?.toIntOrNull()?.takeIf { it in 1..31 }
        }
        if (day == null) {
            omissions += "Άγνωστη ημέρα δόσης: " + name
            return@forEach
        }
        val thisMonth = YearMonth.from(today)
        var next = if(anchor != null && !anchor.isBefore(today)) anchor
            else forecastMonthDate(thisMonth,day).let {
                if (it.isBefore(today)) followingForecastDate(it,day) else it
            }
        if (YearMonth.from(next) == thisMonth &&
            paymentDates.any { YearMonth.from(it) == thisMonth }) {
            next = followingForecastDate(next,day)
        }
        var index = 0
        while (!next.isAfter(end) && index<remaining) {
            record("forecast-loan-" + id + "-" + next + "-" + index,next,name,"loan",
                listOf(CanonicalLedgerLeg(account,-abs(nominal))))
            next = followingForecastDate(next,day)
            index++
        }
    }

    canonicalEvents().forEach { event ->
        val due = forecastDate(event.date) ?: return@forEach
        if(!due.isAfter(today) || due.isAfter(end)) return@forEach
        val legs = event.legs.filter { it.accountId in ids }
        if(legs.isNotEmpty()) record("forecast-event-" + event.id,due,event.note,"dated_event",legs)
    }

    val ordered = movements.sortedWith(compareBy<CanonicalForecastMovement> { it.date }
        .thenBy { it.source }.thenBy { it.id })
    val minimums = balances.toMutableMap()
    val low = accounts.associate { it.id to (if((balances[it.id]?:0.0)<100.0) asOf else null) }.toMutableMap()
    val negative = accounts.associate { it.id to (if((balances[it.id]?:0.0)<0.0) asOf else null) }.toMutableMap()
    val points = mutableListOf(CanonicalForecastPoint(asOf,current,balances.toMap()))
    var portfolio=current
    var minimum=current
    var minimumDate=asOf
    var position=0
    while (position<ordered.size) {
        val due = ordered[position].date
        while(position<ordered.size && ordered[position].date==due) {
            ordered[position].legs.forEach { leg ->
                balances[leg.accountId]=(balances[leg.accountId]?:0.0)+leg.amount
            }
            position++
        }
        portfolio=balances.values.sum()
        if(portfolio<minimum) { minimum=portfolio; minimumDate=due }
        accounts.forEach { account ->
            val value=balances[account.id]?:0.0
            minimums[account.id]=min(minimums[account.id]?:value,value)
            if (low[account.id]==null && value<100.0) low[account.id]=due
            if (negative[account.id]==null && value<0.0) negative[account.id]=due
        }
        if(points.last().date==due) points[points.lastIndex]=CanonicalForecastPoint(due,portfolio,balances.toMap())
        else points+=CanonicalForecastPoint(due,portfolio,balances.toMap())
    }
    if(points.last().date!=end.toString()) points+=CanonicalForecastPoint(end.toString(),portfolio,balances.toMap())
    val sources=listOf("scheduled","recurring","loan","dated_event")
    return CanonicalCashFlowForecast(asOf,end.toString(),horizon,current,portfolio,minimum,
        minimumDate,points,ordered,accounts.map { a ->
            CanonicalForecastAccount(a.id,present[a.id]?:0.0,balances[a.id]?:0.0,
                minimums[a.id]?:0.0,low[a.id],negative[a.id])
        },omissions,sources.associateWith { s -> ordered.count { it.source==s } },ordered.isEmpty())
}
