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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.financeToneColors
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onOpenSupportingActivity: () -> Unit,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val trend = state.monthlyTrend.takeLast(4)
    val latest = trend.lastOrNull()
    val previous = trend.dropLast(1).lastOrNull()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Εικόνα",
                subtitle = "Σύγκριση, πορεία και σύνθεση εξόδων",
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
            item {
                ComparisonStoryCard(
                    latest = latest,
                    previous = previous,
                    savingsRate = state.savingsRate,
                    largeFont = largeFont,
                )
            }
            item {
                MonthlyFlowChartCard(
                    points = trend,
                    averageMonthlySpend = state.averageMonthlySpend,
                )
            }
            item {
                CategoryCompositionCard(
                    categories = state.categories,
                    onOpenSupportingActivity = onOpenSupportingActivity,
                )
            }
        }
    }
}

@Composable
private fun ComparisonStoryCard(
    latest: TrendPoint?,
    previous: TrendPoint?,
    savingsRate: Int,
    largeFont: Boolean,
) {
    val expenseChange = previous?.expense?.takeIf { abs(it) > .005 }?.let { base ->
        ((latest?.expense.orZero() - base) / abs(base)) * 100.0
    }
    val latestNet = latest?.let { it.income - it.expense } ?: 0.0
    val previousNet = previous?.let { it.income - it.expense }
    val netChange = previousNet?.let { latestNet - it }
    val headline = when {
        latest == null -> "Δεν υπάρχει ακόμη αρκετό ιστορικό"
        expenseChange == null -> "${latest.label}: πρώτη βάση σύγκρισης"
        expenseChange < -0.5 -> "Τα έξοδα μειώθηκαν ${abs(expenseChange).roundToInt()}%"
        expenseChange > 0.5 -> "Τα έξοδα αυξήθηκαν ${expenseChange.roundToInt()}%"
        else -> "Τα έξοδα έμειναν σχεδόν σταθερά"
    }
    val supporting = if (latest != null && previous != null) {
        "${latest.label} σε σχέση με ${previous.label}"
    } else {
        "Η σύγκριση θα γίνει καθαρότερη όσο προστίθεται ιστορικό."
    }

    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Η αλλαγή που μετράει", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (largeFont) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    ComparisonFact("Έξοδα τελευταίου μήνα", latest?.expense?.let(::formatEuro) ?: "—")
                    ComparisonFact("Μεταβολή καθαρής ροής", netChange?.let(::formatSignedEuro) ?: "—")
                    ComparisonFact("Ρυθμός αποταμίευσης", "$savingsRate%")
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    ComparisonFact("Έξοδα", latest?.expense?.let(::formatEuro) ?: "—", Modifier.weight(1f))
                    ComparisonFact("Διαφορά ροής", netChange?.let(::formatSignedEuro) ?: "—", Modifier.weight(1f))
                    ComparisonFact("Αποταμίευση", "$savingsRate%", Modifier.weight(1f))
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
private fun MonthlyFlowChartCard(points: List<TrendPoint>, averageMonthlySpend: Double) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Πορεία 4 μηνών", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Έσοδα και έξοδα στην ίδια κλίμακα — όχι τέσσερις διαφορετικές κάρτες.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (points.isEmpty()) {
                Text("Χρειάζονται περισσότερες κινήσεις για να εμφανιστεί πορεία.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowLegend()
                FlowBars(points)
                Text(
                    "Μέσο μηνιαίο έξοδο ${formatEuro(averageMonthlySpend)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FlowLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        LegendMark("Έσοδα", financeToneColors(FinanceTone.Income).accent)
        LegendMark("Έξοδα", financeToneColors(FinanceTone.Expense).accent)
    }
}

@Composable
private fun LegendMark(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(7.dp).clip(RoundedCornerShape(999.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FlowBars(points: List<TrendPoint>) {
    val maxValue = points.maxOfOrNull { maxOf(it.income, it.expense) }?.takeIf { it > .005 } ?: 1.0
    val incomeColor = financeToneColors(FinanceTone.Income).accent
    val expenseColor = financeToneColors(FinanceTone.Expense).accent
    val description = points.joinToString(". ") { point ->
        "${point.label}: έσοδα ${formatEuro(point.income)}, έξοδα ${formatEuro(point.expense)}"
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
                Text(point.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
                MyFinHubAmountText(
                    text = formatSignedEuro(point.income - point.expense),
                    tone = if (point.income >= point.expense) FinanceTone.Income else FinanceTone.Expense,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun CategoryCompositionCard(
    categories: List<InsightCategory>,
    onOpenSupportingActivity: () -> Unit,
) {
    val top = categories.firstOrNull()
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Πού πηγαίνουν τα έξοδα", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (top == null) {
                Text("Δεν υπάρχουν ακόμη κατηγοριοποιημένα έξοδα για αυτόν τον μήνα.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(MyFinHubSpacing.sm)) {
                        Text("Μεγαλύτερη κατηγορία", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(top.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${formatEuro(top.amount)} · ${(top.share * 100).roundToInt()}% των κατηγοριοποιημένων εξόδων")
                    }
                }
                categories.take(5).forEach { category ->
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(category.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            MyFinHubAmountText(formatEuro(category.amount), FinanceTone.Expense)
                        }
                        LinearProgressIndicator(
                            progress = { category.share.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = financeToneColors(FinanceTone.Expense).accent,
                            trackColor = financeToneColors(FinanceTone.Expense).container,
                        )
                    }
                }
                TextButton(onClick = onOpenSupportingActivity) { Text("Δες τις κινήσεις") }
            }
        }
    }
}

private fun Double?.orZero(): Double = this ?: 0.0

private fun formatEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

private fun formatSignedEuro(value: Double): String =
    (if (value > .005) "+" else "") + formatEuro(value)
