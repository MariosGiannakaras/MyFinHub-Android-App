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
