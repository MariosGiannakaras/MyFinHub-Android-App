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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    val limit = state.budget.monthlyLimitText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
    val spent = state.budgetSpent.coerceAtLeast(0.0)
    val ratio = spent / limit
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

internal fun planRemainingItems(state: PlanUiState): List<PlannedItem> {
    val urgentKeys = planUrgentObligations(state).mapTo(mutableSetOf(), ::plannedItemSourceKey)
    return state.items.filterNot { item -> plannedItemSourceKey(item) in urgentKeys }
}

internal fun planForecastIncludedItems(state: PlanUiState): List<PlannedItem> {
    val end = state.forecastEndDateIso.toPlanS7DateOrNull() ?: return state.items.filter { it.dueDateIso.isNotBlank() }
    return state.items.filter { item ->
        item.dueDateIso.toPlanS7DateOrNull()?.let { due -> !due.isAfter(end) } == true
    }
}

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
                items(urgent, key = { "urgent-${plannedItemSourceKey(it)}" }) { item ->
                    PlanS7Row(item = item, amountsVisible = amountsVisible)
                }
            }

            item("forecast-link") {
                ForecastS7LinkCard(state = state, amountsVisible = amountsVisible, onClick = onOpenForecast)
            }
            item("budget-link") {
                BudgetS7LinkCard(state = state, amountsVisible = amountsVisible, onClick = onOpenBudget)
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
                    items(group, key = { "remaining-${plannedItemSourceKey(it)}" }) { item ->
                        PlanS7Row(item = item, amountsVisible = amountsVisible)
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
private fun PlanS7Row(item: PlannedItem, amountsVisible: Boolean) {
    val tone = planS7Tone(item.flow)
    val icon = planS7Icon(item)
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
                    items(included, key = { "forecast-${plannedItemSourceKey(it)}" }) { item ->
                        ForecastS7ItemCard(item = item, amountsVisible = amountsVisible, onRecordItem = onRecordItem)
                    }
                }
            }
        }
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
private fun ForecastS7ItemCard(item: PlannedItem, amountsVisible: Boolean, onRecordItem: (PlannedItem) -> Unit) {
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
                        MyFinHubIconBadge(planS7Icon(item), tone, null)
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
                    MyFinHubIconBadge(planS7Icon(item), tone, null)
                    PlanS7Identity(item, Modifier.weight(1f))
                    MyFinHubAmountText(planS7Amount(item, amountsVisible), tone)
                }
            }
            if (item.flow != PlannedFlow.TRANSFER) {
                TextButton(
                    onClick = { onRecordItem(item) },
                    modifier = Modifier.testTag("s7_forecast_record_${plannedItemSourceKey(item)}"),
                ) {
                    Text(if (item.flow == PlannedFlow.INCOME) "Καταχώριση εσόδου" else "Καταχώριση πληρωμής")
                }
            }
        }
    }
}

/** Overall canonical monthly budget only; unsupported category-budget drafts remain out of production. */
@Composable
fun CanonicalBudget2026Screen(
    state: PlanUiState,
    onSaveBudget: (String, String) -> Unit,
    onBack: () -> Unit,
    mutationInFlight: Boolean = false,
    mutationBlocked: Boolean = false,
) {
    val amountsVisible = rememberAmountVisibilityPreference()
    var savedLimit by rememberSaveable { mutableStateOf(state.budget.monthlyLimitText) }
    var savedThreshold by rememberSaveable { mutableStateOf(state.budget.alertThresholdText) }
    var limitDraft by rememberSaveable { mutableStateOf(state.budget.monthlyLimitText) }
    var thresholdDraft by rememberSaveable { mutableStateOf(state.budget.alertThresholdText) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var discardDialogOpen by rememberSaveable { mutableStateOf(false) }
    var saveRequested by rememberSaveable { mutableStateOf(false) }

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
                        val limit = limitDraft.replace(',', '.').toDoubleOrNull()
                        val threshold = thresholdDraft.toIntOrNull()
                        localError = when {
                            limit == null || limit <= 0.0 -> "Το μηνιαίο όριο πρέπει να είναι μεγαλύτερο από μηδέν."
                            threshold == null || threshold !in 1..100 -> "Το όριο προειδοποίησης πρέπει να είναι από 1 έως 100%."
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
                                "Από 1 έως 100% · επισημαίνει την πρόοδο μέσα στο MyFinHub",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            enabled = !mutationInFlight && !saveRequested,
                        )
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
        }
    }
}

internal fun budgetDraftEquivalent(first: BudgetDraft, second: BudgetDraft): Boolean {
    val firstLimit = first.monthlyLimitText.replace(',', '.').toDoubleOrNull()
    val secondLimit = second.monthlyLimitText.replace(',', '.').toDoubleOrNull()
    val sameLimit = when {
        firstLimit == null || secondLimit == null -> first.monthlyLimitText.trim() == second.monthlyLimitText.trim()
        else -> abs(firstLimit - secondLimit) < 0.005
    }
    return sameLimit && first.alertThresholdText.toIntOrNull() == second.alertThresholdText.toIntOrNull()
}

private fun planS7Tone(flow: PlannedFlow): FinanceTone = when (flow) {
    PlannedFlow.OBLIGATION -> FinanceTone.Expense
    PlannedFlow.INCOME -> FinanceTone.Income
    PlannedFlow.TRANSFER -> FinanceTone.Neutral
}

private fun planS7Icon(item: PlannedItem) = when (item.flow) {
    PlannedFlow.TRANSFER -> MyFinHubIcons.Transfer
    PlannedFlow.INCOME -> myFinHubCategoryIcon(item.category.ifBlank { item.title }, MyFinHubIcons.Income)
    PlannedFlow.OBLIGATION -> myFinHubCategoryIcon(item.category.ifBlank { item.title }, MyFinHubIcons.Plan)
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
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
