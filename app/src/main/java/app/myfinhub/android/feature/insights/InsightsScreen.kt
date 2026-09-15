package app.myfinhub.android.feature.insights

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
    initialDetailsExpanded: Boolean = false,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val scope = state.periodScope(selectedPeriodId)
    var detailsExpanded by rememberSaveable { mutableStateOf(initialDetailsExpanded) }

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
