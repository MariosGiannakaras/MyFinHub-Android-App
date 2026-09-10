package app.myfinhub.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubActionCard
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubHeroCard
import app.myfinhub.android.designsystem.MyFinHubHeroHeading
import app.myfinhub.android.designsystem.MyFinHubHeroMetric
import app.myfinhub.android.designsystem.MyFinHubHeroValue
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Production plan surface: canonical upcoming cash flow, canonical overall budget, and derived forecast. */
@Composable
fun CanonicalPlanScreen(
    state: PlanUiState,
    onOpenBudget: () -> Unit,
) {
    val obligations = state.items.filter { it.flow == PlannedFlow.OBLIGATION }
    val overdue = obligations.filter { it.urgency == PlannedUrgency.OVERDUE }
    val thisWeek = obligations.filter { it.urgency == PlannedUrgency.THIS_WEEK }
    val later = obligations.filter { it.urgency == PlannedUrgency.LATER }
    val expectedIncome = state.items.filter { it.flow == PlannedFlow.INCOME }
    val transfers = state.items.filter { it.flow == PlannedFlow.TRANSFER }
    val largeFont = LocalDensity.current.fontScale >= 1.3f

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Πλάνο",
                subtitle = "Τι έρχεται και πού καταλήγεις",
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("plan_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = MyFinHubSpacing.lg,
                top = MyFinHubSpacing.xs,
                end = MyFinHubSpacing.lg,
                bottom = MyFinHubSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            item {
                PlanForecastCard(state = state, largeFont = largeFont)
            }

            if (obligations.isEmpty()) {
                item {
                    PlannedFlowSection(
                        title = "Υποχρεώσεις",
                        subtitle = "Δεν υπάρχει καταγεγραμμένη πληρωμή που εκκρεμεί.",
                        items = emptyList(),
                        emptyMessage = "Δεν υπάρχουν επόμενες υποχρεώσεις.",
                        tone = FinanceTone.Expense,
                    )
                }
            } else {
                if (overdue.isNotEmpty()) {
                    item {
                        PlannedFlowSection(
                            title = "Καθυστερημένα",
                            subtitle = "Εκκρεμούν ήδη και χρειάζονται πρώτα προσοχή",
                            items = overdue,
                            emptyMessage = "",
                            tone = FinanceTone.Expense,
                        )
                    }
                }
                if (thisWeek.isNotEmpty()) {
                    item {
                        PlannedFlowSection(
                            title = "Αυτή την εβδομάδα",
                            subtitle = "Υποχρεώσεις των επόμενων 7 ημερών",
                            items = thisWeek,
                            emptyMessage = "",
                            tone = FinanceTone.Expense,
                        )
                    }
                }
                if (later.isNotEmpty()) {
                    item {
                        PlannedFlowSection(
                            title = "Αργότερα",
                            subtitle = "Επόμενες καταγεγραμμένες υποχρεώσεις",
                            items = later,
                            emptyMessage = "",
                            tone = FinanceTone.Expense,
                        )
                    }
                }
            }

            if (expectedIncome.isNotEmpty()) {
                item {
                    PlannedFlowSection(
                        title = "Αναμενόμενα έσοδα",
                        subtitle = "Εισροές που συμμετέχουν στην πρόβλεψη όταν είναι μέσα στον ορίζοντα",
                        items = expectedIncome,
                        emptyMessage = "",
                        tone = FinanceTone.Income,
                    )
                }
            }

            if (transfers.isNotEmpty()) {
                item {
                    PlannedFlowSection(
                        title = "Προγραμματισμένες μεταφορές",
                        subtitle = "Εσωτερικές μετακινήσεις χρημάτων · όχι έσοδα ή δαπάνες",
                        items = transfers,
                        emptyMessage = "",
                        tone = FinanceTone.Neutral,
                    )
                }
            }

            item {
                PlanBudgetOverviewCard(state = state, onOpenBudget = onOpenBudget)
            }
        }
    }
}

@Composable
private fun PlanForecastCard(
    state: PlanUiState,
    largeFont: Boolean,
) {
    val endLabel = state.forecastEndDateLabel.ifBlank { "τον ορίζοντα των ${state.forecastHorizonDays} ημερών" }
    MyFinHubHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            MyFinHubHeroHeading(
                eyebrow = "Πρόβλεψη ${state.forecastHorizonDays} ημερών",
                title = "Έως $endLabel",
                supporting = "Τρέχον διαθέσιμο + έσοδα − υποχρεώσεις ± επίδραση μεταφορών",
            )
            MyFinHubHeroValue(formatCanonicalPlanEuro(state.forecastEndBalance))
            if (largeFont) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    MyFinHubHeroMetric("Τώρα", formatCanonicalPlanEuro(state.forecastStartBalance))
                    MyFinHubHeroMetric("Υποχρεώσεις", formatSignedCanonicalPlanEuro(-state.forecastObligations))
                    MyFinHubHeroMetric("Αναμενόμενα έσοδα", formatSignedCanonicalPlanEuro(state.forecastExpectedIncome, showPositiveSign = true))
                    MyFinHubHeroMetric("Μεταφορές", formatSignedCanonicalPlanEuro(state.forecastTransferImpact, showPositiveSign = true))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                ) {
                    MyFinHubHeroMetric("Τώρα", formatCanonicalPlanEuro(state.forecastStartBalance), Modifier.weight(1f))
                    MyFinHubHeroMetric(
                        "Υποχρεώσεις",
                        formatSignedCanonicalPlanEuro(-state.forecastObligations),
                        Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                ) {
                    MyFinHubHeroMetric(
                        "Έσοδα",
                        formatSignedCanonicalPlanEuro(state.forecastExpectedIncome, showPositiveSign = true),
                        Modifier.weight(1f),
                    )
                    MyFinHubHeroMetric(
                        "Μεταφορές",
                        formatSignedCanonicalPlanEuro(state.forecastTransferImpact, showPositiveSign = true),
                        Modifier.weight(1f),
                    )
                }
            }
            Text(
                "Το προβλεπόμενο διαθέσιμο προκύπτει μόνο από καταγεγραμμένες κινήσεις μέσα στον ορίζοντα.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun PlannedFlowSection(
    title: String,
    subtitle: String,
    items: List<PlannedItem>,
    emptyMessage: String,
    tone: FinanceTone,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
        MyFinHubSectionHeading(
            title = title,
            subtitle = subtitle,
            icon = when {
                items.firstOrNull()?.flow == PlannedFlow.INCOME -> MyFinHubIcons.Income
                items.firstOrNull()?.flow == PlannedFlow.TRANSFER -> MyFinHubIcons.Transfer
                else -> MyFinHubIcons.Plan
            },
            tone = tone,
        )
        MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
            if (items.isEmpty()) {
                Text(
                    emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    items.forEachIndexed { index, item ->
                        PlannedFlowRow(item = item, tone = tone)
                        if (index != items.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedFlowRow(
    item: PlannedItem,
    tone: FinanceTone,
) {
    val fallbackIcon = when (item.flow) {
        PlannedFlow.INCOME -> MyFinHubIcons.Income
        PlannedFlow.TRANSFER -> MyFinHubIcons.Transfer
        PlannedFlow.OBLIGATION -> if (item.kind == PlannedKind.RECURRING) MyFinHubIcons.Plan else MyFinHubIcons.Attention
    }
    val icon = if (item.flow == PlannedFlow.TRANSFER) {
        fallbackIcon
    } else {
        myFinHubCategoryIcon(item.category.ifBlank { item.title }, fallbackIcon)
    }
    val sourceLabel = when (item.kind) {
        PlannedKind.RECURRING -> "Επαναλαμβανόμενο"
        PlannedKind.SCHEDULED -> "Προγραμματισμένο"
    }
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val amountText = when (item.flow) {
        PlannedFlow.OBLIGATION -> formatSignedCanonicalPlanEuro(-abs(item.amount))
        PlannedFlow.INCOME -> formatSignedCanonicalPlanEuro(abs(item.amount), showPositiveSign = true)
        PlannedFlow.TRANSFER -> formatCanonicalPlanEuro(abs(item.amount))
    }

    Row(
        modifier = Modifier
  .fillMaxWidth()
  .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MyFinHubIconBadge(
  icon = icon,
  tone = tone,
  contentDescription = null,
        )
        Column(modifier = Modifier.weight(1f)) {
  Text(
      item.title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
  )
  Text(
      "${item.dueLabel} · $sourceLabel",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  if (item.accountLabel.isNotBlank()) {
      if (item.flow == PlannedFlow.TRANSFER && " → Προς " in item.accountLabel) {
val route = item.accountLabel.split(" → Προς ", limit = 2)
Text(
    route.first(),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = if (largeFont) 2 else 1,
)
Text(
    "→ Προς ${route[1]}",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = if (largeFont) 2 else 1,
)
      } else {
Text(
    item.accountLabel,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = if (largeFont) 2 else 1,
)
      }
  }
  if (largeFont) {
      MyFinHubAmountText(
text = amountText,
tone = tone,
modifier = Modifier
    .align(Alignment.End)
    .padding(top = MyFinHubSpacing.xxs),
      )
  }
        }
        if (!largeFont) {
  MyFinHubAmountText(
      text = amountText,
      tone = tone,
  )
        }
    }
}

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

@Composable
private fun PlanBudgetOverviewCard(
    state: PlanUiState,
    onOpenBudget: () -> Unit,
) {
    MyFinHubActionCard(onClick = onOpenBudget, modifier = Modifier.fillMaxWidth()) {
        MyFinHubSectionHeading(
            title = "Μηνιαίος προϋπολογισμός",
            subtitle = state.budgetMonthLabel.ifBlank { "Τρέχων μήνας" },
            icon = MyFinHubIcons.Savings,
            tone = FinanceTone.Savings,
        )
        val progress = canonicalBudgetProgress(state)
        if (progress != null) {
            PlanBudgetProgressContent(progress = progress)
        } else {
            Text(
                "Δεν έχει οριστεί συνολικό μηνιαίο όριο.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanBudgetProgressContent(progress: CanonicalBudgetProgress) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
    ) {
        PlanBudgetMetric(
            label = "Δαπάνες",
            value = formatCanonicalPlanEuro(progress.spent),
            tone = FinanceTone.Expense,
            modifier = Modifier.weight(1f),
        )
        PlanBudgetMetric(
            label = if (progress.remaining >= 0.0) "Υπόλοιπο" else "Υπέρβαση",
            value = formatCanonicalPlanEuro(abs(progress.remaining)),
            tone = if (progress.remaining >= 0.0) FinanceTone.Savings else FinanceTone.Expense,
            modifier = Modifier.weight(1f),
        )
    }
    LinearProgressIndicator(
        progress = { progress.progress },
        modifier = Modifier.fillMaxWidth(),
        color = if (progress.thresholdReached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
    Text(
        buildString {
            append("${progress.percent}% του ορίου")
            progress.threshold?.let { append(" · ειδοποίηση στο $it%") }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (progress.thresholdReached) {
        Text(
            "Έχεις φτάσει το όριο ειδοποίησης.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PlanBudgetMetric(
    label: String,
    value: String,
    tone: FinanceTone,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MyFinHubAmountText(
            text = value,
            tone = tone,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** The only production-editable Plan surface until other canonical mutations are defined. */
@Composable
fun CanonicalBudgetScreen(
    state: PlanUiState,
    onAction: (PlanAction) -> Unit,
    onBack: () -> Unit,
) {
    val limit = state.budget.monthlyLimitText.replace(',', '.').toDoubleOrNull()
    val threshold = state.budget.alertThresholdText.toIntOrNull()
    val limitError = state.message == "Το μηνιαίο όριο πρέπει να είναι μεγαλύτερο από μηδέν."
    val thresholdError = state.message == "Το όριο ειδοποίησης πρέπει να είναι από 1 έως 100%."

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Μηνιαίος προϋπολογισμός",
                subtitle = state.budgetMonthLabel.ifBlank { "Συνολικό όριο τρέχοντος μήνα" },
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            item {
                val progress = canonicalBudgetProgress(state)
                if (progress != null) {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                            MyFinHubSectionHeading(
                                title = "Πρόοδος μήνα",
                                subtitle = "Δαπάνες σε σχέση με το συνολικό όριο",
                                icon = MyFinHubIcons.Savings,
                                tone = FinanceTone.Savings,
                            )
                            PlanBudgetProgressContent(progress = progress)
                        }
                    }
                }
            }
            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        MyFinHubSectionHeading(
                            title = "Συνολικό μηνιαίο όριο",
                            subtitle = "Ένα όριο για όλες τις καταγεγραμμένες δαπάνες του μήνα",
                            icon = MyFinHubIcons.Savings,
                            tone = FinanceTone.Savings,
                        )
                        MyFinHubOutlinedField(
                            value = state.budget.monthlyLimitText,
                            onValueChange = { onAction(PlanAction.MonthlyLimitChanged(it)) },
                            label = "Μηνιαίο όριο",
                            suffix = { Text("€") },
                            errorMessage = state.message.takeIf { limitError },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        MyFinHubOutlinedField(
                            value = state.budget.alertThresholdText,
                            onValueChange = {
                                onAction(PlanAction.AlertThresholdChanged(it.filter(Char::isDigit).take(3)))
                            },
                            label = "Ειδοποίηση στο",
                            suffix = { Text("%") },
                            errorMessage = state.message.takeIf { thresholdError },
                            supportingText = if (thresholdError) null else "Από 1 έως 100%",
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                        )
                        if (limit != null && limit > 0.0 && threshold != null && threshold in 1..100) {
                            Text(
                                "Θα ειδοποιείσαι όταν οι δαπάνες φτάσουν το $threshold% των ${formatCanonicalPlanEuro(limit)}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MyFinHubPrimaryAction(
                            label = "Αποθήκευση προϋπολογισμού",
                            onClick = { onAction(PlanAction.SaveBudget) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = MyFinHubIcons.Savings,
                        )
                        state.message?.takeUnless { limitError || thresholdError }?.let { message ->
                            Text(
                                planUserMessage(message),
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

private fun planUserMessage(message: String): String = when {
    message.startsWith("Αλλαγή budget ·") && message.contains("Προς συγχρονισμό", ignoreCase = true) ->
        "Η αλλαγή αποθηκεύτηκε και θα συγχρονιστεί όταν υπάρχει σύνδεση."
    message.startsWith("Αλλαγή budget ·") && message.contains("Αναμονή επιβεβαίωσης", ignoreCase = true) ->
        "Η αλλαγή αποθηκεύτηκε και αναμένει επιβεβαίωση."
    else -> message.replace("budget", "προϋπολογισμός", ignoreCase = true)
}

private fun formatSignedCanonicalPlanEuro(
    value: Double,
    showPositiveSign: Boolean = false,
): String {
    val absolute = formatCanonicalPlanEuro(abs(value))
    return when {
        value < -0.005 -> "−$absolute"
        value > 0.005 && showPositiveSign -> "+$absolute"
        else -> absolute
    }
}

private fun formatCanonicalPlanEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
