package app.myfinhub.android.feature.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubActionCard
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSectionHeading
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.myFinHubCategoryIcon
import app.myfinhub.android.feature.utilities.HIDDEN_AMOUNT_TEXT
import app.myfinhub.android.feature.utilities.amountVisibilityText
import app.myfinhub.android.feature.utilities.rememberAmountVisibilityPreference
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class CanonicalBudgetProgress(
    val limit: Double,
    val spent: Double,
    val remaining: Double,
    val percent: Int,
    val progress: Float,
    val threshold: Int?,
    val thresholdReached: Boolean,
)

internal fun canonicalBudgetProgress(state: PlanUiState): CanonicalBudgetProgress? {
    val limit = mobileBudgetLimitOrNull(state.budget.monthlyLimitText) ?: return null
    if (!state.budgetSpent.isFinite()) return null
    val spent = state.budgetSpent.coerceAtLeast(0.0)
    val ratio = spent / limit
    if (!ratio.isFinite() || !(ratio * 100.0).isFinite()) return null
    val percent = (ratio * 100.0).roundToInt().coerceAtLeast(0)
    val threshold = state.budget.alertThresholdText.toIntOrNull()?.takeIf { it in 1..100 }
    return CanonicalBudgetProgress(
        limit = limit,
        spent = spent,
        remaining = limit - spent,
        percent = percent,
        progress = ratio.toFloat().coerceIn(0f, 1f),
        threshold = threshold,
        thresholdReached = threshold != null && percent >= threshold,
    )
}

internal fun plannedItemSourceKey(item: PlannedItem): String = "${item.kind.name}:${item.id}"

internal fun planUrgentObligations(state: PlanUiState): List<PlannedItem> = state.items.filter { item ->
    item.flow == PlannedFlow.OBLIGATION &&
        (item.urgency == PlannedUrgency.OVERDUE || item.urgency == PlannedUrgency.THIS_WEEK)
}

internal fun planRemainingItems(state: PlanUiState): List<PlannedItem> =
    // Do not discard an unrelated historical row with the same source ID when
    // a different row is urgent. Source IDs are not unique in old Web data.
    state.items.filterNot { item ->
        item.flow == PlannedFlow.OBLIGATION &&
            item.urgency in setOf(PlannedUrgency.OVERDUE, PlannedUrgency.THIS_WEEK)
    }

internal fun planForecastIncludedItems(state: PlanUiState): List<PlannedItem> {
    val end = state.forecastEndDateIso.toPlanS7DateOrNull() ?: return state.items.filter { it.dueDateIso.isNotBlank() }
    return state.items.filter { item ->
        item.dueDateIso.toPlanS7DateOrNull()?.let { due -> !due.isAfter(end) } == true
    }
}

/** Owner's Android Plan uses only the canonical 30-day window, not Web 60/90-day UI. */
internal fun mobilePlanForecastWindow(windows: List<ForecastWindow>): ForecastWindow? =
    windows.firstOrNull { it.days == 30 }

internal fun planForecastScopeLabel(state: PlanUiState): String {
    val start = state.forecastStartDateIso.toPlanS7DateOrNull()
    val end = state.forecastEndDateIso.toPlanS7DateOrNull()
    if (start == null || end == null) return "Σταθερός ορίζοντας ${state.forecastHorizonDays} ημερών"
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("el-GR"))
    return "${start.format(formatter)} – ${end.format(formatter)}"
}

/**
 * H7 production Plan: immediate obligations first, then exact 30-day forecast and monthly budget.
 * A canonical source is rendered once on this root surface; detail screens own explanation/actions.
 */
@Composable
fun CanonicalPlan2026Screen(
    state: PlanUiState,
    onOpenForecast: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenRecurring: () -> Unit = {},
    onOpenScheduled: () -> Unit = {},
    onOpenSavings: () -> Unit = {},
    onOpenLoans: () -> Unit = {},
    onOpenLending: () -> Unit = {},
) {
    val amountsVisible = rememberAmountVisibilityPreference()
    val urgent = planUrgentObligations(state)
    val remaining = planRemainingItems(state)

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Πλάνο",
                subtitle = "Τι χρειάζεται προσοχή και τι ακολουθεί",
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("s7_plan_root"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = padding.calculateTopPadding() + MyFinHubSpacing.xs,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = MyFinHubDesignMetrics.productSnackbarBottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
        ) {
            if (urgent.isNotEmpty()) {
                item("urgent-heading") {
                    MyFinHubSectionHeading(
                        title = "Πρώτα",
                        subtitle = "Καθυστερημένες και κοντινές υποχρεώσεις",
                        icon = MyFinHubIcons.Attention,
                        tone = FinanceTone.Expense,
                    )
                }
                itemsIndexed(urgent, key = { index, item ->
                    "urgent-${plannedItemSourceKey(item)}-row-$index"
                }) { _, item ->
                    PlanS7Row(item = item, amountsVisible = amountsVisible, glyphOverrides = state.categoryGlyphOverrides)
                }
            }

            item("forecast-link") {
                ForecastS7LinkCard(state = state, amountsVisible = amountsVisible, onClick = onOpenForecast)
            }
            item("budget-link") {
                BudgetS7LinkCard(state = state, amountsVisible = amountsVisible, onClick = onOpenBudget)
            }
            item("savings-link") {
                MyFinHubActionCard(
                    onClick = onOpenSavings,
                    modifier = Modifier.fillMaxWidth().testTag("s7_savings_link"),
                ) {
                    MyFinHubSectionHeading(
                        title = "Στόχοι αποταμίευσης",
                        subtitle = "Υπόλοιπα, πρόοδος και διαχείριση στόχου",
                        icon = MyFinHubIcons.Savings,
                        tone = FinanceTone.Savings,
                    )
                }
            }
            item("loans-link") {
                MyFinHubActionCard(
                    onClick = onOpenLoans,
                    modifier = Modifier.fillMaxWidth().testTag("s7_loans_link"),
                ) {
                    MyFinHubSectionHeading(
                        title = "Δάνεια και οφειλές",
                        subtitle = "Υπόλοιπα, δόσεις και αποπληρωμές",
                        icon = MyFinHubIcons.Account,
                        tone = FinanceTone.Neutral,
                    )
                }
            }
            item("lending-link") {
                MyFinHubActionCard(
                    onClick = onOpenLending,
                    modifier = Modifier.fillMaxWidth().testTag("s7_lending_link"),
                ) {
                    MyFinHubSectionHeading(
                        title = "Χρήματα που μου οφείλουν",
                        subtitle = "Απαιτήσεις και επιστροφές",
                        icon = MyFinHubIcons.Money,
                        tone = FinanceTone.Neutral,
                    )
                }
            }
            item("recurring-management-link") {
                MyFinHubActionCard(
                    onClick = onOpenRecurring,
                    modifier = Modifier.fillMaxWidth().testTag("s7_recurring_management_link"),
                ) {
                    MyFinHubSectionHeading(
                        title = "Πάγια & συνδρομές",
                        subtitle = "Προβολή επαναλαμβανόμενων χρεώσεων",
                        icon = MyFinHubIcons.Plan,
                        tone = FinanceTone.Neutral,
                    )
                }
            }

            // Entry is always available: a first planned payment must be reachable.
            item("scheduled-management") {
                    MyFinHubActionCard(
                        onClick = onOpenScheduled,
                        modifier = Modifier.fillMaxWidth().testTag("s7_scheduled_management_link"),
                    ) {
                        MyFinHubSectionHeading(
                            title = "Προγραμματισμένες κινήσεις",
                            subtitle = if (state.scheduledManageItems.isEmpty())
                                "Νέος προγραμματισμός και ιστορικό"
                            else "Επιβεβαίωση, παράλειψη και ιστορικό",
                            icon = MyFinHubIcons.Plan,
                            tone = FinanceTone.Neutral,
                        )
                    }
                }
            if (remaining.isNotEmpty()) {
                item("remaining-heading") {
                    MyFinHubSectionHeading(
                        title = "Υπόλοιπο πλάνου",
                        subtitle = "Κάθε καταγεγραμμένη πηγή εμφανίζεται μία φορά",
                        icon = MyFinHubIcons.Plan,
                        tone = FinanceTone.Neutral,
                    )
                }
                val groups = remaining.groupBy { item ->
                    item.dueDateIso.ifBlank { item.dueLabel.ifBlank { "9999-12-31" } }
                }
                groups.forEach { (dateKey, group) ->
                    item("date-$dateKey-${plannedItemSourceKey(group.first())}") {
                        Text(
                            group.first().dueLabel.ifBlank { "Χωρίς ημερομηνία" },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(group, key = { index, item ->
                        "remaining-${plannedItemSourceKey(item)}-row-$index"
                    }) { _, item ->
                        PlanS7Row(item = item, amountsVisible = amountsVisible, glyphOverrides = state.categoryGlyphOverrides)
                    }
                }
            } else if (urgent.isEmpty()) {
                item("plan-empty") {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Δεν υπάρχουν καταγεγραμμένες επόμενες κινήσεις.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastS7LinkCard(state: PlanUiState, amountsVisible: Boolean, onClick: () -> Unit) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val tone = if (state.forecastEndBalance >= 0.0) FinanceTone.Income else FinanceTone.Expense
    MyFinHubActionCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("s7_forecast_link"),
    ) {
        MyFinHubSectionHeading(
            title = "Πρόβλεψη 30 ημερών",
            subtitle = planForecastScopeLabel(state),
            icon = MyFinHubIcons.Plan,
            tone = FinanceTone.Neutral,
        )
        if (largeFont) {
            Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                Text("Προβλεπόμενο διαθέσιμο", style = MaterialTheme.typography.bodyMedium)
                MyFinHubAmountText(
                    amountVisibilityText(formatPlanS7Euro(state.forecastEndBalance), amountsVisible),
                    tone,
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Προβλεπόμενο διαθέσιμο", style = MaterialTheme.typography.bodyMedium)
                MyFinHubAmountText(
                    amountVisibilityText(formatPlanS7Euro(state.forecastEndBalance), amountsVisible),
                    tone,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        Text(
            "Άνοιγμα, έσοδα, υποχρεώσεις και επίδραση μεταφορών σε ξεχωριστή ανάλυση.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BudgetS7LinkCard(state: PlanUiState, amountsVisible: Boolean, onClick: () -> Unit) {
    MyFinHubActionCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("s7_budget_link"),
    ) {
        MyFinHubSectionHeading(
            title = "Μηνιαίος προϋπολογισμός",
            subtitle = state.budgetMonthLabel.ifBlank { "Τρέχων μήνας" },
            icon = MyFinHubIcons.Savings,
            tone = FinanceTone.Savings,
        )
        val progress = canonicalBudgetProgress(state)
        if (progress == null) {
            Text("Δεν έχει οριστεί συνολικό μηνιαίο όριο.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${progress.percent}% του ορίου", style = MaterialTheme.typography.bodyMedium)
                MyFinHubAmountText(
                    amountVisibilityText(formatPlanS7Euro(abs(progress.remaining)), amountsVisible),
                    if (progress.remaining >= 0.0) FinanceTone.Savings else FinanceTone.Expense,
                )
            }
            LinearProgressIndicator(
                progress = { progress.progress },
                modifier = Modifier.fillMaxWidth(),
                color = if (progress.thresholdReached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
            Text(
                if (progress.remaining >= 0.0) "Υπόλοιπο προϋπολογισμού" else "Υπέρβαση προϋπολογισμού",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanS7Row(item: PlannedItem, amountsVisible: Boolean, glyphOverrides: Map<String, String> = emptyMap()) {
    val tone = planS7Tone(item.flow)
    val icon = planS7Icon(item, glyphOverrides)
    val amountText = planS7Amount(item, amountsVisible)
    val largeFont = LocalDensity.current.fontScale >= 1.3f

    MyFinHubSectionCard(
        modifier = Modifier.fillMaxWidth().testTag("s7_plan_item_${plannedItemSourceKey(item)}"),
    ) {
        if (largeFont) {
            Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    MyFinHubIconBadge(icon, tone, null)
                    PlanS7Identity(item, Modifier.weight(1f))
                }
                MyFinHubAmountText(amountText, tone, modifier = Modifier.align(Alignment.End))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MyFinHubIconBadge(icon, tone, null)
                PlanS7Identity(item, Modifier.weight(1f))
                MyFinHubAmountText(amountText, tone)
            }
        }
    }
}

@Composable
private fun PlanS7Identity(item: PlannedItem, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            buildString {
                append(
                    when (item.kind) {
                        PlannedKind.RECURRING -> "Επαναλαμβανόμενο"
                        PlannedKind.SCHEDULED -> "Προγραμματισμένο"
                    },
                )
                if (item.category.isNotBlank()) append(" · ${item.category}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.accountLabel.isNotBlank()) {
            Text(
                item.accountLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Exact 30-day forecast detail. All eligible canonical rows are computed before presentation. */
@Composable
fun CanonicalPlanForecastScreen(
    state: PlanUiState,
    onBack: () -> Unit,
    onRecordItem: (PlannedItem) -> Unit = {},
) {
    val amountsVisible = rememberAmountVisibilityPreference()
    val mobileWindow = mobilePlanForecastWindow(state.forecastWindows)
    val mobileBaseline = mobilePlanScenarioBaseline(state.forecastScenarioBaselines)
    val included = planForecastIncludedItems(state)
    val undatedCount = state.items.count { it.dueDateIso.isBlank() }
    val overdueIncluded = included.any {
        it.flow == PlannedFlow.OBLIGATION && it.urgency == PlannedUrgency.OVERDUE
    }
    var rowsExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Πρόβλεψη 30 ημερών",
                subtitle = planForecastScopeLabel(state),
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("s7_forecast_root"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = padding.calculateTopPadding() + MyFinHubSpacing.xs,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = MyFinHubDesignMetrics.navigationContentBottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
        ) {
            item("forecast-summary") {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        Text("Προβλεπόμενο διαθέσιμο", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        MyFinHubAmountText(
                            amountVisibilityText(formatPlanS7Euro(state.forecastEndBalance), amountsVisible),
                            if (state.forecastEndBalance >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        ForecastS7Metric("Άνοιγμα", state.forecastStartBalance, FinanceTone.Neutral, amountsVisible = amountsVisible)
                        ForecastS7Metric("Προγραμματισμένα έσοδα", state.forecastExpectedIncome, FinanceTone.Income, showPositive = true, amountsVisible = amountsVisible)
                        ForecastS7Metric("Υποχρεώσεις", -state.forecastObligations, FinanceTone.Expense, amountsVisible = amountsVisible)
                        ForecastS7Metric("Επίδραση μεταφορών", state.forecastTransferImpact, FinanceTone.Neutral, showPositive = true, amountsVisible = amountsVisible)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "Άνοιγμα + έσοδα − υποχρεώσεις ± επίδραση μεταφορών = προβλεπόμενο διαθέσιμο.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (overdueIncluded) {
                            Text(
                                "Περιλαμβάνονται καθυστερημένες εκκρεμείς υποχρεώσεις που παραμένουν απλήρωτες.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        if (undatedCount > 0) {
                            Text(
                                "Μερική πρόβλεψη: $undatedCount καταγεγραμμένες κινήσεις χωρίς ημερομηνία δεν μπορούν να τοποθετηθούν στο εύρος.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.testTag("s7_forecast_partial"),
                            )
                        }
                    }
                }
            }

            if (mobileWindow != null) {
                item("web-forecast-comparison-heading") {
                    MyFinHubSectionHeading(
                        title = "Σύγκριση χαρτοφυλακίου",
                        subtitle = "30 ημέρες · όλα τα μη πιστωτικά υπόλοιπα",
                        icon = MyFinHubIcons.Plan,
                        tone = FinanceTone.Neutral,
                    )
                }
                item("portfolio-30") {
                    val window = mobileWindow
                    MyFinHubSectionCard(
                        modifier = Modifier.fillMaxWidth()
                            .testTag("s7_forecast_portfolio_" + window.days),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text(window.label, style = MaterialTheme.typography.titleMedium)
                            MyFinHubAmountText(
                                amountVisibilityText(
                                    formatPlanS7Euro(window.projectedPortfolio),
                                    amountsVisible,
                                ),
                                if (window.projectedPortfolio >= 0.0)
                                    FinanceTone.Income else FinanceTone.Expense,
                            )
                            Text("Ελάχιστο: " + amountVisibilityText(
                                formatPlanS7Euro(window.minimumPortfolio), amountsVisible),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (window.accountsWithNegativeBalance > 0 ||
                                window.accountsWithLowBalance > 0) {
                                Text(
                                    "Προσοχή: " + window.accountsWithNegativeBalance +
                                        " αρνητικά και " + window.accountsWithLowBalance +
                                        " χαμηλά υπόλοιπα λογαριασμών.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (window.sourceCounts.values.any { it > 0 }) {
                                val labels = listOf(
                                    "scheduled" to "προγραμματισμένες",
                                    "recurring" to "πάγια",
                                    "loan" to "δόσεις",
                                    "dated_event" to "μελλοντικές",
                                )
                                Text(
                                    "Πηγές πρόβλεψης: " + labels.joinToString(" · ") { (key, label) ->
                                        label + " " + (window.sourceCounts[key] ?: 0)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Synthetic screenshot previews retain old cards; only
                            // genuine canonical FinanceData has source trace rows.
                            if (window.portfolioTimeline.isNotEmpty() &&
                                window.accountBreakdown.isNotEmpty()) {
                                var expandedAccountDetail by rememberSaveable(window.days) {
                                    mutableStateOf(false)
                                }
                                TextButton(
                                    onClick = { expandedAccountDetail = !expandedAccountDetail },
                                    modifier = Modifier.testTag(
                                        "s7_forecast_breakdown_toggle_" + window.days),
                                ) {
                                    Text(if (expandedAccountDetail) "Απόκρυψη αναλυτικής πρόβλεψης"
                                        else "Ανάλυση ανά λογαριασμό και ημερομηνία")
                                }
                                if (expandedAccountDetail) {
                                    CanonicalWebForecastBreakdown(window, amountsVisible)
                                }
                            }
                            if (window.omittedSourceCount > 0) {
                                var showOmitted by rememberSaveable(window.days) {
                                    mutableStateOf(false)
                                }
                                Text(
                                    window.omittedSourceCount.toString() +
                                        " κινήσεις παραλείφθηκαν λόγω ελλιπών στοιχείων.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                if (window.omittedWarnings.isNotEmpty()) {
                                    TextButton(
                                        onClick = { showOmitted = !showOmitted },
                                        modifier = Modifier.testTag(
                                            "s7_forecast_omitted_toggle_" + window.days,
                                        ),
                                    ) {
                                        Text(if (showOmitted) "Απόκρυψη αιτιών" else "Αιτίες παράλειψης")
                                    }
                                    if (showOmitted) {
                                        window.omittedWarnings.forEachIndexed { index, reason ->
                                            Text(
                                                "${index + 1}. " + reason,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (mobileBaseline != null) {
                item("forecast-what-if") {
                    CanonicalForecastScenarioPanel(
                        baseline = mobileBaseline,
                        accounts = state.scheduledAccounts,
                        amountsVisible = amountsVisible,
                    )
                }
            }

            item("forecast-items-heading") {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    MyFinHubSectionHeading(
                        title = "Κινήσεις που υπολογίζονται",
                        subtitle = "${included.size} καταγεγραμμένες πηγές · χωρίς περικοπή ή συγχώνευση όμοιων τίτλων",
                        icon = MyFinHubIcons.Plan,
                        tone = FinanceTone.Neutral,
                    )
                    TextButton(
                        onClick = { rowsExpanded = !rowsExpanded },
                        modifier = Modifier.testTag("s7_forecast_expand"),
                    ) {
                        Text(if (rowsExpanded) "Απόκρυψη κινήσεων" else "Προβολή κινήσεων (${included.size})")
                    }
                }
            }

            if (rowsExpanded) {
                if (included.isEmpty()) {
                    item("forecast-empty") {
                        MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Δεν υπάρχουν καταγεγραμμένες κινήσεις μέσα στο εύρος.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    itemsIndexed(included, key = { index, item ->
                        "forecast-${plannedItemSourceKey(item)}-row-$index"
                    }) { index, item ->
                        val uniqueSource = state.items.count {
                            it.kind == item.kind && it.id == item.id
                        } == 1
                        ForecastS7ItemCard(
                            item = item, amountsVisible = amountsVisible,
                            onRecordItem = onRecordItem, recordEnabled = uniqueSource,
                            tagSuffix = if (uniqueSource) "" else "_duplicate_$index",
                            categoryGlyphOverrides = state.categoryGlyphOverrides,
                        )
                    }
                }
            }
        }
    }
}


/** Web forecast.ts per-day, per-account and source-row detail; no forecast-side writes. */
@Composable
private fun CanonicalWebForecastBreakdown(window: ForecastWindow, amountsVisible: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("s7_forecast_breakdown_" + window.days),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "Ελάχιστο χαρτοφυλακίου: " +
                amountVisibilityText(formatPlanS7Euro(window.minimumPortfolio), amountsVisible) +
                " · " + window.minimumPortfolioDateIso,
            style = MaterialTheme.typography.bodySmall,
        )
        if (window.insufficientData) {
            Text("Δεν υπάρχουν αρκετές γνωστές μελλοντικές ροές. Η πρόβλεψη παραμένει σταθερή.",
                style = MaterialTheme.typography.bodySmall)
        }
        Text("Πρόβλεψη σε κείμενο", style = MaterialTheme.typography.titleSmall)
        window.portfolioTimeline.forEachIndexed { index, point ->
            Column(
                modifier = Modifier.fillMaxWidth()
                    .testTag("s7_forecast_point_" + window.days + "_" + index),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(point.dateIso, style = MaterialTheme.typography.bodySmall)
                    Text(amountVisibilityText(formatPlanS7Euro(point.portfolio), amountsVisible),
                        style = MaterialTheme.typography.bodySmall)
                }
                if (point.balancesByAccount.isNotEmpty()) {
                    var showDailyAccounts by rememberSaveable(window.days, point.dateIso) {
                        mutableStateOf(false)
                    }
                    TextButton(
                        onClick = { showDailyAccounts = !showDailyAccounts },
                        modifier = Modifier.testTag(
                            "s7_forecast_point_accounts_toggle_" + window.days + "_" + index),
                    ) {
                        Text(if (showDailyAccounts) "Απόκρυψη υπολοίπων ημέρας"
                            else "Υπόλοιπα ανά λογαριασμό")
                    }
                    if (showDailyAccounts) {
                        window.accountBreakdown.forEach { account ->
                            point.balancesByAccount[account.accountId]?.let { balance ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("s7_forecast_point_account_" +
                                            window.days + "_" + index + "_" + account.accountId),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(account.accountLabel, style = MaterialTheme.typography.bodySmall)
                                    Text(amountVisibilityText(formatPlanS7Euro(balance), amountsVisible),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Προβολή ανά λογαριασμό", style = MaterialTheme.typography.titleSmall)
        window.accountBreakdown.forEach { account ->
            Column(
                modifier = Modifier.fillMaxWidth().testTag(
                    "s7_forecast_account_" + window.days + "_" + account.accountId),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
            ) {
                Text(account.accountLabel, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    "Τώρα " + amountVisibilityText(formatPlanS7Euro(account.current), amountsVisible) +
                        " → " + amountVisibilityText(formatPlanS7Euro(account.projected), amountsVisible) +
                        " · ελάχιστο " +
                        amountVisibilityText(formatPlanS7Euro(account.minimum), amountsVisible),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    when {
                        account.firstNegativeDateIso != null ->
                            "Αρνητικό από " + account.firstNegativeDateIso
                        account.firstLowDateIso != null ->
                            "Κάτω από " + amountVisibilityText(
                                formatPlanS7Euro(100.0), amountsVisible) +
                                " από " + account.firstLowDateIso
                        else -> "Δεν περνά το χαμηλό όριο"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (account.firstLowDateIso != null ||
                        account.firstNegativeDateIso != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Επόμενες γνωστές ροές", style = MaterialTheme.typography.titleSmall)
        val sourceLabels = mapOf(
            "scheduled" to "Προγραμματισμένη",
            "recurring" to "Πάγιο",
            "loan" to "Δόση",
            "dated_event" to "Μελλοντική κίνηση",
        )
        if (window.knownMovements.isEmpty()) {
            Text("Δεν υπάρχει γνωστή μελλοντική ροή στον ορίζοντα.",
                style = MaterialTheme.typography.bodySmall)
        }
        var showAllMovements by rememberSaveable(window.days) { mutableStateOf(false) }
        val displayedMovements = if (showAllMovements) window.knownMovements
            else window.knownMovements.take(8)
        displayedMovements.forEachIndexed { index, movement ->
            Column(
                modifier = Modifier.fillMaxWidth()
                    .testTag("s7_forecast_movement_" + window.days + "_" + index),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(movement.label.ifBlank { "Χωρίς περιγραφή" },
                            style = MaterialTheme.typography.bodySmall)
                        Text((sourceLabels[movement.source] ?: movement.source) +
                            " · " + movement.dateIso, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val delta = movement.portfolioDelta
                    val amount = if (kotlin.math.abs(delta) < 0.005) "0€ καθαρά"
                        else (if (delta > 0.0) "+" else "−") +
                            formatPlanS7Euro(kotlin.math.abs(delta))
                    Text(amountVisibilityText(amount, amountsVisible),
                        style = MaterialTheme.typography.bodySmall)
                }
                movement.accountImpacts.forEachIndexed { legIndex, impact ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .testTag("s7_forecast_movement_account_" +
                                window.days + "_" + index + "_" + legIndex),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(impact.accountLabel, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val prefix = when {
                            impact.delta > 0.0 -> "+"
                            impact.delta < 0.0 -> "−"
                            else -> ""
                        }
                        Text(amountVisibilityText(
                            prefix + formatPlanS7Euro(kotlin.math.abs(impact.delta)),
                            amountsVisible,
                        ), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (window.knownMovements.size > 8) {
            TextButton(
                onClick = { showAllMovements = !showAllMovements },
                modifier = Modifier.testTag("s7_forecast_movement_more_" + window.days),
            ) {
                Text(if (showAllMovements) "Λιγότερες γνωστές ροές"
                    else "Όλες οι " + window.knownMovements.size + " γνωστές ροές")
            }
        }
        Text(
            "Παραδοχή: άγνωστες αγορές/εισοδήματα δεν προβλέπονται. " +
                "Εσωτερικές μεταφορές έχουν καθαρή επίδραση 0€.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ForecastS7Metric(
    label: String,
    amount: Double,
    tone: FinanceTone,
    showPositive: Boolean = false,
    amountsVisible: Boolean,
) {
    val amountText = if (!amountsVisible) HIDDEN_AMOUNT_TEXT else when {
        amount < -0.005 -> "−${formatPlanS7Euro(abs(amount))}"
        amount > 0.005 && showPositive -> "+${formatPlanS7Euro(amount)}"
        else -> formatPlanS7Euro(abs(amount))
    }
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    if (largeFont) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro),
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MyFinHubAmountText(amountText, tone, modifier = Modifier.align(Alignment.End))
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MyFinHubAmountText(amountText, tone)
        }
    }
}

@Composable
private fun ForecastS7ItemCard(
    item: PlannedItem, amountsVisible: Boolean,
    onRecordItem: (PlannedItem) -> Unit,
    recordEnabled: Boolean = true, tagSuffix: String = "",
    categoryGlyphOverrides: Map<String, String> = emptyMap(),
) {
    val tone = planS7Tone(item.flow)
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            if (largeFont) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.Top,
                    ) {
                        MyFinHubIconBadge(planS7Icon(item, categoryGlyphOverrides), tone, null)
                        PlanS7Identity(item, Modifier.weight(1f))
                    }
                    MyFinHubAmountText(planS7Amount(item, amountsVisible), tone, modifier = Modifier.align(Alignment.End))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MyFinHubIconBadge(planS7Icon(item, categoryGlyphOverrides), tone, null)
                    PlanS7Identity(item, Modifier.weight(1f))
                    MyFinHubAmountText(planS7Amount(item, amountsVisible), tone)
                }
            }
            if (item.flow != PlannedFlow.TRANSFER) {
                if (!recordEnabled) {
                    Text(
                        "Ιστορικό διπλό ID · Καταχώριση μόνο από επαληθευμένη μοναδική εγγραφή",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    enabled = recordEnabled,
                    onClick = { onRecordItem(item) },
                    modifier = Modifier.testTag("s7_forecast_record_${plannedItemSourceKey(item)}$tagSuffix"),
                ) {
                    Text(if (item.flow == PlannedFlow.INCOME) "Καταχώριση εσόδου" else "Καταχώριση πληρωμής")
                }
            }
        }
    }
}

data class CategoryBudgetEditRequest(
    val budgetId: String?,
    val category: String,
    val monthlyLimitText: String,
    val alertThresholdText: String,
)

/** Canonical overall and category budget workspace. */
@Composable
fun CanonicalBudget2026Screen(
    state: PlanUiState,
    onSaveBudget: (String, String) -> Unit,
    onBack: () -> Unit,
    onSaveCategoryBudget: (CategoryBudgetEditRequest) -> Unit = {},
    onDeleteCategoryBudget: (String) -> Unit = {},
    onSaveSubcategoryBudget: (SubcategoryBudgetEditRequest) -> Unit = {},
    onDeleteSubcategoryBudget: (String) -> Unit = {},
    onDeleteOverallBudget: (String) -> Unit = {},
    onShiftBudgetMonth: ((Int) -> Unit)? = null,
    onSelectBudgetMonth: ((String) -> Unit)? = null,
    mutationInFlight: Boolean = false,
    mutationBlocked: Boolean = false,
) {
    val amountsVisible = rememberAmountVisibilityPreference()
    // Reset all draft state when changing the period, never submit an old-month draft.
    var savedLimit by rememberSaveable(state.budgetSelectedMonthIso) { mutableStateOf(state.budget.monthlyLimitText) }
    var savedThreshold by rememberSaveable(state.budgetSelectedMonthIso) { mutableStateOf(state.budget.alertThresholdText) }
    var limitDraft by rememberSaveable(state.budgetSelectedMonthIso) { mutableStateOf(state.budget.monthlyLimitText) }
    var thresholdDraft by rememberSaveable(state.budgetSelectedMonthIso) { mutableStateOf(state.budget.alertThresholdText) }
    var localError by rememberSaveable(state.budgetSelectedMonthIso) { mutableStateOf<String?>(null) }
    var discardDialogOpen by rememberSaveable(state.budgetSelectedMonthIso) { mutableStateOf(false) }
    var saveRequested by rememberSaveable(state.budgetSelectedMonthIso) { mutableStateOf(false) }
    // Web's budget editor displays the first 24 records, then reveals 24 more
    // on demand. Never silently hide historical category budgets.
    var visibleCategoryBudgetLimit by rememberSaveable { mutableStateOf(24) }
    var visibleSubcategoryBudgetLimit by rememberSaveable { mutableStateOf(24) }
    var visibleBudgetCommitmentLimit by rememberSaveable(state.budgetSelectedMonthIso) {
        mutableStateOf(24)
    }
    var creatingCategoryBudget by remember { mutableStateOf(false) }
    var editingCategoryBudget by remember { mutableStateOf<CategoryBudget?>(null) }
    var deletingCategoryBudget by remember { mutableStateOf<CategoryBudget?>(null) }
    var creatingSubcategoryBudget by remember { mutableStateOf(false) }
    var editingSubcategoryBudget by remember { mutableStateOf<CategoryBudget?>(null) }
    var deletingSubcategoryBudget by remember { mutableStateOf<CategoryBudget?>(null) }
    var deletingOverallBudget by rememberSaveable { mutableStateOf(false) }
    var selectBudgetMonthOpen by rememberSaveable { mutableStateOf(false) }
    var selectedMonthDraft by rememberSaveable(state.budgetSelectedMonthIso) {
        mutableStateOf(state.budgetSelectedMonthIso)
    }

    val savedBudget = BudgetDraft(savedLimit, savedThreshold)
    val draftBudget = BudgetDraft(limitDraft, thresholdDraft)
    val dirty = !budgetDraftEquivalent(savedBudget, draftBudget)
    val previewState = state.copy(budget = draftBudget)
    val progress = canonicalBudgetProgress(previewState)
    val canonicalMatchesDraft = budgetDraftEquivalent(state.budget, draftBudget)
    val canonicalChangedFromSaved = !budgetDraftEquivalent(state.budget, savedBudget)

    LaunchedEffect(
        state.budget.monthlyLimitText,
        state.budget.alertThresholdText,
        mutationInFlight,
        mutationBlocked,
        saveRequested,
        dirty,
    ) {
        if (!saveRequested && !dirty) {
            savedLimit = state.budget.monthlyLimitText
            savedThreshold = state.budget.alertThresholdText
            limitDraft = state.budget.monthlyLimitText
            thresholdDraft = state.budget.alertThresholdText
        }
        if (
            saveRequested &&
            !mutationInFlight &&
            !mutationBlocked &&
            canonicalMatchesDraft &&
            canonicalChangedFromSaved
        ) {
            onBack()
        }
    }

    fun requestBack() {
        when {
            mutationInFlight -> Unit
            saveRequested && !mutationBlocked -> Unit
            saveRequested && mutationBlocked -> onBack()
            dirty -> discardDialogOpen = true
            else -> onBack()
        }
    }

    BackHandler(enabled = dirty || mutationInFlight || saveRequested) { requestBack() }

    if (selectBudgetMonthOpen && onSelectBudgetMonth != null) {
        AlertDialog(
            onDismissRequest = { selectBudgetMonthOpen = false },
            title = { Text("Επιλογή μήνα προϋπολογισμού") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text(
                        "Γράψε τον μήνα σε μορφή YYYY-MM, π.χ. 2026-12. " +
                            "Η επιλογή δεν αλλάζει οικονομικές κινήσεις.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = selectedMonthDraft,
                        onValueChange = { selectedMonthDraft = it.take(7) },
                        label = { Text("Μήνας (YYYY-MM)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("s7_budget_month_input"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !dirty && !saveRequested && !mutationInFlight && !mutationBlocked &&
                        runCatching {
                            java.time.YearMonth.parse(selectedMonthDraft).toString() == selectedMonthDraft
                        }.getOrDefault(false),
                    modifier = Modifier.testTag("s7_budget_month_apply"),
                    onClick = {
                        onSelectBudgetMonth(selectedMonthDraft)
                        selectBudgetMonthOpen = false
                    },
                ) { Text("Μετάβαση") }
            },
            dismissButton = {
                TextButton(onClick = { selectBudgetMonthOpen = false }) { Text("Ακύρωση") }
            },
        )
    }

    if (deletingOverallBudget && state.overallBudgetId != null) {
        AlertDialog(
            onDismissRequest = {
                if (!mutationInFlight && !mutationBlocked) deletingOverallBudget = false
            },
            title = { Text("Διαγραφή συνολικού προϋπολογισμού;") },
            text = {
                Text("Θα αφαιρεθεί μόνο το συνολικό όριο του τρέχοντος μήνα. " +
                    "Τα όρια κατηγοριών, οι κινήσεις και το ιστορικό δεν θα αλλάξουν.")
            },
            confirmButton = {
                TextButton(
                    enabled = !mutationInFlight && !mutationBlocked && !saveRequested && !dirty,
                    modifier = Modifier.testTag("s7_budget_confirm_delete_overall"),
                    onClick = {
                        onDeleteOverallBudget(state.overallBudgetId)
                        deletingOverallBudget = false
                    },
                ) { Text("Διαγραφή συνολικού ορίου") }
            },
            dismissButton = {
                TextButton(
                    enabled = !mutationInFlight && !mutationBlocked,
                    onClick = { deletingOverallBudget = false },
                ) { Text("Ακύρωση") }
            },
        )
    }

    if (discardDialogOpen) {
        AlertDialog(
            onDismissRequest = { discardDialogOpen = false },
            title = { Text("Απόρριψη αλλαγών;") },
            text = { Text("Οι μη αποθηκευμένες αλλαγές του προϋπολογισμού θα χαθούν.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        discardDialogOpen = false
                        onBack()
                    },
                ) { Text("Απόρριψη") }
            },
            dismissButton = {
                TextButton(onClick = { discardDialogOpen = false }) { Text("Συνέχεια επεξεργασίας") }
            },
        )
    }

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Μηνιαίος προϋπολογισμός",
                subtitle = state.budgetMonthLabel.ifBlank { "Συνολικό όριο τρέχοντος μήνα" },
                navigation = { MyFinHubBackButton(::requestBack) },
            )
        },
        bottomBar = {
            Surface {
                MyFinHubPrimaryAction(
                    label = when {
                        mutationInFlight -> "Αποθήκευση…"
                        mutationBlocked -> "Χρειάζεται συγχρονισμός"
                        saveRequested -> "Αναμονή επιβεβαίωσης…"
                        else -> "Αποθήκευση προϋπολογισμού"
                    },
                    enabled = dirty && !saveRequested && !mutationInFlight && !mutationBlocked,
                    onClick = {
                        val limit = mobileBudgetLimitOrNull(limitDraft)
                        val threshold = thresholdDraft.toIntOrNull()
                        localError = when {
                            limit == null -> "Το μηνιαίο όριο πρέπει να είναι θετικό ποσό με έως δύο δεκαδικά."
                            threshold == null || threshold !in 50..99 -> "Το όριο προειδοποίησης πρέπει να είναι από 50 έως 99%."
                            else -> null
                        }
                        if (localError == null) {
                            saveRequested = true
                            onSaveBudget(limitDraft, thresholdDraft)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("s7_budget_save")
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = MyFinHubDesignMetrics.screenHorizontalPadding, vertical = MyFinHubSpacing.sm),
                    icon = MyFinHubIcons.Savings,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("s7_budget_root"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = padding.calculateTopPadding() + MyFinHubSpacing.xs,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = padding.calculateBottomPadding() + MyFinHubSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
        ) {
            if (onShiftBudgetMonth != null) {
                item("budget-month-selector") {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()
                        .testTag("s7_budget_month_selector")) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text(
                                "Περίοδος: " + state.budgetMonthLabel,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Κάθε μήνας έχει δικά του συνολικά όρια, όρια κατηγοριών " +
                                    "και υποκατηγοριών. Η αλλαγή μήνα δεν αποθηκεύει οικονομική κίνηση.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (onSelectBudgetMonth != null) {
                                TextButton(
                                    enabled = !dirty && !saveRequested && !mutationInFlight && !mutationBlocked,
                                    onClick = {
                                        selectedMonthDraft = state.budgetSelectedMonthIso
                                        selectBudgetMonthOpen = true
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("s7_budget_pick_month"),
                                ) { Text("Επιλογή συγκεκριμένου μήνα") }
                            }
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(
                                    enabled = !dirty && !saveRequested && !mutationInFlight && !mutationBlocked,
                                    onClick = { onShiftBudgetMonth(-1) },
                                    modifier = Modifier.testTag("s7_budget_previous_month"),
                                ) { Text("‹ Προηγούμενος") }
                                TextButton(
                                    enabled = !dirty && !saveRequested && !mutationInFlight && !mutationBlocked,
                                    onClick = { onShiftBudgetMonth(1) },
                                    modifier = Modifier.testTag("s7_budget_next_month"),
                                ) { Text("Επόμενος ›") }
                            }
                        }
                    }
                }
            }
            if (progress != null) {
                item("budget-progress") {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                            MyFinHubSectionHeading(
                                title = "Πρόοδος μήνα",
                                subtitle = if (amountsVisible) {
                                    "${formatPlanS7Euro(progress.spent)} από ${formatPlanS7Euro(progress.limit)}"
                                } else {
                                    "$HIDDEN_AMOUNT_TEXT από $HIDDEN_AMOUNT_TEXT"
                                },
                                icon = MyFinHubIcons.Savings,
                                tone = FinanceTone.Savings,
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (progress.remaining >= 0.0) "Υπόλοιπο" else "Υπέρβαση")
                                MyFinHubAmountText(
                                    amountVisibilityText(formatPlanS7Euro(abs(progress.remaining)), amountsVisible),
                                    if (progress.remaining >= 0.0) FinanceTone.Savings else FinanceTone.Expense,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (progress.thresholdReached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "${progress.percent}% του ορίου",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.budgetProjectedDue.isFinite() &&
                                state.budgetProjectedDue > 0.0 &&
                                state.budgetProjectedRemaining.isFinite()) {
                                Text(
                                    "Γνωστές εκκρεμείς υποχρεώσεις: " +
                                        amountVisibilityText(formatPlanS7Euro(state.budgetProjectedDue), amountsVisible),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("s7_budget_projected_due"),
                                )
                                Text(
                                    "Εκτιμώμενο υπόλοιπο μετά τις υποχρεώσεις: " +
                                        amountVisibilityText(formatPlanS7Euro(state.budgetProjectedRemaining), amountsVisible),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (state.budgetProjectedStatus != "ok") {
                                    Text(
                                        if (state.budgetProjectedStatus == "exceeded")
                                            "Με τις γνωστές υποχρεώσεις προβλέπεται υπέρβαση του συνολικού ορίου."
                                        else "Με τις γνωστές υποχρεώσεις προβλέπεται προσέγγιση του ορίου ειδοποίησης.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.testTag("s7_budget_projected_warning"),
                                    )
                                }
                            }
                            if (progress.thresholdReached) {
                                Text(
                                    "Οι δαπάνες έχουν φτάσει το όριο προειδοποίησης ${progress.threshold}%.",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.testTag("s7_budget_threshold_warning"),
                                )
                            }
                        }
                    }
                }
            }

            if (state.budgetKnownCommitments.isNotEmpty()) {
                item("budget-commitments-heading") {
                    MyFinHubSectionHeading(
                        title = "Γνωστές εκκρεμείς υποχρεώσεις",
                        subtitle = "Προγραμματισμένες χρεώσεις και επόμενα πάγια · δεν έχουν εξοφληθεί",
                        icon = MyFinHubIcons.Plan,
                        tone = FinanceTone.Neutral,
                    )
                }
                itemsIndexed(
                    state.budgetKnownCommitments.take(visibleBudgetCommitmentLimit),
                    key = { index, row -> "budget-upcoming-" + row.source + "-" + row.id + "-" + index },
                ) { index, row ->
                    MyFinHubSectionCard(
                        modifier = Modifier.fillMaxWidth()
                            .testTag("s7_budget_commitment_$index"),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                            Text(
                                row.category + (row.subcategory?.let { " › " + it } ?: ""),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                row.date + " · " +
                                    if (row.source == "recurring") "Επόμενο πάγιο"
                                    else "Προγραμματισμένη χρέωση",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MyFinHubAmountText(
                                amountVisibilityText(formatPlanS7Euro(row.amount), amountsVisible),
                                FinanceTone.Expense,
                            )
                        }
                    }
                }
                if (state.budgetKnownCommitments.size > visibleBudgetCommitmentLimit) {
                    item("budget-commitments-more") {
                        TextButton(
                            onClick = {
                                visibleBudgetCommitmentLimit =
                                    (visibleBudgetCommitmentLimit + 24)
                                        .coerceAtMost(state.budgetKnownCommitments.size)
                            },
                            modifier = Modifier.fillMaxWidth()
                                .testTag("s7_budget_commitments_more"),
                        ) {
                            Text(
                                "Περισσότερες υποχρεώσεις (" +
                                    (state.budgetKnownCommitments.size - visibleBudgetCommitmentLimit) + ")",
                            )
                        }
                    }
                }
            }

            if (state.categoryBudgets.isNotEmpty()) {
                item("budget-categories-heading") {
                    MyFinHubSectionHeading(
                        title = "Όρια ανά κατηγορία",
                        subtitle = "Πραγματικές κατηγοριοποιημένες δαπάνες του μήνα · επιστροφές μειώνουν τη χρήση",
                        icon = MyFinHubIcons.Savings,
                        tone = FinanceTone.Neutral,
                    )
                }
                itemsIndexed(
                    state.categoryBudgets.take(visibleCategoryBudgetLimit),
                    key = { index, budget -> "category-budget-" + budget.id + "#" + index },
                ) { index, budget ->
                    MyFinHubSectionCard(
                        modifier = Modifier.fillMaxWidth().testTag(
                            "s7_category_budget_" + budget.id +
                                (if (state.categoryBudgets.count { it.id == budget.id } == 1)
                                    "" else "_duplicate_" + index),
                        ),
                    ) {
                        val limit = budget.effectiveLimit
                            ?: (budget.monthlyLimitText.replace(',', '.').toDoubleOrNull() ?: 0.0)
                        val used = budget.spent.coerceAtLeast(0.0)
                        val threshold = (budget.alertThresholdText.toDoubleOrNull() ?: 80.0) / 100.0
                        val ratio = if (limit > 0.0) used / limit else 0.0
                        val remaining = limit - used
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text(budget.name, style = MaterialTheme.typography.titleMedium)
                            if (budget.projectedDue > 0.0) {
                                Text(
                                    "Γνωστές μελλοντικές υποχρεώσεις: " +
                                        amountVisibilityText(formatPlanS7Euro(budget.projectedDue), amountsVisible),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Εκτιμώμενο υπόλοιπο: " +
                                        amountVisibilityText(formatPlanS7Euro(budget.projectedRemaining), amountsVisible),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (budget.projectedStatus != "ok") {
                                    Text(
                                        if (budget.projectedStatus == "exceeded")
                                            "Προβλεπόμενη υπέρβαση με τις γνωστές υποχρεώσεις."
                                        else "Προβλεπόμενη προσέγγιση του ορίου προειδοποίησης.",
                                        color = MaterialTheme.colorScheme.tertiary,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                            if (budget.carried > 0.0) {
                                Text(
                                    "Μεταφορά από προηγούμενο μήνα: " +
                                        amountVisibilityText(formatPlanS7Euro(budget.carried), amountsVisible),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { editingCategoryBudget = budget },
                                enabled = !mutationBlocked && !mutationInFlight && !saveRequested &&
                                    budget.id.isNotBlank() &&
                                    state.categoryBudgets.count { it.name == budget.name } == 1 &&
                                    state.categoryBudgets.count { it.id == budget.id } == 1,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("s7_category_budget_edit_" + budget.id),
                            ) { Text("Επεξεργασία ορίου") }
                            TextButton(
                                onClick = { deletingCategoryBudget = budget },
                                enabled = !mutationBlocked && !mutationInFlight && !saveRequested &&
                                    budget.id.isNotBlank() &&
                                    state.categoryBudgets.count { it.name == budget.name } == 1 &&
                                    state.categoryBudgets.count { it.id == budget.id } == 1,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("s7_category_budget_delete_" + budget.id),
                            ) { Text("Διαγραφή ορίου") }
                            Text(
                                if (amountsVisible) "${formatPlanS7Euro(used)} / ${formatPlanS7Euro(limit)}"
                                else "$HIDDEN_AMOUNT_TEXT / $HIDDEN_AMOUNT_TEXT",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            LinearProgressIndicator(
                                progress = { ratio.toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                (if (remaining >= 0.0) "Υπόλοιπο: " else "Υπέρβαση: ") +
                                    amountVisibilityText(formatPlanS7Euro(abs(remaining)), amountsVisible),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("s7_category_budget_remaining_" + index),
                            )
                            if (budget.rawSpent < 0.0) {
                                Text(
                                    "Καθαρό αποτέλεσμα επιστροφών: " +
                                        amountVisibilityText(formatPlanS7Euro(budget.rawSpent), amountsVisible),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("s7_category_budget_refunds_" + index),
                                )
                            }
                            if (ratio > 1.0 || ratio >= threshold) {
                                Text(
                                    if (ratio > 1.0) "Υπέρβαση ορίου" else "Κοντά στο όριο",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
                if (state.categoryBudgets.size > visibleCategoryBudgetLimit) {
                    item("budget-categories-more") {
                        TextButton(
                            onClick = { visibleCategoryBudgetLimit += 24 },
                            modifier = Modifier.fillMaxWidth()
                                .testTag("s7_category_budget_more"),
                        ) {
                            Text("Προβολή περισσότερων · " +
                                (state.categoryBudgets.size - visibleCategoryBudgetLimit) + " ακόμη")
                        }
                    }
                }
            }

            if (state.subcategoryBudgets.isNotEmpty()) {
                item("budget-subcategories-heading") {
                    MyFinHubSectionHeading(
                        title = "Όρια ανά υποκατηγορία",
                        subtitle = "Πραγματικές δαπάνες της επιλεγμένης υποκατηγορίας · χωρίς διπλή χρέωση του γονικού budget",
                        icon = MyFinHubIcons.Savings,
                        tone = FinanceTone.Neutral,
                    )
                }
                itemsIndexed(
                    state.subcategoryBudgets.take(visibleSubcategoryBudgetLimit),
                    key = { index, budget -> "subcategory-budget-" + budget.id + "#" + index },
                ) { index, budget ->
                    MyFinHubSectionCard(
                        modifier = Modifier.fillMaxWidth()
                            .testTag("s7_subcategory_budget_" + index),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            val limit = budget.effectiveLimit
                                ?: (budget.monthlyLimitText.replace(',', '.').toDoubleOrNull() ?: 0.0)
                            val used = budget.spent.coerceAtLeast(0.0)
                            val ratio = if (limit > 0.0) used / limit else 0.0
                            Text(budget.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                amountVisibilityText(formatPlanS7Euro(used), amountsVisible) + " / " +
                                    amountVisibilityText(formatPlanS7Euro(limit), amountsVisible),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            LinearProgressIndicator(
                                progress = { ratio.toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                (if (budget.remaining >= 0.0) "Υπόλοιπο: " else "Υπέρβαση: ") +
                                    amountVisibilityText(
                                        formatPlanS7Euro(abs(budget.remaining)), amountsVisible),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (budget.projectedDue > 0.0) {
                                Text(
                                    "Γνωστές μελλοντικές υποχρεώσεις: " +
                                        amountVisibilityText(formatPlanS7Euro(budget.projectedDue), amountsVisible),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Εκτιμώμενο υπόλοιπο: " +
                                        amountVisibilityText(formatPlanS7Euro(budget.projectedRemaining), amountsVisible),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (budget.projectedStatus != "ok") {
                                    Text(
                                        if (budget.projectedStatus == "exceeded")
                                            "Προβλεπόμενη υπέρβαση με τις γνωστές υποχρεώσεις."
                                        else "Προβλεπόμενη προσέγγιση του ορίου προειδοποίησης.",
                                        color = MaterialTheme.colorScheme.tertiary,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                            if (budget.carried > 0.0) {
                                Text(
                                    "Μεταφορά υπολοίπου: " +
                                        amountVisibilityText(
                                            formatPlanS7Euro(budget.carried), amountsVisible),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(
                                onClick = { editingSubcategoryBudget = budget },
                                enabled = !mutationBlocked && !mutationInFlight && !saveRequested &&
                                    budget.id.isNotBlank() && budget.category != null &&
                                    budget.subcategory != null &&
                                    state.subcategoryBudgets.count { it.id == budget.id } == 1,
                                modifier = Modifier.testTag("s7_subcategory_budget_edit_$index"),
                            ) { Text("Επεξεργασία ορίου") }
                            TextButton(
                                onClick = { deletingSubcategoryBudget = budget },
                                enabled = !mutationBlocked && !mutationInFlight && !saveRequested &&
                                    budget.id.isNotBlank() && budget.category != null &&
                                    budget.subcategory != null &&
                                    state.subcategoryBudgets.count { it.id == budget.id } == 1,
                                modifier = Modifier.testTag("s7_subcategory_budget_delete_$index"),
                            ) { Text("Διαγραφή ορίου") }
                            Text(
                                "Το όριο δεν δημιουργεί νέα οικονομική κίνηση.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.subcategoryBudgets.size > visibleSubcategoryBudgetLimit) {
                    item("budget-subcategories-more") {
                        TextButton(
                            onClick = {
                                visibleSubcategoryBudgetLimit =
                                    (visibleSubcategoryBudgetLimit + 24)
                                        .coerceAtMost(state.subcategoryBudgets.size)
                            },
                            modifier = Modifier.fillMaxWidth()
                                .testTag("s7_subcategory_budget_more"),
                        ) {
                            Text("Προβολή περισσότερων · " +
                                (state.subcategoryBudgets.size - visibleSubcategoryBudgetLimit) +
                                " ακόμη")
                        }
                    }
                }
            }

            item("budget-editor") {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        MyFinHubOutlinedField(
                            value = limitDraft,
                            onValueChange = {
                                limitDraft = it
                                localError = null
                            },
                            label = "Μηνιαίο όριο",
                            modifier = Modifier.testTag("s7_budget_limit"),
                            suffix = { Text("€") },
                            errorMessage = localError.takeIf { it?.contains("μηνιαίο όριο") == true },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            enabled = !mutationInFlight && !saveRequested,
                        )
                        MyFinHubOutlinedField(
                            value = thresholdDraft,
                            onValueChange = {
                                thresholdDraft = it.filter(Char::isDigit).take(3)
                                localError = null
                            },
                            label = "Όριο προειδοποίησης",
                            modifier = Modifier.testTag("s7_budget_threshold"),
                            suffix = { Text("%") },
                            errorMessage = localError.takeIf { it?.contains("προειδοποίησης") == true },
                            supportingText = if (localError?.contains("προειδοποίησης") == true) null else
                                "Από 50 έως 99% · επισημαίνει την πρόοδο μέσα στο MyFinHub",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            enabled = !mutationInFlight && !saveRequested,
                        )
                        if (state.overallBudgetId != null) {
                            TextButton(
                                onClick = { deletingOverallBudget = true },
                                enabled = !mutationInFlight && !mutationBlocked &&
                                    !saveRequested && !dirty,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("s7_budget_delete_overall"),
                            ) { Text("Διαγραφή συνολικού ορίου") }
                        }
                        progress?.threshold?.let { threshold ->
                            Text(
                                "Η ένδειξη προόδου επισημαίνεται στο $threshold% του συνολικού ορίου.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // Keep the existing summary and overall editor in their original
            // on-screen order. The new affordance follows them without shifting
            // approved screenshot baselines or the top-level budget navigation.
            item("budget-category-new") {
                TextButton(
                    onClick = { creatingCategoryBudget = true },
                    enabled = !mutationBlocked && !mutationInFlight && !saveRequested,
                    modifier = Modifier.fillMaxWidth().testTag("s7_category_budget_create"),
                ) { Text("Νέο όριο κατηγορίας") }
            }
            item("budget-subcategory-new") {
                TextButton(
                    onClick = { creatingSubcategoryBudget = true },
                    enabled = !mutationBlocked && !mutationInFlight && !saveRequested &&
                        state.ruleSubcategories.values.any { it.isNotEmpty() },
                    modifier = Modifier.fillMaxWidth()
                        .testTag("s7_subcategory_budget_create"),
                ) { Text("Νέο όριο υποκατηγορίας") }
            }
        }
    }
    deletingSubcategoryBudget?.let { candidate ->
        AlertDialog(
            onDismissRequest = {
                if (!mutationBlocked && !mutationInFlight) deletingSubcategoryBudget = null
            },
            title = { Text("Διαγραφή ορίου υποκατηγορίας;") },
            text = {
                Text("Θα αφαιρεθεί μόνο το όριο «" + candidate.name +
                    "». Η γονική κατηγορία και οι συναλλαγές θα παραμείνουν.")
            },
            confirmButton = {
                TextButton(
                    enabled = !mutationBlocked && !mutationInFlight && !saveRequested &&
                        candidate.id.isNotBlank() &&
                        state.subcategoryBudgets.count { it.id == candidate.id } == 1,
                    modifier = Modifier.testTag("s7_subcategory_budget_confirm_delete"),
                    onClick = {
                        onDeleteSubcategoryBudget(candidate.id)
                        deletingSubcategoryBudget = null
                    },
                ) { Text("Διαγραφή ορίου") }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingSubcategoryBudget = null },
                    enabled = !mutationBlocked && !mutationInFlight,
                ) { Text("Ακύρωση") }
            },
        )
    }
    deletingCategoryBudget?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!mutationBlocked && !mutationInFlight) deletingCategoryBudget = null },
            title = { Text("Διαγραφή ορίου κατηγορίας;") },
            text = {
                Text("Το όριο «" + candidate.name +
                    "» θα αφαιρεθεί. Οι οικονομικές κινήσεις και οι δαπάνες θα παραμείνουν.")
            },
            confirmButton = {
                TextButton(
                    enabled = !mutationBlocked && !mutationInFlight && !saveRequested,
                    modifier = Modifier.testTag("s7_category_budget_confirm_delete"),
                    onClick = {
                        onDeleteCategoryBudget(candidate.id)
                        deletingCategoryBudget = null
                    },
                ) { Text("Διαγραφή ορίου") }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingCategoryBudget = null },
                    enabled = !mutationBlocked && !mutationInFlight,
                ) { Text("Ακύρωση") }
            },
        )
    }
    if (creatingSubcategoryBudget || editingSubcategoryBudget != null) {
        CanonicalSubcategoryBudgetEditor(
            initial = editingSubcategoryBudget,
            categories = state.ruleSubcategories,
            existing = state.subcategoryBudgets,
            mutationBlocked = mutationInFlight || mutationBlocked || saveRequested,
            onDismiss = {
                creatingSubcategoryBudget = false
                editingSubcategoryBudget = null
            },
            onSave = { request ->
                onSaveSubcategoryBudget(request)
                creatingSubcategoryBudget = false
                editingSubcategoryBudget = null
            },
        )
    }
    if (creatingCategoryBudget || editingCategoryBudget != null) {
        CanonicalCategoryBudgetEditor(
            initial = editingCategoryBudget,
            existing = state.categoryBudgets,
            categories = state.budgetCategories,
            mutationBlocked = mutationInFlight || mutationBlocked || saveRequested,
            onDismiss = { creatingCategoryBudget = false; editingCategoryBudget = null },
            onSave = { request ->
                onSaveCategoryBudget(request)
                creatingCategoryBudget = false
                editingCategoryBudget = null
            },
        )
    }
}

@Composable
private fun CanonicalCategoryBudgetEditor(
    initial: CategoryBudget?,
    existing: List<CategoryBudget>,
    categories: List<String>,
    mutationBlocked: Boolean,
    onDismiss: () -> Unit,
    onSave: (CategoryBudgetEditRequest) -> Unit,
) {
    var category by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var amount by remember(initial?.id) { mutableStateOf(initial?.monthlyLimitText.orEmpty()) }
    var threshold by remember(initial?.id) {
        mutableStateOf(initial?.alertThresholdText ?: "80")
    }
    val valid = category.trim().isNotEmpty() && mobileBudgetLimitOrNull(amount) != null &&
        (initial != null || categories.isEmpty() || category.trim() in categories) &&
        (threshold.toIntOrNull()?.let { it in 50..99 } == true) &&
        existing.none { it.name.trim() == category.trim() && it.id != initial?.id }
    AlertDialog(
        onDismissRequest = { if (!mutationBlocked) onDismiss() },
        title = { Text(if (initial == null) "Νέο όριο κατηγορίας" else "Επεξεργασία ορίου") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                Text("Το όριο δεν καταχωρίζει οικονομική κίνηση.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = category, onValueChange = { category = it },
                    enabled = !mutationBlocked && initial == null && categories.isEmpty(),
                    label = { Text("Κατηγορία") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("s7_category_editor_name"),
                )
                if (initial == null && categories.isNotEmpty()) {
                    TextButton(
                        onClick = { categoryMenuOpen = true },
                        enabled = !mutationBlocked,
                        modifier = Modifier.fillMaxWidth()
                            .testTag("s7_category_editor_select"),
                    ) { Text(if (category.isBlank()) "Επίλεξε κατηγορία εξόδων" else "Αλλαγή: $category") }
                    DropdownMenu(
                        expanded = categoryMenuOpen,
                        onDismissRequest = { categoryMenuOpen = false },
                    ) {
                        categories.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = { category = item; categoryMenuOpen = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    enabled = !mutationBlocked,
                    label = { Text("Μηνιαίο όριο (€)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("s7_category_editor_amount"),
                )
                OutlinedTextField(
                    value = threshold, onValueChange = { threshold = it },
                    enabled = !mutationBlocked,
                    label = { Text("Προειδοποίηση (50–99%)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("s7_category_editor_threshold"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(CategoryBudgetEditRequest(
                        budgetId = initial?.id,
                        category = category.trim(),
                        monthlyLimitText = amount,
                        alertThresholdText = threshold,
                    ))
                },
                enabled = valid && !mutationBlocked,
                modifier = Modifier.testTag("s7_category_editor_save"),
            ) { Text("Αποθήκευση ορίου") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !mutationBlocked) { Text("Ακύρωση") }
        },
    )
}

internal fun budgetDraftEquivalent(first: BudgetDraft, second: BudgetDraft): Boolean {
    val firstLimit = mobileBudgetLimitOrNull(first.monthlyLimitText)
    val secondLimit = mobileBudgetLimitOrNull(second.monthlyLimitText)
    val sameLimit = when {
        firstLimit == null || secondLimit == null ->
            first.monthlyLimitText.trim() == second.monthlyLimitText.trim()
        else -> firstLimit == secondLimit
    }
    return sameLimit && first.alertThresholdText.toIntOrNull() == second.alertThresholdText.toIntOrNull()
}

private fun planS7Tone(flow: PlannedFlow): FinanceTone = when (flow) {
    PlannedFlow.OBLIGATION -> FinanceTone.Expense
    PlannedFlow.INCOME -> FinanceTone.Income
    PlannedFlow.TRANSFER -> FinanceTone.Neutral
}

private fun planS7Icon(item: PlannedItem, overrides: Map<String, String> = emptyMap()) = when (item.flow) {
    PlannedFlow.TRANSFER -> MyFinHubIcons.Transfer
    PlannedFlow.INCOME -> myFinHubCategoryIcon(
        item.category.ifBlank { item.title }, MyFinHubIcons.Income,
        overrides["income:" + item.category],
    )
    PlannedFlow.OBLIGATION -> myFinHubCategoryIcon(
        item.category.ifBlank { item.title }, MyFinHubIcons.Plan,
        overrides["expense:" + item.category],
    )
}

private fun planS7Amount(item: PlannedItem, amountsVisible: Boolean): String = if (!amountsVisible) {
    HIDDEN_AMOUNT_TEXT
} else when (item.flow) {
    PlannedFlow.OBLIGATION -> "−${formatPlanS7Euro(abs(item.amount))}"
    PlannedFlow.INCOME -> "+${formatPlanS7Euro(abs(item.amount))}"
    PlannedFlow.TRANSFER -> formatPlanS7Euro(abs(item.amount))
}

private fun String?.toPlanS7DateOrNull(): LocalDate? = this
    ?.takeIf(String::isNotBlank)
    ?.let { raw -> runCatching { LocalDate.parse(raw.take(10)) }.getOrNull() }

private fun formatPlanS7Euro(value: Double): String =
    if (value.isFinite()) NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
    else "—"
