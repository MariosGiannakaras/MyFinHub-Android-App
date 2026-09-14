from __future__ import annotations

import json
from pathlib import Path

ROOT = Path.cwd()


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content)


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if text.count(old) != 1:
        raise SystemExit(f"{path}: expected one match for {old[:100]!r}, found {text.count(old)}")
    write(path, text.replace(old, new, 1))


write(
    'app/src/main/java/app/myfinhub/android/feature/insights/InsightsUiState.kt',
    r'''package app.myfinhub.android.feature.insights

import androidx.lifecycle.ViewModel
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val INSIGHTS_PERIOD_MONTH = "month_to_date"
const val INSIGHTS_PERIOD_30_DAYS = "last_30_days"
const val INSIGHTS_PERIOD_90_DAYS = "last_90_days"

data class TrendPoint(
    val label: String,
    val income: Double,
    val expense: Double,
    val isPartial: Boolean = false,
    val periodDetail: String? = null,
)

data class InsightCategory(
    val name: String,
    val amount: Double,
    val share: Float,
    /** Exact canonical categories represented by this row. Usually one; "Λοιπά" may contain several. */
    val sourceCategories: List<String> = listOf(name),
)

data class InsightsComparison(
    val currentLabel: String,
    val previousLabel: String,
    val currentIncome: Double,
    val currentExpense: Double,
    val previousIncome: Double,
    val previousExpense: Double,
) {
    val currentNet: Double get() = currentIncome - currentExpense
    val previousNet: Double get() = previousIncome - previousExpense
    val expenseChangePercent: Double?
        get() = previousExpense.takeIf { abs(it) > 0.005 }?.let { base ->
            ((currentExpense - base) / abs(base)) * 100.0
        }
}

data class InsightPeriodScope(
    val id: String,
    val label: String,
    val startDate: String,
    val endDate: String,
    val contextLabel: String,
    val comparison: InsightsComparison,
    val categories: List<InsightCategory>,
)

data class InsightsUiState(
    val monthlyTrend: List<TrendPoint> = syntheticTrend(),
    val categories: List<InsightCategory> = syntheticCategories(),
    val averageMonthlySpend: Double = 1_040.0,
    val comparison: InsightsComparison = syntheticComparison(),
    val categoryStartDate: String = "2026-08-01",
    val categoryEndDate: String = "2026-08-31",
    val periods: List<InsightPeriodScope> = emptyList(),
) {
    val defaultPeriodId: String
        get() = periods.firstOrNull()?.id ?: INSIGHTS_PERIOD_MONTH

    fun periodScope(id: String): InsightPeriodScope = periods.firstOrNull { it.id == id }
        ?: periods.firstOrNull()
        ?: InsightPeriodScope(
            id = INSIGHTS_PERIOD_MONTH,
            label = "Μήνας",
            startDate = categoryStartDate,
            endDate = categoryEndDate,
            contextLabel = comparison.currentLabel,
            comparison = comparison,
            categories = categories,
        )
}

class InsightsViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = mutableState.asStateFlow()
}

fun syntheticTrend() = listOf(
    TrendPoint("Ιουν", 1_840.0, 980.0),
    TrendPoint("Ιουλ", 1_920.0, 1_260.0),
    TrendPoint("Αυγ", 1_840.0, 910.0),
    TrendPoint("Σεπ", 920.0, 455.0, isPartial = true, periodDetail = "έως 10 Σεπ"),
)

fun syntheticCategories() = listOf(
    InsightCategory("Στέγαση", 340.0, 0.42f),
    InsightCategory("Τρόφιμα", 124.0, 0.15f),
    InsightCategory("Μετακινήσεις", 71.0, 0.09f),
    InsightCategory("Έξοδος", 59.0, 0.07f),
    InsightCategory("Λοιπά", 219.0, 0.27f, listOf("Υγεία", "Αγορές", "Άλλο")),
)

fun syntheticComparison() = InsightsComparison(
    currentLabel = "1–10 Σεπ 2026",
    previousLabel = "1–10 Αυγ 2026",
    currentIncome = 920.0,
    currentExpense = 455.0,
    previousIncome = 920.0,
    previousExpense = 510.0,
)
''',
)

write(
    'app/src/main/java/app/myfinhub/android/app/CanonicalInsightsProjection.kt',
    r'''package app.myfinhub.android.app

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
    val comparisonDay = min(today.dayOfMonth, previousMonth.lengthOfMonth())
    val previousMonthStart = previousMonth.atDay(1)
    val previousMonthEnd = previousMonth.atDay(comparisonDay)

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
''',
)

write(
    'app/src/main/java/app/myfinhub/android/feature/insights/InsightsScreen.kt',
    r'''package app.myfinhub.android.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.financeToneColors
import app.myfinhub.android.designsystem.myFinHubCategoryIcon
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    selectedPeriodId: String = state.defaultPeriodId,
    onPeriodSelected: (String) -> Unit = {},
    onOpenSupportingActivity: () -> Unit,
    onOpenCategoryActivity: (InsightCategory, String, String) -> Unit = { _, _, _ -> onOpenSupportingActivity() },
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val scope = state.periodScope(selectedPeriodId)
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Ανάλυση",
                subtitle = "Σύγκριση ίδιων περιόδων και σύνθεση εξόδων",
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("insights_list"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = MyFinHubSpacing.xs,
                bottom = MyFinHubSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            item("period-selector") {
                PeriodSelector(
                    periods = state.periods.ifEmpty { listOf(scope) },
                    selectedPeriodId = scope.id,
                    onPeriodSelected = onPeriodSelected,
                )
            }
            item("spending-comparison") {
                SpendingComparisonCard(scope = scope, largeFont = largeFont)
            }
            item("categories") {
                CategoryCompositionCard(
                    scope = scope,
                    onOpenCategoryActivity = onOpenCategoryActivity,
                )
            }
            item("details") {
                AnalysisDetailsCard(
                    scope = scope,
                    points = state.monthlyTrend.takeLast(4),
                    averageMonthlySpend = state.averageMonthlySpend,
                    expanded = detailsExpanded,
                    onToggle = { detailsExpanded = !detailsExpanded },
                )
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    periods: List<InsightPeriodScope>,
    selectedPeriodId: String,
    onPeriodSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
        Text("Περίοδος", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().testTag("insights_period_selector"),
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        ) {
            periods.forEach { period ->
                FilterChip(
                    selected = period.id == selectedPeriodId,
                    onClick = { onPeriodSelected(period.id) },
                    label = { Text(period.label) },
                    modifier = Modifier.weight(1f).testTag("insights_period_${period.id}"),
                )
            }
        }
    }
}

@Composable
private fun SpendingComparisonCard(scope: InsightPeriodScope, largeFont: Boolean) {
    val comparison = scope.comparison
    val expenseChange = comparison.expenseChangePercent
    val headline = when {
        expenseChange == null -> "Δεν υπάρχει προηγούμενη βάση εξόδων"
        expenseChange < -0.5 -> "${abs(expenseChange).roundToInt()}% λιγότερα έξοδα"
        expenseChange > 0.5 -> "${expenseChange.roundToInt()}% περισσότερα έξοδα"
        else -> "Σχεδόν ίδια έξοδα"
    }

    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth().testTag("insights_spending_comparison")) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Έξοδα περιόδου", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MyFinHubAmountText(
                formatEuro(comparison.currentExpense),
                FinanceTone.Expense,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                scope.contextLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${comparison.currentLabel} · σύγκριση με ${comparison.previousLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("insights_exact_comparison_dates"),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (largeFont) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    ComparisonFact("Προηγούμενα έξοδα", formatEuro(comparison.previousExpense))
                    ComparisonFact("Έσοδα περιόδου", formatEuro(comparison.currentIncome))
                    ComparisonFact("Καθαρό αποτέλεσμα", formatSignedEuro(comparison.currentNet))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                ) {
                    ComparisonFact("Προηγούμενα", formatEuro(comparison.previousExpense), Modifier.weight(1f))
                    ComparisonFact("Έσοδα", formatEuro(comparison.currentIncome), Modifier.weight(1f))
                    ComparisonFact("Καθαρό", formatSignedEuro(comparison.currentNet), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ComparisonFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CategoryCompositionCard(
    scope: InsightPeriodScope,
    onOpenCategoryActivity: (InsightCategory, String, String) -> Unit,
) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth().testTag("insights_categories")) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Πού πηγαίνουν τα έξοδα", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "${scope.comparison.currentLabel} · ποσοστό επί όλων των κατηγοριοποιημένων εξόδων",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (scope.categories.isEmpty()) {
                Text(
                    "Δεν υπάρχουν κατηγοριοποιημένα έξοδα σε αυτή την περίοδο.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                scope.categories.forEach { category ->
                    CategoryRow(
                        category = category,
                        onOpen = { onOpenCategoryActivity(category, scope.startDate, scope.endDate) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: InsightCategory, onOpen: () -> Unit) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val description = "${category.name}, ${formatEuro(category.amount)}, ${(category.share * 100).roundToInt()}% των εξόδων"
    Surface(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_category_${category.name}")
            .semantics(mergeDescendants = true) { contentDescription = description },
        color = MaterialTheme.colorScheme.background,
    ) {
        if (largeFont) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = MyFinHubSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MyFinHubIconBadge(
                        icon = myFinHubCategoryIcon(category.name, MyFinHubIcons.Expense),
                        tone = FinanceTone.Expense,
                        contentDescription = null,
                    )
                    Text(category.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MyFinHubAmountText(formatEuro(category.amount), FinanceTone.Expense)
                    Text(
                        "${(category.share * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CategoryShareBar(category.share)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = MyFinHubSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MyFinHubIconBadge(
                    icon = myFinHubCategoryIcon(category.name, MyFinHubIcons.Expense),
                    tone = FinanceTone.Expense,
                    contentDescription = null,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                    Text(category.name, style = MaterialTheme.typography.titleMedium)
                    CategoryShareBar(category.share)
                }
                Column(horizontalAlignment = Alignment.End) {
                    MyFinHubAmountText(formatEuro(category.amount), FinanceTone.Expense)
                    Text(
                        "${(category.share * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun CategoryShareBar(share: Float) {
    LinearProgressIndicator(
        progress = { share.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
        color = financeToneColors(FinanceTone.Expense).accent,
        trackColor = financeToneColors(FinanceTone.Expense).container,
    )
}

@Composable
private fun AnalysisDetailsCard(
    scope: InsightPeriodScope,
    points: List<TrendPoint>,
    averageMonthlySpend: Double,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth().testTag("insights_details")) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Έσοδα, καθαρό & πορεία", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Δευτερεύουσα ανάλυση", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onToggle, modifier = Modifier.testTag("insights_details_toggle")) {
                    Text(if (expanded) "Απόκρυψη" else "Προβολή")
                }
            }
            if (expanded) {
                ComparisonFact("Έσοδα περιόδου", formatEuro(scope.comparison.currentIncome))
                ComparisonFact("Καθαρό αποτέλεσμα", formatSignedEuro(scope.comparison.currentNet))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MonthlyFlowChart(points = points)
                val partial = points.lastOrNull { it.isPartial }
                partial?.periodDetail?.let { detail ->
                    Text(
                        "* ${partial.label}: $detail. Οι προηγούμενοι μήνες είναι πλήρεις.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Μέσο έξοδο πλήρων μηνών ${formatEuro(averageMonthlySpend)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    points.joinToString(" · ") { point ->
                        "${point.label}: έσοδα ${formatEuro(point.income)}, έξοδα ${formatEuro(point.expense)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("insights_chart_text_alternative"),
                )
            }
        }
    }
}

@Composable
private fun MonthlyFlowChart(points: List<TrendPoint>) {
    if (points.isEmpty()) {
        Text("Δεν υπάρχουν αρκετές κινήσεις για μηνιαία πορεία.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val maxValue = points.maxOfOrNull { maxOf(it.income, it.expense) }?.takeIf { it > .005 } ?: 1.0
    val incomeColor = financeToneColors(FinanceTone.Income).accent
    val expenseColor = financeToneColors(FinanceTone.Expense).accent
    val description = points.joinToString(". ") { point ->
        val period = point.periodDetail?.let { ", $it" }.orEmpty()
        "${point.label}$period: έσοδα ${formatEuro(point.income)}, έξοδα ${formatEuro(point.expense)}"
    }
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendMark("Έσοδα", incomeColor)
            LegendMark("Έξοδα", expenseColor)
        }
        Row(
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEach { point ->
                val incomeHeight = (96f * (point.income / maxValue).toFloat()).coerceAtLeast(4f).dp
                val expenseHeight = (96f * (point.expense / maxValue).toFloat()).coerceAtLeast(4f).dp
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                ) {
                    Row(
                        modifier = Modifier.height(104.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Box(Modifier.width(12.dp).height(incomeHeight).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(incomeColor))
                        Box(Modifier.width(12.dp).height(expenseHeight).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(expenseColor))
                    }
                    Text(
                        text = point.label + if (point.isPartial) "*" else "",
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    MyFinHubAmountText(
                        text = formatSignedEuro(point.income - point.expense),
                        tone = if (point.income >= point.expense) FinanceTone.Income else FinanceTone.Expense,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendMark(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(14.dp).height(7.dp).clip(RoundedCornerShape(999.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

private fun formatSignedEuro(value: Double): String = when {
    value > .005 -> "+${formatEuro(value)}"
    value < -.005 -> formatEuro(value)
    else -> formatEuro(0.0)
}
''',
)

# Activity analytics scope supports one exact category or an explicit remainder set.
activity_path = 'app/src/main/java/app/myfinhub/android/feature/activity/ActivityUiState.kt'
replace_once(
    activity_path,
    '''    val categoryFilter: String? = null,
    val dateFrom: String? = null,''',
    '''    val categoryFilter: String? = null,
    /** Exact canonical category identities represented by an analytics row such as "Λοιπά". */
    val categoryFilterIds: Set<String>? = null,
    val dateFrom: String? = null,''',
)
replace_once(
    activity_path,
    '''    val isAnalyticsScope: Boolean = categoryFilter != null''',
    '''    val isAnalyticsScope: Boolean = categoryFilter != null || !categoryFilterIds.isNullOrEmpty()''',
)
replace_once(
    activity_path,
    '''        val exactCategory = if (isAnalyticsScope) categoryFilter else ledgerCategoryFilter
        val effectiveDateFrom = if (isAnalyticsScope) dateFrom else ledgerDateFrom''',
    '''        val exactCategory = if (isAnalyticsScope) categoryFilter else ledgerCategoryFilter
        val exactCategoryIds = if (isAnalyticsScope) categoryFilterIds else exactCategory?.let(::setOf)
        val effectiveDateFrom = if (isAnalyticsScope) dateFrom else ledgerDateFrom''',
)
replace_once(
    activity_path,
    '''            val matchesCategory = exactCategory == null ||
                (item.categoryContributions?.takeIf { it.isNotEmpty() }?.containsKey(exactCategory)
                    ?: ((item.category?.takeIf(String::isNotBlank) ?: "Άλλο") == exactCategory))''',
    '''            val matchesCategory = exactCategoryIds.isNullOrEmpty() ||
                (item.categoryContributions?.takeIf { it.isNotEmpty() }?.keys?.any(exactCategoryIds::contains)
                    ?: ((item.category?.takeIf(String::isNotBlank) ?: "Άλλο") in exactCategoryIds))''',
)
replace_once(
    activity_path,
    '''    /** Isolated read view: opening analysis never replaces the user's global ledger filters. */
    fun forCategory(category: String, start: String, end: String): ActivityUiState = copy(
        query = "",
        filter = ActivityFilter.ALL,
        accountFilterId = null,
        selectedId = null,
        ledgerCategoryFilter = null,
        ledgerDateFrom = null,
        ledgerDateTo = null,
        typeFilterId = null,
        categoryFilter = category,
        dateFrom = start,
        dateTo = end,
        items = items.map { item ->
            val contribution = item.categoryContributions?.get(category)
            if (contribution == null) item else item.copy(amount = -contribution)
        },
    )''',
    '''    /** Isolated read view: opening analysis never replaces the user's global ledger filters. */
    fun forCategory(category: String, start: String, end: String): ActivityUiState =
        forCategories(listOf(category), category, start, end)

    fun forCategories(categories: List<String>, label: String, start: String, end: String): ActivityUiState {
        val exact = categories.filter(String::isNotBlank).toSet()
        return copy(
            query = "",
            filter = ActivityFilter.ALL,
            accountFilterId = null,
            selectedId = null,
            ledgerCategoryFilter = null,
            ledgerDateFrom = null,
            ledgerDateTo = null,
            typeFilterId = null,
            categoryFilter = label,
            categoryFilterIds = exact,
            dateFrom = start,
            dateTo = end,
            items = items.map { item ->
                val contributions = item.categoryContributions
                if (contributions.isNullOrEmpty()) {
                    item
                } else {
                    val contribution = exact.sumOf { category -> contributions[category] ?: 0.0 }
                    if (kotlin.math.abs(contribution) <= 0.005) item else item.copy(amount = -contribution)
                }
            },
        )
    }''',
)

# Route keeps the display label plus exact source identities for remainder drill-down.
replace_once(
    'app/src/main/java/app/myfinhub/android/app/AppRoute.kt',
    '''    @Serializable data class CategoryActivity(val category: String, val start: String, val end: String) : AppRoute''',
    '''    @Serializable data class CategoryActivity(
        val category: String,
        val start: String,
        val end: String,
        val categories: List<String> = emptyList(),
    ) : AppRoute''',
)

# App-level selected period survives top-level tab switches; category route carries exact range and identities.
app_path = 'app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt'
replace_once(
    app_path,
    '''import app.myfinhub.android.feature.insights.InsightsScreen
import app.myfinhub.android.feature.insights.InsightsUiState''',
    '''import app.myfinhub.android.feature.insights.InsightsScreen
import app.myfinhub.android.feature.insights.InsightsUiState''',
)
replace_once(
    app_path,
    '''    var currentDestination by rememberSaveable { mutableStateOf(TopLevelDestination.HOME) }
    var walletAccountsRequest by rememberSaveable { mutableStateOf(0) }''',
    '''    var currentDestination by rememberSaveable { mutableStateOf(TopLevelDestination.HOME) }
    var walletAccountsRequest by rememberSaveable { mutableStateOf(0) }
    var insightsPeriodId by rememberSaveable { mutableStateOf(insightsState.defaultPeriodId) }''',
)
replace_once(
    app_path,
    '''                entry<AppRoute.CategoryActivity> { route ->
                    ActivityLedgerScreen(
                        state = activityState.forCategory(route.category, route.start, route.end),''',
    '''                entry<AppRoute.CategoryActivity> { route ->
                    ActivityLedgerScreen(
                        state = activityState.forCategories(
                            categories = route.categories.ifEmpty { listOf(route.category) },
                            label = route.category,
                            start = route.start,
                            end = route.end,
                        ),''',
)
replace_once(
    app_path,
    '''                        InsightsScreen(
                            state = insightsState,
                            onOpenSupportingActivity = { activityBackStack.popToRoot() },
                            onOpenCategoryActivity = { category ->
                                activityBackStack.pushIfNew(
                                    AppRoute.CategoryActivity(
                                        category = category,
                                        start = insightsState.categoryStartDate,
                                        end = insightsState.categoryEndDate,
                                    ),
                                )
                            },
                        )''',
    '''                        InsightsScreen(
                            state = insightsState,
                            selectedPeriodId = insightsPeriodId,
                            onPeriodSelected = { insightsPeriodId = it },
                            onOpenSupportingActivity = { activityBackStack.popToRoot() },
                            onOpenCategoryActivity = { category, start, end ->
                                activityBackStack.pushIfNew(
                                    AppRoute.CategoryActivity(
                                        category = category.name,
                                        start = start,
                                        end = end,
                                        categories = category.sourceCategories,
                                    ),
                                )
                            },
                        )''',
)

# Projection contracts: exact periods, complete denominator/remainder, honest zero baseline.
write(
    'app/src/test/java/app/myfinhub/android/app/CanonicalInsightsProjectionTest.kt',
    r'''package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_30_DAYS
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_MONTH
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalInsightsProjectionTest {
    @Test
    fun currentPartialMonth_comparesOnlyEquivalentPreviousPeriod() {
        val state = projectCanonicalInsightsState(comparisonFixture(), LocalDate.of(2026, 9, 10))
        val scope = state.periodScope(INSIGHTS_PERIOD_MONTH)

        assertEquals("1 Αυγ – 10 Αυγ 2026", scope.comparison.previousLabel)
        assertEquals("1 Σεπ – 10 Σεπ 2026", scope.comparison.currentLabel)
        assertEquals(120.0, scope.comparison.currentExpense, 0.001)
        assertEquals(100.0, scope.comparison.previousExpense, 0.001)
        assertEquals(20.0, scope.comparison.expenseChangePercent ?: 0.0, 0.001)
        assertTrue(scope.contextLabel.contains("Μερικός μήνας"))

        val august = state.monthlyTrend.first { it.label.startsWith("Αυγ", ignoreCase = true) }
        val september = state.monthlyTrend.last()
        assertEquals(1_000.0, august.expense, 0.001)
        assertTrue(september.isPartial)
        assertEquals("έως 10 Σεπ", september.periodDetail)
        assertEquals(120.0, september.expense, 0.001)
    }

    @Test
    fun thirtyDayComparison_hasExactlyEqualAdjacentWindowLength() {
        val state = projectCanonicalInsightsState(comparisonFixture(), LocalDate.of(2026, 9, 10))
        val scope = state.periodScope(INSIGHTS_PERIOD_30_DAYS)
        assertEquals("12 Αυγ – 10 Σεπ 2026", scope.comparison.currentLabel)
        assertEquals("13 Ιουλ – 11 Αυγ 2026", scope.comparison.previousLabel)
    }

    @Test
    fun topCategories_includeRemainderSoSharesCoverCompleteDenominator() {
        val categories = insightCategories(categoryFixture(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        assertEquals(6, categories.size)
        val other = categories.last()
        assertEquals("Λοιπά", other.name)
        assertEquals(setOf("Ζ", "Η"), other.sourceCategories.toSet())
        assertEquals(1.0, categories.sumOf { it.share.toDouble() }, 0.0001)
        assertEquals(30.0, other.amount, 0.001)
    }

    @Test
    fun zeroPreviousExpense_isUnavailablePercentageNotFakeZeroChange() {
        val state = projectCanonicalInsightsState(categoryFixture(), LocalDate.of(2026, 9, 10))
        assertNull(state.periodScope(INSIGHTS_PERIOD_MONTH).comparison.expenseChangePercent)
    }

    @Test
    fun averageMonthlySpend_excludesPartialCurrentMonth() {
        val state = projectCanonicalInsightsState(comparisonFixture(), LocalDate.of(2026, 9, 10))
        assertEquals(1_000.0 / 3.0, state.averageMonthlySpend, 0.001)
        assertEquals(120.0, state.periodScope(INSIGHTS_PERIOD_MONTH).categories.single { it.name == "Τρόφιμα" }.amount, 0.001)
    }
}

private fun comparisonFixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(
    Json.parseToJsonElement(
        """
        {
          "schemaVersion":3,
          "seed":{
            "accounts":[],"snapshots":[],
            "transactions":[
              {"id":"aug-early","date":"2026-08-05","type":"expense","amount":100.0,"category":"Τρόφιμα","note":"Πρώτο δεκαήμερο"},
              {"id":"aug-late","date":"2026-08-20","type":"expense","amount":900.0,"category":"Στέγαση","note":"Μετά το συγκρίσιμο διάστημα"},
              {"id":"sep-early","date":"2026-09-05","type":"expense","amount":120.0,"category":"Τρόφιμα","note":"Τρέχων μήνας"}
            ]
          },
          "state":{"deleted":[],"overrides":{},"customTransactions":[],"events":[],"settings":{}}
        }
        """.trimIndent(),
    ).jsonObject,
)

private fun categoryFixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(
    Json.parseToJsonElement(
        """
        {
          "schemaVersion":3,
          "seed":{
            "accounts":[],"snapshots":[],
            "transactions":[
              {"id":"a","date":"2026-09-02","type":"expense","amount":80.0,"category":"Α"},
              {"id":"b","date":"2026-09-03","type":"expense","amount":70.0,"category":"Β"},
              {"id":"c","date":"2026-09-04","type":"expense","amount":60.0,"category":"Γ"},
              {"id":"d","date":"2026-09-05","type":"expense","amount":50.0,"category":"Δ"},
              {"id":"e","date":"2026-09-06","type":"expense","amount":40.0,"category":"Ε"},
              {"id":"z","date":"2026-09-07","type":"expense","amount":20.0,"category":"Ζ"},
              {"id":"h","date":"2026-09-08","type":"expense","amount":10.0,"category":"Η"}
            ]
          },
          "state":{"deleted":[],"overrides":{},"customTransactions":[],"events":[],"settings":{}}
        }
        """.trimIndent(),
    ).jsonObject,
)
''',
)

write(
    'app/src/test/java/app/myfinhub/android/feature/activity/S8AnalyticsScopeTest.kt',
    r'''package app.myfinhub.android.feature.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class S8AnalyticsScopeTest {
    @Test
    fun remainderDrillDown_preservesExactCategorySetAndInterval() {
        val state = ActivityUiState(
            items = listOf(
                ActivityItem("split", "", ActivityKind.EXPENSE, "Split", "", -100.0, "A", "Ζ", rawDate = "2026-09-05", categoryContributions = mapOf("Ζ" to 20.0, "Η" to 30.0, "Α" to 50.0)),
                ActivityItem("outside", "", ActivityKind.EXPENSE, "Outside", "", -10.0, "A", "Ζ", rawDate = "2026-08-30", categoryContributions = mapOf("Ζ" to 10.0)),
            ),
        ).forCategories(listOf("Ζ", "Η"), "Λοιπά", "2026-09-01", "2026-09-30")

        assertEquals("Λοιπά", state.categoryFilter)
        assertEquals(setOf("Ζ", "Η"), state.categoryFilterIds)
        assertEquals(listOf("split"), state.visibleItems.map { it.id })
        assertEquals(-50.0, state.visibleItems.single().amount, 0.001)
        assertTrue(state.isAnalyticsScope)
    }
}
''',
)

write(
    'app/src/androidTest/java/app/myfinhub/android/feature/insights/S8InsightsSurfaceTest.kt',
    r'''package app.myfinhub.android.feature.insights

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class S8InsightsSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val month = InsightPeriodScope(
        id = INSIGHTS_PERIOD_MONTH,
        label = "Μήνας",
        startDate = "2026-09-01",
        endDate = "2026-09-10",
        contextLabel = "Μερικός μήνας · έως 10 Σεπ",
        comparison = InsightsComparison("1 Σεπ – 10 Σεπ 2026", "1 Αυγ – 10 Αυγ 2026", 900.0, 455.0, 900.0, 510.0),
        categories = listOf(InsightCategory("Τρόφιμα", 200.0, .44f)),
    )
    private val days30 = month.copy(id = INSIGHTS_PERIOD_30_DAYS, label = "30 ημ.", startDate = "2026-08-12")
    private val state = InsightsUiState(periods = listOf(month, days30), categories = month.categories, comparison = month.comparison)

    @Test
    fun periodSelector_changesRequestedStablePeriod() {
        var selected = INSIGHTS_PERIOD_MONTH
        composeRule.setContent {
            MyFinHubTheme {
                InsightsScreen(
                    state = state,
                    selectedPeriodId = selected,
                    onPeriodSelected = { selected = it },
                    onOpenSupportingActivity = {},
                )
            }
        }
        composeRule.onNodeWithTag("insights_period_${INSIGHTS_PERIOD_30_DAYS}").performClick()
        composeRule.runOnIdle { assertEquals(INSIGHTS_PERIOD_30_DAYS, selected) }
    }

    @Test
    fun categoryWholeRow_opensExactIdentityAndDateRange() {
        var result: Triple<InsightCategory, String, String>? = null
        composeRule.setContent {
            MyFinHubTheme {
                InsightsScreen(
                    state = state,
                    onOpenSupportingActivity = {},
                    onOpenCategoryActivity = { category, start, end -> result = Triple(category, start, end) },
                )
            }
        }
        composeRule.onNodeWithTag("insights_category_Τρόφιμα").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("Τρόφιμα", result?.first?.name)
            assertEquals("2026-09-01", result?.second)
            assertEquals("2026-09-10", result?.third)
        }
    }

    @Test
    fun secondaryIncomeNetTrend_isExpandableAndHasTextAlternative() {
        composeRule.setContent { MyFinHubTheme { InsightsScreen(state = state, onOpenSupportingActivity = {}) } }
        composeRule.onNodeWithTag("insights_details_toggle").performScrollTo().performClick()
        composeRule.onNodeWithText("Έσοδα περιόδου").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("insights_chart_text_alternative").performScrollTo().assertIsDisplayed()
    }
}
''',
)

# Fresh S8 render fixture exercises complete denominator and new hierarchy.
write(
    'app/src/screenshotTest/kotlin/app/myfinhub/android/feature/insights/InsightsScreenshotTest.kt',
    r'''package app.myfinhub.android.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private fun ownerInsightsScreenshotState(): InsightsUiState {
    val comparison = InsightsComparison(
        currentLabel = "1 Σεπ – 10 Σεπ 2026",
        previousLabel = "1 Αυγ – 10 Αυγ 2026",
        currentIncome = 920.0,
        currentExpense = 455.0,
        previousIncome = 920.0,
        previousExpense = 510.0,
    )
    val categories = listOf(
        InsightCategory("Στέγαση", 210.0, 0.46f),
        InsightCategory("Τρόφιμα", 125.0, 0.27f),
        InsightCategory("Μετακινήσεις", 70.0, 0.15f),
        InsightCategory("Λοιπά", 50.0, 0.11f, listOf("Έξοδος", "Υγεία", "Αγορές")),
    )
    return InsightsUiState(
        monthlyTrend = listOf(
            TrendPoint("Ιουν", 1_840.0, 980.0),
            TrendPoint("Ιουλ", 1_920.0, 1_260.0),
            TrendPoint("Αυγ", 1_840.0, 910.0),
            TrendPoint("Σεπ", 920.0, 455.0, isPartial = true, periodDetail = "έως 10 Σεπ"),
        ),
        categories = categories,
        averageMonthlySpend = 1_050.0,
        comparison = comparison,
        categoryStartDate = "2026-09-01",
        categoryEndDate = "2026-09-10",
        periods = listOf(
            InsightPeriodScope(INSIGHTS_PERIOD_MONTH, "Μήνας", "2026-09-01", "2026-09-10", "Μερικός μήνας · έως 10 Σεπ", comparison, categories),
            InsightPeriodScope(INSIGHTS_PERIOD_30_DAYS, "30 ημ.", "2026-08-12", "2026-09-10", "Κυλιόμενο διάστημα 30 ημερών", comparison, categories),
            InsightPeriodScope(INSIGHTS_PERIOD_90_DAYS, "90 ημ.", "2026-06-13", "2026-09-10", "Κυλιόμενο διάστημα 90 ημερών", comparison, categories),
        ),
    )
}

@PreviewTest
@Preview(name = "insights_compact_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun InsightsCompactLightScreenshot() = InsightsFixture(false)

@PreviewTest
@Preview(name = "insights_compact_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun InsightsCompactDarkScreenshot() = InsightsFixture(true)

@PreviewTest
@Preview(name = "insights_compact_large_font", widthDp = 412, heightDp = 1050, fontScale = 1.5f, showBackground = true)
@Composable
fun InsightsCompactLargeFontScreenshot() = InsightsFixture(false)

@PreviewTest
@Preview(name = "insights_full_large_font", widthDp = 412, heightDp = 1900, fontScale = 1.5f, showBackground = true)
@Composable
fun InsightsFullLargeFontScreenshot() = InsightsFixture(false)

@Composable
private fun InsightsFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        InsightsScreen(
            state = ownerInsightsScreenshotState(),
            onOpenSupportingActivity = {},
        )
    }
}
''',
)

# Tracking remains implementation-candidate only until rendered/device/PR evidence exists.
tracking_path = ROOT / 'tracking/android-project-state.json'
data = json.loads(tracking_path.read_text())
data['updated_at'] = '2026-09-14'
work = data['active_workstream']
work['status'] = 'android_redesign_s8_analysis_implementation'
work['summary'] = (
    'S1–S7 are complete and merged. S8 Analysis is the active Android-only slice, implementing explicit period '
    'selection, equivalent comparison windows, complete category denominator/remainder, exact scoped drill-down '
    'and secondary expandable income/net/trend detail. No S8 subtask is accepted until fresh renders and hosted gates pass.'
)
work['next'] = [
    'Validate the coherent S8 Analysis candidate with focused projection/UI tests and inspect fresh light/dark/150% Compose renders.',
    'After render acceptance, open the S8 draft PR and require exact-head Android CI, Project Tracking and Android UI Quality/S24-target instrumentation.',
]
current = data['current_redesign_pass']
current['branch'] = 'android/redesign-s8-analysis'
current['pr'] = None
current['current_slice'] = 'S8.1 / S8.2 / S8.3 / S8.4'
current['checkpoint'] = 's8_implementation_candidate'
current['blockers'] = []
current['next_action'] = (
    'Validate the coherent S8 Analysis candidate, personally inspect the fresh Analysis light/dark/150% renders, '
    'then publish a draft PR and complete S8 only from exact-head hosted evidence.'
)
tracking_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')

import subprocess
subprocess.run(['python3', 'scripts/render_project_tracking.py'], check=True)
