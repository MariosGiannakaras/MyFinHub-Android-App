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
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

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
                    PlanS7Row(item = item)
                }
            }

            item("forecast-link") {
                ForecastS7LinkCard(state = state, onClick = onOpenForecast)
            }
            item("budget-link") {
                BudgetS7LinkCard(state = state, onClick = onOpenBudget)
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
                        PlanS7Row(item = item)
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
private fun ForecastS7LinkCard(state: PlanUiState, onClick: () -> Unit) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Προβλεπόμενο διαθέσιμο", style = MaterialTheme.typography.bodyMedium)
            MyFinHubAmountText(
                formatPlanS7Euro(state.forecastEndBalance),
                if (state.forecastEndBalance >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Text(
            "Άνοιγμα, έσοδα, υποχρεώσεις και επίδραση μεταφορών σε ξεχωριστή ανάλυση.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BudgetS7LinkCard(state: PlanUiState, onClick: () -> Unit) {
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
                    formatPlanS7Euro(abs(progress.remaining)),
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
private fun PlanS7Row(item: PlannedItem) {
    val tone = planS7Tone(item.flow)
    val icon = planS7Icon(item)
    val amountText = planS7Amount(item)
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
                            formatPlanS7Euro(state.forecastEndBalance),
                            if (state.forecastEndBalance >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        ForecastS7Metric("Άνοιγμα", state.forecastStartBalance, FinanceTone.Neutral)
                        ForecastS7Metric("Προγραμματισμένα έσοδα", state.forecastExpectedIncome, FinanceTone.Income, showPositive = true)
                        ForecastS7Metric("Υποχρεώσεις", -state.forecastObligations, FinanceTone.Expense)
                        ForecastS7Metric("Επίδραση μεταφορών", state.forecastTransferImpact, FinanceTone.Neutral, showPositive = true)
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
                        ForecastS7ItemCard(item = item, onRecordItem = onRecordItem)
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val text = when {
            amount < -0.005 -> "−${formatPlanS7Euro(abs(amount))}"
            amount > 0.005 && showPositive -> "+${formatPlanS7Euro(amount)}"
            else -> formatPlanS7Euro(abs(amount))
        }
        MyFinHubAmountText(text, tone)
    }
}

@Composable
private fun ForecastS7ItemCard(item: PlannedItem, onRecordItem: (PlannedItem) -> Unit) {
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
                    MyFinHubAmountText(planS7Amount(item), tone, modifier = Modifier.align(Alignment.End))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MyFinHubIconBadge(planS7Icon(item), tone, null)
                    PlanS7Identity(item, Modifier.weight(1f))
                    MyFinHubAmountText(planS7Amount(item), tone)
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
    onAction: (PlanAction) -> Unit,
    onBack: () -> Unit,
) {
    var savedLimit by rememberSaveable(state.budget.monthlyLimitText) { mutableStateOf(state.budget.monthlyLimitText) }
    var savedThreshold by rememberSaveable(state.budget.alertThresholdText) { mutableStateOf(state.budget.alertThresholdText) }
    var limitDraft by rememberSaveable(state.budget.monthlyLimitText) { mutableStateOf(state.budget.monthlyLimitText) }
    var thresholdDraft by rememberSaveable(state.budget.alertThresholdText) { mutableStateOf(state.budget.alertThresholdText) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var discardDialogOpen by rememberSaveable { mutableStateOf(false) }

    val dirty = limitDraft != savedLimit || thresholdDraft != savedThreshold
    val previewState = state.copy(budget = BudgetDraft(limitDraft, thresholdDraft))
    val progress = canonicalBudgetProgress(previewState)

    fun requestBack() {
        if (dirty) discardDialogOpen = true else onBack()
    }

    BackHandler(enabled = dirty) { discardDialogOpen = true }

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
                    label = "Αποθήκευση προϋπολογισμού",
                    onClick = {
                        val limit = limitDraft.replace(',', '.').toDoubleOrNull()
                        val threshold = thresholdDraft.toIntOrNull()
                        localError = when {
                            limit == null || limit <= 0.0 -> "Το μηνιαίο όριο πρέπει να είναι μεγαλύτερο από μηδέν."
                            threshold == null || threshold !in 1..100 -> "Το όριο προειδοποίησης πρέπει να είναι από 1 έως 100%."
                            else -> null
                        }
                        if (localError == null) {
                            onAction(PlanAction.MonthlyLimitChanged(limitDraft))
                            onAction(PlanAction.AlertThresholdChanged(thresholdDraft))
                            onAction(PlanAction.SaveBudget)
                            savedLimit = limitDraft
                            savedThreshold = thresholdDraft
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
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
                                subtitle = "${formatPlanS7Euro(progress.spent)} από ${formatPlanS7Euro(progress.limit)}",
                                icon = MyFinHubIcons.Savings,
                                tone = FinanceTone.Savings,
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (progress.remaining >= 0.0) "Υπόλοιπο" else "Υπέρβαση")
                                MyFinHubAmountText(
                                    formatPlanS7Euro(abs(progress.remaining)),
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
                            suffix = { Text("€") },
                            errorMessage = localError.takeIf { it?.contains("μηνιαίο όριο") == true },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        )
                        MyFinHubOutlinedField(
                            value = thresholdDraft,
                            onValueChange = {
                                thresholdDraft = it.filter(Char::isDigit).take(3)
                                localError = null
                            },
                            label = "Όριο προειδοποίησης",
                            suffix = { Text("%") },
                            errorMessage = localError.takeIf { it?.contains("προειδοποίησης") == true },
                            supportingText = if (localError?.contains("προειδοποίησης") == true) null else
                                "Από 1 έως 100% · επισημαίνει την πρόοδο μέσα στο MyFinHub",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        )
                        progress?.threshold?.let { threshold ->
                            Text(
                                "Η ένδειξη προόδου επισημαίνεται στο $threshold% του συνολικού ορίου.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.message?.let { message ->
                            Text(
                                planS7UserMessage(message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun planS7UserMessage(message: String): String = when {
    message.startsWith("Αλλαγή budget ·") && message.contains("Προς συγχρονισμό", ignoreCase = true) ->
        "Η αλλαγή αποθηκεύτηκε και θα συγχρονιστεί όταν υπάρχει σύνδεση."
    message.startsWith("Αλλαγή budget ·") && message.contains("Αναμονή επιβεβαίωσης", ignoreCase = true) ->
        "Η αλλαγή αποθηκεύτηκε και αναμένει επιβεβαίωση."
    else -> message.replace("budget", "προϋπολογισμός", ignoreCase = true)
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

private fun planS7Amount(item: PlannedItem): String = when (item.flow) {
    PlannedFlow.OBLIGATION -> "−${formatPlanS7Euro(abs(item.amount))}"
    PlannedFlow.INCOME -> "+${formatPlanS7Euro(abs(item.amount))}"
    PlannedFlow.TRANSFER -> formatPlanS7Euro(abs(item.amount))
}

private fun String?.toPlanS7DateOrNull(): LocalDate? = this
    ?.takeIf(String::isNotBlank)
    ?.let { raw -> runCatching { LocalDate.parse(raw.take(10)) }.getOrNull() }

private fun formatPlanS7Euro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
