package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.serialization.json.JsonObject

/**
 * Advisory-only Web goalProjections.ts parity. Scenarios never post a saving,
 * earmark shared funds, or become another source of financial truth.
 */
data class CanonicalGoalScenario(
    val label: String,
    val monthly: Double,
    val totalAtDeadline: Double,
    val gapAtDeadline: Double,
)

data class CanonicalSavingsGoalProjection(
    val status: String,
    val asOf: String,
    val targetDate: String?,
    val target: Double,
    val sharedSavingsBalance: Double,
    val remaining: Double,
    val contributionMonths: Int?,
    val suggestedMonthly: Double?,
    val operatingLiquidityMinimum90d: Double,
    val knownObligationCount90d: Int,
    val forecastOmissions: Int,
    val forecastCompleteness: String,
    val suggestedContributionRisk: String,
    val scenarios: List<CanonicalGoalScenario>,
)

private const val WEB_MAX_SAFE_CENTS = 9_007_199_254_740_991L

/** JS savingsGoalProjection accepts exact cents, including zero only for savings/custom input. */
private fun exactGoalCents(value: Double, allowZero: Boolean = false): Long {
    val cents = runCatching { moneyToCents(value) }.getOrNull()
    require(cents != null && cents in 0L..WEB_MAX_SAFE_CENTS &&
        (allowZero || cents > 0L) &&
        value.isFinite() && abs(value * 100.0 - cents.toDouble()) <= 1e-6) {
        "Το ποσό πρέπει να είναι έγκυρο σε ακριβή λεπτά ευρώ."
    }
    return cents
}

private fun validGoalIsoDate(value: String): LocalDate {
    require(value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
        "Μη έγκυρη ημερομηνία αναφοράς στόχου."
    }
    return runCatching { LocalDate.parse(value) }.getOrElse {
        throw IllegalArgumentException("Μη έγκυρη ημερομηνία αναφοράς στόχου.")
    }
}

/**
 * Web gives one last partial-month contribution opportunity only when the
 * deadline's day-of-month is later than the current day-of-month.
 */
private fun goalContributionMonths(asOf: LocalDate, due: LocalDate): Int =
    ((due.year - asOf.year) * 12 + due.monthValue - asOf.monthValue +
        if (due.dayOfMonth > asOf.dayOfMonth) 1 else 0).coerceAtLeast(1)

/**
 * Take the exact goal row rather than an ID, so historical duplicate IDs
 * are never merged or mistaken for separate allocated savings.
 * A saved goal is read-only here; mutation/replay remains a separate guarded path.
 */
fun CanonicalFinanceDocument.canonicalSavingsGoalProjection(
    goal: JsonObject,
    asOf: LocalDate,
    customMonthly: Double? = null,
): CanonicalSavingsGoalProjection {
    val asOfIso = asOf.toString()
    validGoalIsoDate(asOfIso)
    val rawDeadline = goal.string("targetDate")?.takeIf(String::isNotEmpty)
    val deadline = rawDeadline?.let { date ->
        runCatching { validGoalIsoDate(date) }.getOrElse {
            throw IllegalArgumentException("Μη έγκυρη προθεσμία στόχου.")
        }
    }
    val targetCents = exactGoalCents(goal.number("targetAmount") ?: Double.NaN)
    val balances = accountBalances(asOfIso)
    val accounts = canonicalAccounts().filter { it.kind != "credit" }
    val savedCents = exactGoalCents(
        accounts.filter(CanonicalAccount::isSavingsAccount)
            .sumOf { (balances[it.id] ?: 0.0).coerceAtLeast(0.0) },
        allowZero = true,
    )
    val remainingCents = (targetCents - savedCents).coerceAtLeast(0L)
    val customCents = customMonthly?.let { exactGoalCents(it, allowZero = true) }
    val status = when {
        remainingCents == 0L -> "reached"
        deadline == null -> "undated"
        deadline.isBefore(asOf) -> "overdue"
        deadline.isEqual(asOf) -> "due_today"
        else -> "active"
    }
    val months = if (status == "active" && deadline != null) goalContributionMonths(asOf, deadline) else null
    val suggestedCents = if (months != null) {
        // Ceil in cents; integer form avoids fractional cent suggestions.
        remainingCents / months + if (remainingCents % months != 0L) 1L else 0L
    } else if (status == "reached") 0L else null

    val operatingIds = accounts.filterNot(CanonicalAccount::isSavingsAccount).map { it.id }.toSet()
    val forecast = cashFlowForecast(asOf, 90)
    val minimumOperatingCents = if (operatingIds.isEmpty()) 0L else {
        forecast.points.minOf { point ->
            point.balances.entries.filter { it.key in operatingIds }.fold(0L) { sum, (_, amount) ->
                Math.addExact(sum, moneyToCents(amount))
            }
        }
    }
    val scenarios = buildList {
        fun scenario(label: String, monthlyCents: Long) {
            if (months == null) return
            val total = Math.addExact(savedCents, Math.multiplyExact(monthlyCents, months.toLong()))
            require(total in 0L..WEB_MAX_SAFE_CENTS) { "Υπέρβαση του επιτρεπτού χρηματικού ορίου." }
            add(CanonicalGoalScenario(
                label = label,
                monthly = centsToMoney(monthlyCents),
                totalAtDeadline = centsToMoney(total),
                gapAtDeadline = centsToMoney((targetCents - total).coerceAtLeast(0L)),
            ))
        }
        scenario("none", 0L)
        if (suggestedCents != null) scenario("suggested", suggestedCents)
        if (customCents != null) scenario("custom", customCents)
    }
    return CanonicalSavingsGoalProjection(
        status = status,
        asOf = asOfIso,
        targetDate = rawDeadline,
        target = centsToMoney(targetCents),
        sharedSavingsBalance = centsToMoney(savedCents),
        remaining = centsToMoney(remainingCents),
        contributionMonths = months,
        suggestedMonthly = suggestedCents?.let(::centsToMoney),
        operatingLiquidityMinimum90d = centsToMoney(minimumOperatingCents),
        knownObligationCount90d = listOf("scheduled", "recurring", "loan")
            .sumOf { forecast.sourceCounts[it] ?: 0 },
        forecastOmissions = forecast.omitted.size,
        forecastCompleteness = if (forecast.insufficientData || forecast.omitted.isNotEmpty())
            "limited" else "known_movements",
        suggestedContributionRisk = when {
            suggestedCents == null -> "no_date"
            minimumOperatingCents < suggestedCents -> "liquidity_warning"
            else -> "none"
        },
        scenarios = scenarios,
    )
}
