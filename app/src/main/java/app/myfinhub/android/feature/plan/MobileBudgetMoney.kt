package app.myfinhub.android.feature.plan

import java.math.BigDecimal

/**
 * A monthly budget is a positive euro amount with at most two exact decimal digits.
 *
 * Reject invalid sources rather than converting NaN/Infinity or rounded sub-cent
 * values into an actionable finance request. This is a presentation/draft guard;
 * it does not modify canonical Web accounting or stored transactions.
 */
private val MOBILE_BUDGET_DECIMAL = Regex("""[0-9]+(?:[.,][0-9]{1,2})?""")

internal fun mobileBudgetLimitOrNull(raw: String): Double? {
    val value = raw.trim()
    if (!MOBILE_BUDGET_DECIMAL.matches(value)) return null
    val cents = runCatching {
        BigDecimal(value.replace(',', '.')).movePointRight(2).longValueExact()
    }.getOrNull() ?: return null
    if (cents <= 0L) return null
    return cents / 100.0
}

/**
 * Render canonical imported budget progress only when every base metric is finite.
 * Never replace malformed imported finance numbers with a plausible zero balance.
 * The projected/commitment numbers are validated separately before being displayed.
 */
internal data class MobileBudgetRowMetrics(
    val limit: Double,
    val used: Double,
    val remaining: Double,
    val ratio: Double,
    val progress: Float,
)

internal fun mobileBudgetRowMetrics(
    budget: CategoryBudget,
    sourceRemaining: Boolean = false,
): MobileBudgetRowMetrics? {
    val limit = if (budget.effectiveLimit != null) {
        budget.effectiveLimit.takeIf { it.isFinite() && it > 0.0 }
    } else mobileBudgetLimitOrNull(budget.monthlyLimitText)
    if (limit == null || !budget.spent.isFinite()) return null
    val used = budget.spent.coerceAtLeast(0.0)
    val ratio = used / limit
    val remaining = if (sourceRemaining) budget.remaining else limit - used
    if (!ratio.isFinite() || !remaining.isFinite()) return null
    return MobileBudgetRowMetrics(limit, used, remaining, ratio, ratio.coerceIn(0.0, 1.0).toFloat())
}

internal fun mobileBudgetProjectionValid(budget: CategoryBudget): Boolean =
    budget.projectedDue.isFinite() && budget.projectedDue > 0.0 &&
        budget.projectedRemaining.isFinite()

internal fun mobileBudgetCommitmentValid(amount: Double): Boolean =
    amount.isFinite() && amount > 0.0
