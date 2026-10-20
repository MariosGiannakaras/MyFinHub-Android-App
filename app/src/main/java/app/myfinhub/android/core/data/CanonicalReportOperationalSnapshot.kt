package app.myfinhub.android.core.data

import java.time.YearMonth

/**
 * Web reports.ts operationalReportSnapshot: read-only finance-wide monthly
 * operational summary, distinct from the spendable Home hero (cash + payroll).
 */
data class CanonicalReportOperationalSnapshot(
    val month: String,
    val cutoffDateIso: String,
    val flow: CanonicalMonthlyFlow,
    val previous: CanonicalMonthlyFlow,
    val balances: Map<String, Double>,
    val credit: CanonicalReportCreditPortfolio,
    val receivables: Double,
    val recurringMonthlyTotal: Double,
    val savings: CanonicalSavingsBreakdown,
    val budget: Double,
    val budgetRemaining: Double,
)

/** The Web snapshot uses full month-end except when an as-of day belongs
 * to that requested reporting month. Do not advance current month balances
 * into the future or silently pull in another month.
 */
fun CanonicalFinanceDocument.canonicalOperationalReportSnapshot(
    month: String,
    asOf: String? = null,
): CanonicalReportOperationalSnapshot {
    val target=YearMonth.parse(month)
    val date=asOf?.takeIf { it.startsWith("$month-") }
        ?: target.atEndOfMonth().toString()
    val previousMonth=target.minusMonths(1).toString()
    val flow=canonicalOperationalMonthlyFlow(month,asOf)
    val previous=canonicalOperationalMonthlyFlow(previousMonth)
    val credit=canonicalReportCreditPortfolio(date)
    val receivables=canonicalLendingPersonRows(date).sumOf { it.outstanding }
    val recurring=canonicalRecurringMonthlyTotal()
    val savings=canonicalSavingsBreakdown(month,asOf)
    // Web intentionally uses settings.monthlyBudget (not category budget rows).
    val budget=settingsObject().number("monthlyBudget") ?: 0.0
    return CanonicalReportOperationalSnapshot(
        month=month,cutoffDateIso=date,
        flow=flow,previous=previous,
        balances=accountBalances(date),
        credit=credit,receivables=receivables,
        recurringMonthlyTotal=recurring,
        savings=savings,budget=budget,
        budgetRemaining=budget-flow.expense,
    )
}
