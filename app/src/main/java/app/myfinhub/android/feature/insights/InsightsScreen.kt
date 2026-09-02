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
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Αναλύσεις",
                subtitle = "Τάσεις και οικονομική πρόοδος",
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("insights_list"),
            contentPadding = PaddingValues(
                start = MyFinHubSpacing.lg,
                end = MyFinHubSpacing.lg,
                top = MyFinHubSpacing.xs,
                bottom = MyFinHubSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            item { SectionTitle("Σύνοψη") }
            item {
                if (largeFont) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                    ) {
                        SummaryCard(
                            label = "Μέση δαπάνη",
                            value = formatEuro(state.averageMonthlySpend),
                            icon = MyFinHubIcons.Expense,
                            tone = FinanceTone.Expense,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SummaryCard(
                            label = "Ρυθμός αποταμίευσης",
                            value = "${state.savingsRate}%",
                            icon = MyFinHubIcons.Savings,
                            tone = FinanceTone.Savings,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                    ) {
                        SummaryCard(
                            label = "Μέση δαπάνη",
                            value = formatEuro(state.averageMonthlySpend),
                            icon = MyFinHubIcons.Expense,
                            tone = FinanceTone.Expense,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryCard(
                            label = "Ρυθμός αποταμίευσης",
                            value = "${state.savingsRate}%",
                            icon = MyFinHubIcons.Savings,
                            tone = FinanceTone.Savings,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item { SectionTitle("Μηνιαία ροή") }
            items(state.monthlyTrend, key = TrendPoint::label) { point ->
                MonthlyTrendCard(point)
            }
            item {
                Text(
                    "Κείμενο γραφήματος: τον Αύγουστο οι synthetic δαπάνες είναι χαμηλότερες από τον Ιούλιο και ο ρυθμός αποταμίευσης είναι θετικός.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { SectionTitle("Κορυφαίες κατηγορίες") }
            items(state.categories, key = InsightCategory::name) { category ->
                CategoryCard(category = category, largeFont = largeFont)
            }

            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text(
                            "Οι αναλύσεις διαβάζουν τα ίδια FinanceEvent δεδομένα με τις Κινήσεις — χωρίς δεύτερο analytics store.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onOpenSupportingActivity) {
                            Text("Προβολή σχετικών κινήσεων")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: FinanceTone,
    modifier: Modifier,
) {
    MyFinHubSectionCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            MyFinHubIconBadge(icon = icon, tone = tone, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MyFinHubAmountText(
                text = value,
                tone = tone,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun MonthlyTrendCard(point: TrendPoint) {
    val ratio = (point.expense / point.income).toFloat().coerceIn(0f, 1f)
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text(point.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Έσοδα", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MyFinHubAmountText(formatEuro(point.income), FinanceTone.Income)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Έξοδα", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MyFinHubAmountText(formatEuro(point.expense), FinanceTone.Expense)
                }
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
private fun CategoryCard(category: InsightCategory, largeFont: Boolean) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            if (largeFont) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                progress = { category.share },
                modifier = Modifier.fillMaxWidth(),
                color = financeToneColors(FinanceTone.Expense).accent,
                trackColor = financeToneColors(FinanceTone.Expense).container,
            )
        }
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
