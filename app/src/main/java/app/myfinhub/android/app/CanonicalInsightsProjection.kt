package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.categoryTotalsBetween
import app.myfinhub.android.core.data.flowBetween
import app.myfinhub.android.core.data.monthlyFlow
import app.myfinhub.android.feature.insights.InsightCategory
import app.myfinhub.android.feature.insights.InsightsComparison
import app.myfinhub.android.feature.insights.InsightsUiState
import app.myfinhub.android.feature.insights.TrendPoint
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

internal fun projectCanonicalInsightsState(
    document: CanonicalFinanceDocument,
    today: LocalDate,
): InsightsUiState {
    val currentMonth = YearMonth.from(today)
    val currentStart = currentMonth.atDay(1)
    val currentEnd = today
    val previousMonth = currentMonth.minusMonths(1)
    val comparisonDay = min(today.dayOfMonth, previousMonth.lengthOfMonth())
    val currentComparableEnd = currentMonth.atDay(comparisonDay)
    val previousComparableEnd = previousMonth.atDay(comparisonDay)

    val currentComparable = document.flowBetween(
        currentStart.toString(),
        currentComparableEnd.toString(),
    )
    val previousComparable = document.flowBetween(
        previousMonth.atDay(1).toString(),
        previousComparableEnd.toString(),
    )

    val trend = (3L downTo 0L).map { offset -> currentMonth.minusMonths(offset) }.map { month ->
        val partial = month == currentMonth && today.dayOfMonth < currentMonth.lengthOfMonth()
        val flow = if (month == currentMonth) {
            document.flowBetween(month.atDay(1).toString(), currentEnd.toString())
        } else {
            document.monthlyFlow(month.toString())
        }
        TrendPoint(
            label = insightsMonthLabel(month),
            income = flow.income,
            expense = flow.expense,
            isPartial = partial,
            periodDetail = if (partial) "έως ${insightsDayMonthLabel(today)}" else null,
        )
    }

    val categories = document.categoryTotalsBetween(currentStart.toString(), currentEnd.toString())
        .entries
        .sortedByDescending { it.value }
    val categoryTotal = categories.sumOf { it.value }
    val completedMonthSpend = trend.filterNot(TrendPoint::isPartial).map(TrendPoint::expense)

    return InsightsUiState(
        monthlyTrend = trend,
        categories = categories.take(8).map { (name, amount) ->
            InsightCategory(
                name = name,
                amount = amount,
                share = if (categoryTotal <= 0.0) 0f else (amount / categoryTotal).toFloat(),
            )
        },
        averageMonthlySpend = completedMonthSpend.average().takeIf { it.isFinite() } ?: 0.0,
        comparison = InsightsComparison(
            currentLabel = insightsRangeLabel(currentMonth, comparisonDay),
            previousLabel = insightsRangeLabel(previousMonth, comparisonDay),
            currentIncome = currentComparable.income,
            currentExpense = currentComparable.expense,
            previousIncome = previousComparable.income,
            previousExpense = previousComparable.expense,
        ),
    )
}

private fun insightsMonthLabel(month: YearMonth): String = month.atDay(1)
    .format(DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("el-GR")))

private fun insightsDayMonthLabel(date: LocalDate): String = date
    .format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("el-GR")))

private fun insightsRangeLabel(month: YearMonth, endDay: Int): String {
    val monthYear = month.atDay(1)
        .format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("el-GR")))
    return if (endDay <= 1) "1 $monthYear" else "1–$endDay $monthYear"
}
