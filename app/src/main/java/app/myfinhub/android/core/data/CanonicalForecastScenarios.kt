package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlin.math.abs

/** Exact Web forecastScenarios.ts assumption; no server mutation or synthetic ledger event. */
data class CanonicalForecastAssumption(
    val id: String,
    val accountId: String,
    val date: String,
    val kind: String,
    val amount: Double,
)

data class CanonicalForecastScenarioPoint(
    val date: LocalDate,
    val portfolio: Double,
    val balances: Map<String, Double>,
    val scenarioDelta: Double,
)

data class CanonicalForecastScenarioAccount(
    val accountId: String,
    val projected: Double,
    val minimum: Double,
    val firstNegativeDate: LocalDate?,
    val firstLowDate: LocalDate?,
)

data class CanonicalForecastScenarioEvidence(
    val declared: Int,
    val estimated: Int,
    val hypothetical: Int,
    val omitted: Int,
)

data class CanonicalForecastScenario(
    val baseline: CanonicalCashFlowForecast,
    val points: List<CanonicalForecastScenarioPoint>,
    val projectedPortfolio: Double,
    val minimumPortfolio: Double,
    val minimumPortfolioDate: LocalDate,
    val firstPortfolioShortfallDate: LocalDate?,
    val accounts: List<CanonicalForecastScenarioAccount>,
    val assumptions: List<CanonicalForecastAssumption>,
    val evidence: CanonicalForecastScenarioEvidence,
)

private const val WEB_SAFE_FORECAST_CENTS = 9_007_199_254_740_991L

private fun scenarioCents(amount: Double): Long {
    val cents = runCatching { moneyToCents(amount) }.getOrNull()
    require(cents != null && cents in 1L..WEB_SAFE_FORECAST_CENTS &&
        amount.isFinite() && abs(amount * 100.0 - cents.toDouble()) <= 1e-6) {
        "Το ποσό σεναρίου πρέπει να είναι θετικό και ακριβές σε λεπτά ευρώ."
    }
    return cents
}

/**
 * Pure what-if overlay on an existing canonical forecast. Reuse the *exact*
 * production forecast already computed for the selected 30/60/90-day horizon.
 * The overlay is never persisted; same-day declared and hypothetical movements
 * remain additive and are never deduplicated by date, label or amount.
 */
fun CanonicalCashFlowForecast.webForecastScenario(
    assumptions: List<CanonicalForecastAssumption>,
): CanonicalForecastScenario {
    require(horizonDays in setOf(30, 60, 90)) {
        "Επίλεξε ορίζοντα 30, 60 ή 90 ημερών."
    }
    require(assumptions.size <= 16) { "Μέχρι 16 υποθέσεις ανά σενάριο." }
    val ids = accounts.map { it.accountId }.toSet()
    val seen = mutableSetOf<String>()
    val byDate = sortedMapOf<LocalDate, MutableMap<String, Long>>()
    assumptions.forEach { assumption ->
        require(assumption.id.isNotEmpty() && assumption.id.length <= 160 &&
            seen.add(assumption.id)) {
            "Κάθε υπόθεση χρειάζεται μοναδική ταυτότητα."
        }
        require(assumption.accountId in ids) {
            "Η υποθετική κίνηση χρειάζεται πραγματικό λογαριασμό."
        }
        val date = runCatching {
            require(assumption.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
            LocalDate.parse(assumption.date)
        }.getOrNull()
        require(date != null && date.isAfter(asOf) && !date.isAfter(endDate)) {
            "Η υποθετική ημερομηνία πρέπει να είναι μέσα στο μέλλον του ορίζοντα."
        }
        require(assumption.kind in setOf("extra_income", "extra_expense")) {
            "Μη έγκυρος τύπος υποθετικής κίνησης."
        }
        val amount = scenarioCents(assumption.amount)
        val signed = if (assumption.kind == "extra_income") amount else -amount
        val onDay = byDate.getOrPut(date) { mutableMapOf() }
        val sum = Math.addExact(onDay[assumption.accountId] ?: 0L, signed)
        require(sum in -WEB_SAFE_FORECAST_CENTS..WEB_SAFE_FORECAST_CENTS) {
            "Υπέρβαση ποσού υποθετικού σεναρίου."
        }
        onDay[assumption.accountId] = sum
    }
    require(points.isNotEmpty()) { "Δεν υπάρχουν σημεία πρόβλεψης." }
    val dates = (points.map { it.date } + byDate.keys).distinct().sorted()
    val cumulative = mutableMapOf<String, Long>()
    val minAccount = accounts.associate { it.accountId to it.current }.toMutableMap()
    val firstNegative = accounts.associate { it.accountId to
        if (it.current < 0.0) asOf else null }.toMutableMap()
    val firstLow = accounts.associate { it.accountId to
        if (it.current < 100.0) asOf else null }.toMutableMap()
    val projectedPoints = mutableListOf<CanonicalForecastScenarioPoint>()
    var originalIndex = 0
    var minPortfolio = Double.POSITIVE_INFINITY
    var minDate = asOf
    var firstShortfall: LocalDate? = null
    dates.forEach { date ->
        while (originalIndex + 1 < points.size &&
            !points[originalIndex + 1].date.isAfter(date)) originalIndex++
        val actual = points[originalIndex]
        byDate[date]?.forEach { (id, signed) ->
            cumulative[id] = Math.addExact(cumulative[id] ?: 0L, signed)
        }
        val balances = linkedMapOf<String, Double>()
        var portfolioCents = 0L
        var deltaCents = 0L
        ids.forEach { id ->
            val delta = cumulative[id] ?: 0L
            val real = moneyToCents(actual.balances[id] ?: 0.0)
            val amount = Math.addExact(real, delta)
            require(amount in -WEB_SAFE_FORECAST_CENTS..WEB_SAFE_FORECAST_CENTS) {
                "Υπέρβαση ορίου υπολογισμού σεναρίου."
            }
            portfolioCents = Math.addExact(portfolioCents, amount)
            deltaCents = Math.addExact(deltaCents, delta)
            require(portfolioCents in -WEB_SAFE_FORECAST_CENTS..WEB_SAFE_FORECAST_CENTS) {
                "Υπέρβαση συνολικού υπολοίπου."
            }
            val value = centsToMoney(amount)
            balances[id] = value
            minAccount[id] = kotlin.math.min(minAccount[id] ?: value, value)
            if (firstNegative[id] == null && amount < 0L) firstNegative[id] = date
            if (firstLow[id] == null && amount < 10_000L) firstLow[id] = date
        }
        val portfolio = centsToMoney(portfolioCents)
        projectedPoints += CanonicalForecastScenarioPoint(
            date, portfolio, balances, centsToMoney(deltaCents),
        )
        if (portfolio < minPortfolio) { minPortfolio = portfolio; minDate = date }
        if (firstShortfall == null && portfolioCents < 0L) firstShortfall = date
    }
    val last = projectedPoints.last()
    return CanonicalForecastScenario(
        baseline = this, points = projectedPoints,
        projectedPortfolio = last.portfolio, minimumPortfolio = minPortfolio,
        minimumPortfolioDate = minDate, firstPortfolioShortfallDate = firstShortfall,
        accounts = accounts.map { account ->
            CanonicalForecastScenarioAccount(
                accountId = account.accountId,
                projected = last.balances[account.accountId] ?: 0.0,
                minimum = minAccount[account.accountId] ?: 0.0,
                firstNegativeDate = firstNegative[account.accountId],
                firstLowDate = firstLow[account.accountId],
            )
        },
        assumptions = assumptions.toList(),
        evidence = CanonicalForecastScenarioEvidence(
            declared = (sourceCounts["dated_event"] ?: 0) + (sourceCounts["scheduled"] ?: 0),
            estimated = (sourceCounts["recurring"] ?: 0) + (sourceCounts["loan"] ?: 0),
            hypothetical = assumptions.size,
            omitted = omitted.size,
        ),
    )
}

/** A convenience adapter for golden tests; never writes canonical FinanceData. */
fun CanonicalFinanceDocument.webForecastScenario(
    asOf: LocalDate,
    horizonDays: Int,
    assumptions: List<CanonicalForecastAssumption>,
): CanonicalForecastScenario =
    cashFlowForecast(asOf, horizonDays).webForecastScenario(assumptions)
