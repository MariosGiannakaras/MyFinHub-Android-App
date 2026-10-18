package app.myfinhub.android.core.data

import java.time.YearMonth

/**
 * Web SavingsPage uses operationalMonthlyFlow and state.settings.savingsTargetRate ?? 0.2
 * for the monthly savings-rate goal, NOT the balance of savings accounts or personal goals.
 * Previous-month comparison always uses the fully completed calendar month.
 */
data class CanonicalSavingsMonthlyTargetSnapshot(
    val month: String,
    val income: Double,
    val saved: Double,
    val targetRate: Double,
    val targetAmount: Double,
    val progressPercent: Double?,
    val previousMonthSaved: Double,
)

fun CanonicalFinanceDocument.webSavingsMonthlyTargetSnapshot(
    month: String,
    asOf: String? = null,
): CanonicalSavingsMonthlyTargetSnapshot {
    val flow = canonicalOperationalMonthlyFlow(month, asOf)
    val rate = settingsObject().number("savingsTargetRate") ?: 0.2
    val actualRate = if (flow.income != 0.0) flow.saving / flow.income else 0.0
    val progress = if (!actualRate.isFinite() || !rate.isFinite() || rate <= 0.0) null
        else ((actualRate / rate) * 100.0).coerceIn(0.0, 100.0)
    val previousMonth = YearMonth.parse(month).minusMonths(1).toString()
    return CanonicalSavingsMonthlyTargetSnapshot(
        month = month,
        income = flow.income,
        saved = flow.saving,
        targetRate = rate,
        targetAmount = if (rate > 0.0 && flow.income > 0.0) flow.income * rate else 0.0,
        progressPercent = progress,
        previousMonthSaved = canonicalOperationalMonthlyFlow(previousMonth, asOf).saving,
    )
}
