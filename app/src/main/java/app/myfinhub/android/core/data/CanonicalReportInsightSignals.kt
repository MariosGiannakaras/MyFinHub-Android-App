package app.myfinhub.android.core.data

import java.time.YearMonth
import kotlin.math.abs

/** Web reports.ts reportInsightModel, excluding credit snapshot (provided by Wallet). */
data class CanonicalReportTopCategory(
    val name: String,
    val amount: Double,
    val share: Double?,
    val change: Double?,
)

data class CanonicalReportInsightSignals(
    val incomeChange: Double?,
    val expenseChange: Double?,
    val savingChange: Double?,
    val netFlow: Double,
    val savingsRate: Double?,
    val previousSavingsRate: Double?,
    val trailingExpenseAverage: Double,
    val expenseVsTrailingAverage: Double?,
    val recurringMonthlyTotal: Double,
    val recurringBurden: Double?,
    val topCategory: CanonicalReportTopCategory?,
    val sufficientExpenseHistory: Boolean,
)

/**
 * Reuses canonical operational finance math. Web compares current month-to-date
 * with the FULL preceding month and the mean of three FULL prior months.
 * Income/saving ratios are null without positive income, relative change null
 * without a prior nonzero amount. No mutation, estimated extra event or double count.
 */
fun CanonicalFinanceDocument.canonicalReportInsightSignals(
    month: String,
    asOf: String? = null,
): CanonicalReportInsightSignals {
    val period=YearMonth.parse(month)
    val current=canonicalOperationalMonthlyFlow(month,asOf)
    val previousMonth=period.minusMonths(1).toString()
    val previous=canonicalOperationalMonthlyFlow(previousMonth)
    val priorThree=(1L..3L).map { offset ->
        canonicalOperationalMonthlyFlow(period.minusMonths(offset).toString())
    }
    val trailingAverage=priorThree.sumOf { it.expense } / priorThree.size
    val sufficientExpenseHistory=priorThree.any { it.expense > 0.0 }
    val currentCategories=canonicalReportSubcategoryTotals(month,asOf)
    val previousCategories=canonicalReportSubcategoryTotals(previousMonth)
        .associate { it.name to it.value }
    val top=currentCategories.firstOrNull()?.let { row ->
        val prior=previousCategories[row.name] ?: 0.0
        CanonicalReportTopCategory(
            name=row.name,
            amount=row.value,
            share=if (current.expense > 0.0) row.value/current.expense else null,
            change=relativeReportChange(row.value,prior),
        )
    }
    val monthlyRecurring=canonicalRecurringMonthlyTotal()
    return CanonicalReportInsightSignals(
        incomeChange=relativeReportChange(current.income,previous.income),
        expenseChange=relativeReportChange(current.expense,previous.expense),
        savingChange=relativeReportChange(current.saving,previous.saving),
        netFlow=current.income-current.expense,
        savingsRate=if (current.income>0.0) current.saving/current.income else null,
        previousSavingsRate=if (previous.income>0.0) previous.saving/previous.income else null,
        trailingExpenseAverage=trailingAverage,
        expenseVsTrailingAverage=if (trailingAverage>0.0)
            (current.expense-trailingAverage)/trailingAverage else null,
        recurringMonthlyTotal=monthlyRecurring,
        recurringBurden=if (current.income>0.0) monthlyRecurring/current.income else null,
        topCategory=top,
        sufficientExpenseHistory=sufficientExpenseHistory,
    )
}

private fun relativeReportChange(current: Double, previous: Double): Double? =
    if (previous == 0.0) {
        if (current == 0.0) 0.0 else null
    } else (current-previous)/abs(previous)
