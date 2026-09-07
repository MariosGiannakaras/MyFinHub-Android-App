package app.myfinhub.android.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubHeroCard
import app.myfinhub.android.designsystem.MyFinHubHeroHeading
import app.myfinhub.android.designsystem.MyFinHubHeroMetric
import app.myfinhub.android.designsystem.MyFinHubHeroValue
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.financeToneColors
import java.text.NumberFormat
import java.util.Locale

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onOpenSupportingActivity: () -> Unit,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val latest = state.monthlyTrend.lastOrNull()
    val previous = state.monthlyTrend.dropLast(1).lastOrNull()
    val latestNet = latest?.let { it.income - it.expense } ?: 0.0
    val previousNet = previous?.let { it.income - it.expense }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Εικόνα",
                subtitle = "Τι αλλάζει και πού πηγαίνουν τα χρήματά σου",
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
                FinancialPulseCard(
                    latestMonth = latest?.label,
                    latestExpense = latest?.expense ?: 0.0,
                    previousExpense = previous?.expense,
                    latestNet = latestNet,
                    previousNet = previousNet,
                    savingsRate = state.savingsRate,
                    largeFont = largeFont,
                )
            }

            item { SectionTitle("Μηνιαία ροή") }
            if (state.monthlyTrend.isEmpty()) {
                item { EmptyInsightCard("Χρειάζονται περισσότερες κινήσεις για να εμφανιστεί μηνιαία πορεία.") }
            } else {
                items(state.monthlyTrend.takeLast(4), key = TrendPoint::label) { point ->
                    MonthlyTrendRow(point)
                }
            }

            item { SectionTitle("Κορυφαίες κατηγορίες") }
            if (state.categories.isEmpty()) {
                item { EmptyInsightCard("Δεν υπάρχουν ακόμη κατηγοριοποιημένα έξοδα για αυτόν τον μήνα.") }
            } else {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                            state.categories.take(5).forEach { category ->
                                CategoryRow(category = category, largeFont = largeFont)
                            }
                            TextButton(onClick = onOpenSupportingActivity) {
                                Text("Προβολή σχετικών κινήσεων")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinancialPulseCard(
    latestMonth: String?,
    latestExpense: Double,
    previousExpense: Double?,
    latestNet: Double,
    previousNet: Double?,
    savingsRate: Int,
    largeFont: Boolean,
) {
    val expenseDelta = previousExpense?.takeIf { kotlin.math.abs(it) > 0.005 }?.let { ((latestExpense - it) / kotlin.math.abs(it)) * 100.0 }
    val netDelta = previousNet?.let { latestNet - it }
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Τι άλλαξε", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            latestMonth?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (largeFont) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    InsightComparisonMetric("Έξοδα", formatEuro(latestExpense), expenseDelta?.let { "${if (it >= 0) "+" else ""}${it.toInt()}% από πριν" } ?: "Χωρίς βάση")
                    InsightComparisonMetric("Μεταβολή καθαρής ροής", netDelta?.let(::formatEuro) ?: "—", "έναντι προηγούμενου μήνα")
                    InsightComparisonMetric("Ρυθμός αποταμίευσης", "$savingsRate%", "τρέχων μήνας")
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    InsightComparisonMetric("Έξοδα", formatEuro(latestExpense), expenseDelta?.let { "${if (it >= 0) "+" else ""}${it.toInt()}%" } ?: "—", Modifier.weight(1f))
                    InsightComparisonMetric("Διαφορά ροής", netDelta?.let(::formatEuro) ?: "—", "από πριν", Modifier.weight(1f))
                }
                InsightComparisonMetric("Ρυθμός αποταμίευσης", "$savingsRate%", "τρέχων μήνας")
            }
        }
    }
}

@Composable
private fun InsightComparisonMetric(
    label: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PulseMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: FinanceTone,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
        MyFinHubIconBadge(icon = icon, tone = tone, contentDescription = null)
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MyFinHubAmountText(value, tone, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MonthlyTrendRow(point: TrendPoint) {
    val ratio = if (point.income <= 0.0) {
        if (point.expense > 0.0) 1f else 0f
    } else {
        (point.expense / point.income).toFloat().coerceIn(0f, 1f)
    }
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(point.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                MyFinHubAmountText(
                    text = formatEuro(point.income - point.expense),
                    tone = if (point.income >= point.expense) FinanceTone.Income else FinanceTone.Expense,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Έσοδα ${formatEuro(point.income)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Έξοδα ${formatEuro(point.expense)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth(),
                color = financeToneColors(FinanceTone.Expense).accent,
                trackColor = financeToneColors(FinanceTone.Expense).container,
            )
        }
    }
}

@Composable
private fun CategoryRow(category: InsightCategory, largeFont: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
        if (largeFont) {
            Column {
                Text(category.name, style = MaterialTheme.typography.titleMedium)
                MyFinHubAmountText(formatEuro(category.amount), FinanceTone.Expense)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category.name, style = MaterialTheme.typography.titleMedium)
                MyFinHubAmountText(formatEuro(category.amount), FinanceTone.Expense)
            }
        }
        LinearProgressIndicator(
            progress = { category.share.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = financeToneColors(FinanceTone.Expense).accent,
            trackColor = financeToneColors(FinanceTone.Expense).container,
        )
    }
}

@Composable
private fun EmptyInsightCard(message: String) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = MyFinHubSpacing.xs).semantics { heading() },
    )
}

private fun formatEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
