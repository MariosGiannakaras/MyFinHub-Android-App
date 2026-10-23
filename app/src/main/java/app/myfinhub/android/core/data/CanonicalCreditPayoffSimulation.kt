package app.myfinhub.android.core.data

import kotlin.math.abs
import kotlin.math.floor

/** Web creditRates.ts estimateCreditPayoff: advisory only, no posting to finance events. */
data class CanonicalCreditPayoffEstimate(
    val feasible: Boolean,
    val months: Int,
    val totalInterest: Double,
    val totalPaid: Double,
    val remaining: Double,
)

/**
 * Mirrors JavaScript Math.round for non-negative cents (not JVM ties-to-even).
 * The 2^53-1 integer ceiling is preserved so cross-client rounding is comparable.
 */
fun estimateCanonicalCreditPayoff(
    balance: Double,
    annualPercent: Double,
    monthlyPayment: Double,
    maxMonths: Int = 360,
): CanonicalCreditPayoffEstimate? {
    if (!balance.isFinite() || balance < 0.0 || !annualPercent.isFinite() ||
        annualPercent !in 0.0..100.0 || !monthlyPayment.isFinite() ||
        monthlyPayment <= 0.0 || maxMonths !in 1..600) return null
    val balanceScaled = balance * 100.0
    val paymentScaled = monthlyPayment * 100.0
    if (!balanceScaled.isFinite() || !paymentScaled.isFinite() ||
        abs(floor(balanceScaled + 0.5) - balanceScaled) > 0.00001 ||
        abs(floor(paymentScaled + 0.5) - paymentScaled) > 0.00001) return null
    val maxSafe = 9_007_199_254_740_991L
    if (balanceScaled > maxSafe.toDouble() ||
        paymentScaled > maxSafe.toDouble()) return null
    var remaining = floor(balanceScaled + 0.5).toLong()
    val fixed = floor(paymentScaled + 0.5).toLong()
    if (fixed <= 0L) return null
    if (remaining == 0L) return CanonicalCreditPayoffEstimate(
        feasible = true, months = 0, totalInterest = 0.0, totalPaid = 0.0, remaining = 0.0,
    )
    var totalInterest = 0L
    var totalPaid = 0L
    for (month in 1..maxMonths) {
        if (remaining == 0L) break
        val interestRaw = remaining.toDouble() * annualPercent / 1200.0
        if (!interestRaw.isFinite() || interestRaw > maxSafe.toDouble()) return null
        val interest = floor(interestRaw + 0.5).toLong()
        if (fixed <= interest) return CanonicalCreditPayoffEstimate(
            feasible = false,
            months = month - 1,
            totalInterest = totalInterest / 100.0,
            totalPaid = totalPaid / 100.0,
            remaining = remaining / 100.0,
        )
        if (remaining > maxSafe - interest) return null
        val paid = minOf(fixed, remaining + interest)
        remaining += interest - paid
        if (totalInterest > maxSafe - interest || totalPaid > maxSafe - paid) return null
        totalInterest += interest
        totalPaid += paid
        if (remaining == 0L) return CanonicalCreditPayoffEstimate(
            feasible = true,
            months = month,
            totalInterest = totalInterest / 100.0,
            totalPaid = totalPaid / 100.0,
            remaining = 0.0,
        )
    }
    return CanonicalCreditPayoffEstimate(
        feasible = remaining == 0L,
        months = maxMonths,
        totalInterest = totalInterest / 100.0,
        totalPaid = totalPaid / 100.0,
        remaining = remaining / 100.0,
    )
}
