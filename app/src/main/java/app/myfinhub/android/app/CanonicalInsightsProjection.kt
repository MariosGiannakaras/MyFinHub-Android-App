package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.categoryTotalsBetween
import app.myfinhub.android.core.data.flowBetween
import app.myfinhub.android.core.data.monthlyFlow
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_30_DAYS
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_90_DAYS
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_MONTH
import app.myfinhub.android.feature.insights.InsightCategory
import app.myfinhub.android.feature.insights.InsightPeriodScope
import app.myfinhub.android.feature.insights.InsightsComparison
import app.myfinhub.android.feature.insights.InsightsUiState
import app.myfinhub.android.feature.insights.TrendPoint
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
    val (previousMonthStart, previousMonthEnd) = equivalentPreviousMonthWindow(today)

    val monthScope = buildInsightsScope(
        document = document,
        id = INSIGHTS_PERIOD_MONTH,
        label = "Μήνας",
        start = currentStart,
        end = currentEnd,
        comparisonStart = previousMonthStart,
        comparisonEnd = previousMonthEnd,
        contextLabel = if (today.dayOfMonth < currentMonth.lengthOfMonth()) {
            "Μερικός μήνας · έως ${insightsDayMonthLabel(today)}"
        } else {
            "Πλήρης μήνας"
        },
    )
    val thirtyStart = today.minusDays(29)
    val thirtyComparisonEnd = thirtyStart.minusDays(1)
    val thirtyScope = buildInsightsScope(
        document = document,
        id = INSIGHTS_PERIOD_30_DAYS,
        label = "30 ημ.",
        start = thirtyStart,
        end = today,
        comparisonStart = thirtyComparisonEnd.minusDays(29),
        comparisonEnd = thirtyComparisonEnd,
        contextLabel = "Κυλιόμενο διάστημα 30 ημερών",
    )
    val ninetyStart = today.minusDays(89)
    val ninetyComparisonEnd = ninetyStart.minusDays(1)
    val ninetyScope = buildInsightsScope(
        document = document,
        id = INSIGHTS_PERIOD_90_DAYS,
        label = "90 ημ.",
        start = ninetyStart,
        end = today,
        comparisonStart = ninetyComparisonEnd.minusDays(89),
        comparisonEnd = ninetyComparisonEnd,
        contextLabel = "Κυλιόμενο διάστημα 90 ημερών",
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
    val completedMonthSpend = trend.filterNot(TrendPoint::isPartial).map(TrendPoint::expense)

    return InsightsUiState(
        categoryStartDate = monthScope.startDate,
        categoryEndDate = monthScope.endDate,
        monthlyTrend = trend,
        categories = monthScope.categories,
        averageMonthlySpend = completedMonthSpend.average().takeIf { it.isFinite() } ?: 0.0,
        comparison = monthScope.comparison,
        periods = listOf(monthScope, thirtyScope, ninetyScope),
    )
}

private fun buildInsightsScope(
    document: CanonicalFinanceDocument,
    id: String,
    label: String,
    start: LocalDate,
    end: LocalDate,
    comparisonStart: LocalDate,
    comparisonEnd: LocalDate,
    contextLabel: String,
): InsightPeriodScope {
    check(!end.isBefore(start))
    check(ChronoUnit.DAYS.between(start, end) == ChronoUnit.DAYS.between(comparisonStart, comparisonEnd)) {
        "Insight comparison windows must contain the same number of days"
    }
    val current = document.flowBetween(start.toString(), end.toString())
    val previous = document.flowBetween(comparisonStart.toString(), comparisonEnd.toString())
    return InsightPeriodScope(
        id = id,
        label = label,
        startDate = start.toString(),
        endDate = end.toString(),
        contextLabel = contextLabel,
        comparison = InsightsComparison(
            currentLabel = insightsDateRangeLabel(start, end),
            previousLabel = insightsDateRangeLabel(comparisonStart, comparisonEnd),
            currentIncome = current.income,
            currentExpense = current.expense,
            previousIncome = previous.income,
            previousExpense = previous.expense,
        ),
        categories = insightCategories(document, start, end),
    )
}

/** Top five exact categories plus an explicit remainder so displayed shares cover the full denominator. */
internal fun insightCategories(
    document: CanonicalFinanceDocument,
    start: LocalDate,
    end: LocalDate,
): List<InsightCategory> {
    val all = document.categoryTotalsBetween(start.toString(), end.toString())
        .entries
        .filter { it.value > 0.005 }
        .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key.lowercase() })
    val total = all.sumOf { it.value }
    if (total <= 0.005) return emptyList()

    val visible = all.take(5).map { (name, amount) ->
        InsightCategory(name = name, amount = amount, share = (amount / total).toFloat())
    }.toMutableList()
    val remainder = all.drop(5)
    if (remainder.isNotEmpty()) {
        val amount = remainder.sumOf { it.value }
        visible += InsightCategory(
            name = "Λοιπά",
            amount = amount,
            share = (amount / total).toFloat(),
            sourceCategories = remainder.map { it.key },
        )
    }
    return visible
}

/**
 * Previous comparison interval for month-to-date. The baseline keeps exactly the same
 * inclusive day count as the current month interval. When the previous calendar month
 * is shorter (for example 1–31 May versus April), the baseline extends backward across
 * the prior month boundary instead of silently using a shorter denominator or crashing.
 */
internal fun equivalentPreviousMonthWindow(today: LocalDate): Pair<LocalDate, LocalDate> {
    val currentStart = YearMonth.from(today).atDay(1)
    val inclusiveSpanMinusOne = ChronoUnit.DAYS.between(currentStart, today)
    val previousMonth = YearMonth.from(today).minusMonths(1)
    val previousEnd = previousMonth.atDay(min(today.dayOfMonth, previousMonth.lengthOfMonth()))
    val previousStart = previousEnd.minusDays(inclusiveSpanMinusOne)
    return previousStart to previousEnd
}

private fun insightsMonthLabel(month: YearMonth): String = month.atDay(1)
    .format(DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("el-GR")))

private fun insightsDayMonthLabel(date: LocalDate): String = date
    .format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("el-GR")))

private fun insightsDateRangeLabel(start: LocalDate, end: LocalDate): String {
    val short = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("el-GR"))
    val full = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("el-GR"))
    return when {
        start == end -> start.format(full)
        start.year == end.year -> "${start.format(short)} – ${end.format(full)}"
        else -> "${start.format(full)} – ${end.format(full)}"
    }
}
