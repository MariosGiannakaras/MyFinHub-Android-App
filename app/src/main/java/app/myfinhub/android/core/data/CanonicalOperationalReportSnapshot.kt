package app.myfinhub.android.core.data

import java.time.YearMonth

/** Web reports.ts operationalReportSnapshot: derived without modifying FinanceData. */
data class CanonicalOperationalReportSnapshot(
    val flow: CanonicalMonthlyFlow,
    val previous: CanonicalMonthlyFlow,
    val balances: Map<String, Double>,
    val creditDebt: Double,
    val creditLimit: Double,
    val creditUsage: Double,
    val creditAvailable: Double,
    val creditCards: Int,
    val creditCardRows: List<CanonicalReportCreditCard>,
    val receivables: Double,
    val recurring: Double,
    val savings: CanonicalSavingsBreakdown,
    val budget: Double,
    val budgetRemaining: Double,
)

fun CanonicalFinanceDocument.canonicalOperationalReportSnapshot(
    month: String,
    asOf: String? = null,
): CanonicalOperationalReportSnapshot {
    val current=YearMonth.parse(month)
    val cutoff=if (asOf != null && asOf.startsWith("$month-")) asOf
        else current.atEndOfMonth().toString()
    val flow=canonicalOperationalMonthlyFlow(month,asOf)
    val previous=canonicalOperationalMonthlyFlow(current.minusMonths(1).toString())
    val credit=canonicalReportCreditPortfolio(cutoff)
    val budget=settingsObject().number("monthlyBudget") ?: 0.0
    return CanonicalOperationalReportSnapshot(
        flow=flow,previous=previous,balances=accountBalances(cutoff),
        creditDebt=credit.debt,creditLimit=credit.limit,creditUsage=credit.usage,
        creditAvailable=credit.available,creditCards=credit.activeCards,
        creditCardRows=credit.cards,
        receivables=canonicalLendingPersonRows(cutoff).sumOf { it.outstanding },
        recurring=canonicalRecurringMonthlyTotal(),
        savings=canonicalSavingsBreakdown(month,asOf),
        budget=budget,budgetRemaining=budget-flow.expense,
    )
}
