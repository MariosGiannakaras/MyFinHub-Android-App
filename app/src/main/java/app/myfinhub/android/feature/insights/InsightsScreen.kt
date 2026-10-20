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
import app.myfinhub.android.feature.utilities.amountVisibilityText
import app.myfinhub.android.feature.utilities.rememberAmountVisibilityPreference
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
    val amountsVisible = rememberAmountVisibilityPreference()
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
                SpendingComparisonCard(scope = scope, largeFont = largeFont, amountsVisible = amountsVisible)
            }
            if (scope.monthlyReportSignals != null) {
                item("monthly-report-signals") {
                    MonthlyReportSignalsCard(scope.monthlyReportSignals, amountsVisible)
                }
            }
            if (scope.primaryAccountSeries != null) {
                item("primary-account-series") {
                    PrimaryAccountSeriesCard(scope.primaryAccountSeries, amountsVisible)
                }
            }
            item("categories") {
                CategoryCompositionCard(
                    scope = scope,
                    onOpenCategoryActivity = onOpenCategoryActivity,
                    amountsVisible = amountsVisible,
                )
            }
            if (scope.counterparties.isNotEmpty()) {
                item("counterparties") {
                    MyFinHubSectionCard(
                        modifier = Modifier.fillMaxWidth().testTag("insights_counterparties"),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                            Text(
                                "Μεγαλύτερες δαπάνες ανά περιγραφή",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Ανά περιγραφή και κατηγορία · τρέχων μήνας",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            scope.counterparties.forEachIndexed { index, row ->
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("insights_counterparty_" + index),
                                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                                ) {
                                    Text(row.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        row.category + " · " + row.count + " κινήσεις · " +
                                            "τελευταία " + row.lastDateIso,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        amountVisibilityText(formatEuro(row.amount), amountsVisible) +
                                            (row.share?.let {
                                                " · " + (it * 100.0).roundToInt() + "%"
                                            } ?: ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                if (index < scope.counterparties.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (scope.categoryMomentum.isNotEmpty()) {
                item("category-momentum") {
                    MyFinHubSectionCard(
                        modifier = Modifier.fillMaxWidth()
                            .testTag("insights_category_momentum"),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                            Text(
                                "Μεταβολές κατηγοριών",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Τρέχων μήνας έναντι ολόκληρου προηγούμενου · ανά υποκατηγορία",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            scope.categoryMomentum.forEachIndexed { index, row ->
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("insights_momentum_row_" + index),
                                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                                ) {
                                    Text(row.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Τώρα: " +
                                            amountVisibilityText(
                                                formatEuro(row.currentAmount), amountsVisible,
                                            ) + " · προηγούμενος: " +
                                            amountVisibilityText(
                                                formatEuro(row.previousAmount), amountsVisible,
                                            ),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        row.relativeChange?.let { delta ->
                                            (if (delta > 0.0) "+" else "") +
                                                (delta * 100.0).roundToInt() + "%"
                                        } ?: "Νέα βάση",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("insights_momentum_change_" + index),
                                    )
                                }
                                if (index < scope.categoryMomentum.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (scope.loanBurden != null && scope.loanBurden.count > 0) {
                item("loan-burden") {
                    MyFinHubSectionCard(
                        modifier = Modifier.fillMaxWidth().testTag("insights_loan_burden"),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                            Text(
                                "Επόμενες δόσεις δανείων",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Ενεργά μακροχρόνια δάνεια · συνολική επόμενη δόση: " +
                                    amountVisibilityText(
                                        formatEuro(scope.loanBurden.total), amountsVisible,
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("insights_loan_burden_total"),
                            )
                            scope.loanBurden.rows.forEachIndexed { index, row ->
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("insights_loan_burden_row_" + index),
                                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                                ) {
                                    Text(row.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Επόμενη: " +
                                            amountVisibilityText(
                                                formatEuro(row.nextAmount), amountsVisible,
                                            ) + " · Υπόλοιπο: " +
                                            amountVisibilityText(
                                                formatEuro(row.outstanding), amountsVisible,
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        "Εναπομείνασες δόσεις: " + row.remainingInstallments,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item("details") {
                AnalysisDetailsCard(
                    scope = scope,
                    points = state.monthlyTrend.takeLast(6),
                    averageMonthlySpend = state.averageMonthlySpend,
                    expanded = detailsExpanded,
                    onToggle = { detailsExpanded = !detailsExpanded },
                    amountsVisible = amountsVisible,
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
private fun SpendingComparisonCard(scope: InsightPeriodScope, largeFont: Boolean, amountsVisible: Boolean) {
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
                amountVisibilityText(formatEuro(comparison.currentExpense), amountsVisible),
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
                    ComparisonFact("Προηγούμενα έξοδα", amountVisibilityText(formatEuro(comparison.previousExpense), amountsVisible))
                    ComparisonFact("Έσοδα περιόδου", amountVisibilityText(formatEuro(comparison.currentIncome), amountsVisible))
                    ComparisonFact("Καθαρό αποτέλεσμα", amountVisibilityText(formatSignedEuro(comparison.currentNet), amountsVisible))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                ) {
                    ComparisonFact("Προηγούμενα", amountVisibilityText(formatEuro(comparison.previousExpense), amountsVisible), Modifier.weight(1f))
                    ComparisonFact("Έσοδα", amountVisibilityText(formatEuro(comparison.currentIncome), amountsVisible), Modifier.weight(1f))
                    ComparisonFact("Καθαρό", amountVisibilityText(formatSignedEuro(comparison.currentNet), amountsVisible), Modifier.weight(1f))
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

/** Ratios follow Web reportInsightModel; money is always covered by amount privacy. */
@Composable
private fun MonthlyReportSignalsCard(
    signals: InsightMonthlyReportSignals,
    amountsVisible: Boolean,
) {
    fun percentage(value: Double?): String =
        value?.let { (if (it > 0.0) "+" else "") + (it * 100.0).roundToInt() + "%" }
            ?: "Χωρίς προηγούμενη βάση"

    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth().testTag("insights_monthly_report_signals")) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Δείκτες μήνα", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
            Text("Web Reports · ο τρέχων μήνας μπορεί να είναι μερικός",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ComparisonFact(
                "Καθαρή ροή εσόδων − εξόδων",
                amountVisibilityText(formatSignedEuro(signals.netFlow), amountsVisible),
                Modifier.testTag("insights_report_net_flow"),
            )
            ComparisonFact(
                "Ρυθμός αποταμίευσης · προηγούμενος",
                percentage(signals.savingsRate) + " · " + percentage(signals.previousSavingsRate),
                Modifier.testTag("insights_report_savings_rate"),
            )
            ComparisonFact(
                "Μεταβολή εσόδων / εξόδων / αποταμίευσης",
                percentage(signals.incomeChange) + " / " +
                    percentage(signals.expenseChange) + " / " +
                    percentage(signals.savingChange),
            )
            ComparisonFact(
                "Μέσος όρος εξόδων 3 προηγούμενων μηνών",
                amountVisibilityText(
                    formatEuro(signals.trailingExpenseAverage), amountsVisible,
                ) + " · " +
                    (if (signals.sufficientExpenseHistory) {
                        percentage(signals.expenseVsTrailingAverage)
                    } else "Χωρίς ιστορικό εξόδων"),
                Modifier.testTag("insights_report_trailing_expense"),
            )
            ComparisonFact(
                "Μηνιαία ισοδύναμα πάγια · επιβάρυνση εσόδων",
                amountVisibilityText(formatEuro(signals.recurringMonthlyTotal), amountsVisible) +
                    " · " + percentage(signals.recurringBurden),
                Modifier.testTag("insights_report_recurring_burden"),
            )
            if (signals.topCategoryName != null && signals.topCategoryAmount != null) {
                ComparisonFact(
                    "Μεγαλύτερη υποκατηγορία · " + signals.topCategoryName,
                    amountVisibilityText(formatEuro(signals.topCategoryAmount), amountsVisible) +
                        " · " + percentage(signals.topCategoryShare) +
                        " · μεταβολή " + percentage(signals.topCategoryChange),
                    Modifier.testTag("insights_report_top_category"),
                )
            }
        }
    }
}

@Composable
private fun PrimaryAccountSeriesCard(
    series: InsightsPrimaryAccountSeries,
    amountsVisible: Boolean,
) {
    MyFinHubSectionCard(
        modifier = Modifier.fillMaxWidth().testTag("insights_primary_account_series"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text(
                "Εξέλιξη μισθοδοσίας και αποταμίευσης",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Υπόλοιπα στο τέλος κάθε ημερολογιακού μήνα · τρέχων έως σήμερα",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            series.months.forEachIndexed { index, month ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .testTag("insights_primary_account_month_" + index),
                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                ) {
                    Text(
                        month.monthIso + " · έως " + month.cutoffDateIso,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (month.payrollBalance != null) {
                        Text(
                            (series.payrollLabel ?: "Μισθοδοσία") + ": " +
                                amountVisibilityText(formatEuro(month.payrollBalance), amountsVisible),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (month.savingsBalance != null) {
                        Text(
                            (series.savingsLabel ?: "Αποταμίευση") + ": " +
                                amountVisibilityText(formatEuro(month.savingsBalance), amountsVisible),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (index < series.months.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun CategoryCompositionCard(
    scope: InsightPeriodScope,
    onOpenCategoryActivity: (InsightCategory, String, String) -> Unit,
    amountsVisible: Boolean,
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
                        amountsVisible = amountsVisible,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: InsightCategory, onOpen: () -> Unit, amountsVisible: Boolean) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val description = "${category.name}, ${amountVisibilityText(formatEuro(category.amount), amountsVisible)}, ${(category.share * 100).roundToInt()}% των εξόδων"
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
                    MyFinHubAmountText(amountVisibilityText(formatEuro(category.amount), amountsVisible), FinanceTone.Expense)
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
                    MyFinHubAmountText(amountVisibilityText(formatEuro(category.amount), amountsVisible), FinanceTone.Expense)
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
    amountsVisible: Boolean,
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
                ComparisonFact("Έσοδα περιόδου", amountVisibilityText(formatEuro(scope.comparison.currentIncome), amountsVisible))
                ComparisonFact("Καθαρό αποτέλεσμα", amountVisibilityText(formatSignedEuro(scope.comparison.currentNet), amountsVisible))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MonthlyFlowChart(points = points, amountsVisible = amountsVisible)
                val partial = points.lastOrNull { it.isPartial }
                partial?.periodDetail?.let { detail ->
                    Text(
                        "* ${partial.label}: $detail. Οι προηγούμενοι μήνες είναι πλήρεις.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Μέσο έξοδο πλήρων μηνών ${amountVisibilityText(formatEuro(averageMonthlySpend), amountsVisible)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Εξαμηνιαία σωρευτική ροή · έσοδα μείον έξοδα, χωρίς διπλή προσθήκη αποταμίευσης",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier.testTag("insights_chart_text_alternative"),
                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                ) {
                    points.forEachIndexed { index, point ->
                        val month = point.monthIso.ifBlank { point.label }
                        Text(
                            "$month: έσοδα ${amountVisibilityText(formatEuro(point.income), amountsVisible)}" +
                                " · έξοδα ${amountVisibilityText(formatEuro(point.expense), amountsVisible)}" +
                                " · αποταμίευση ${amountVisibilityText(formatEuro(point.saving), amountsVisible)}" +
                                " · σωρευτικά ${amountVisibilityText(formatSignedEuro(point.cumulativeNet), amountsVisible)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("insights_six_month_row_" + index),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyFlowChart(points: List<TrendPoint>, amountsVisible: Boolean) {
    if (points.isEmpty()) {
        Text("Δεν υπάρχουν αρκετές κινήσεις για μηνιαία πορεία.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val maxValue = points.maxOfOrNull { maxOf(it.income, it.expense) }?.takeIf { it > .005 } ?: 1.0
    val incomeColor = financeToneColors(FinanceTone.Income).accent
    val expenseColor = financeToneColors(FinanceTone.Expense).accent
    val description = points.joinToString(". ") { point ->
        val period = point.periodDetail?.let { ", $it" }.orEmpty()
        "${point.label}$period: έσοδα ${amountVisibilityText(formatEuro(point.income), amountsVisible)}, έξοδα ${amountVisibilityText(formatEuro(point.expense), amountsVisible)}"
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
                    // Six full currency labels would overlap on a compact S24
                    // screen; all values remain in the accessible monthly text.
                    if (points.size <= 4) {
                        MyFinHubAmountText(
                            text = amountVisibilityText(formatSignedEuro(point.income - point.expense), amountsVisible),
                            tone = if (point.income >= point.expense) FinanceTone.Income else FinanceTone.Expense,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
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
