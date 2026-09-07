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
import java.text.NumberFormat
import java.util.Locale

/** Production plan surface: canonical upcoming cash flow, canonical overall budget, and derived forecast. */
@Composable
fun CanonicalPlanScreen(
    state: PlanUiState,
    onOpenBudget: () -> Unit,
) {
    val obligations = state.items.filter { it.flow == PlannedFlow.OBLIGATION }
    val expectedIncome = state.items.filter { it.flow == PlannedFlow.INCOME }
    val transfers = state.items.filter { it.flow == PlannedFlow.TRANSFER }
    val obligationsTotal = obligations.sumOf(PlannedItem::amount)
    val incomeTotal = expectedIncome.sumOf(PlannedItem::amount)
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
                PlanSnapshotCard(
                    obligationsTotal = obligationsTotal,
                    incomeTotal = incomeTotal,
                    forecastEndBalance = state.forecastEndBalance,
                    largeFont = largeFont,
                )
            }

            item {
                PlannedFlowSection(
                    title = "Επόμενες υποχρεώσεις",
                    subtitle = "Προγραμματισμένα έξοδα και επαναλαμβανόμενες χρεώσεις",
                    items = obligations,
                    emptyMessage = "Δεν υπάρχουν επόμενες καταγεγραμμένες υποχρεώσεις.",
                    tone = FinanceTone.Expense,
                )
            }

            if (expectedIncome.isNotEmpty()) {
                item {
                    PlannedFlowSection(
                        title = "Αναμενόμενα έσοδα",
                        subtitle = "Προγραμματισμένες εισροές που υπάρχουν στα συγχρονισμένα δεδομένα",
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
                        subtitle = "Μετακινήσεις χρημάτων μεταξύ λογαριασμών, όχι δαπάνες",
                        items = transfers,
                        emptyMessage = "",
                        tone = FinanceTone.Transfer,
                    )
                }
            }

            item {
                MyFinHubActionCard(onClick = onOpenBudget, modifier = Modifier.fillMaxWidth()) {
                    MyFinHubSectionHeading(
                        title = "Μηνιαίο budget",
                        subtitle = "Συνολικό όριο και έγκαιρη ειδοποίηση",
                        icon = MyFinHubIcons.Savings,
                        tone = FinanceTone.Savings,
                    )
                    val amount = state.budget.monthlyLimitText.replace(',', '.').toDoubleOrNull()
                    if (amount != null && amount > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            MyFinHubAmountText(
                                text = formatCanonicalPlanEuro(amount),
                                tone = FinanceTone.Savings,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "Alert ${state.budget.alertThresholdText}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            "Δεν έχει οριστεί συνολικό budget.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.message?.takeIf { it.startsWith("Αλλαγή budget ·") }?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun PlanSnapshotCard(
    obligationsTotal: Double,
    incomeTotal: Double,
    forecastEndBalance: Double,
    largeFont: Boolean,
) {
    MyFinHubHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md)) {
            MyFinHubHeroHeading(
                eyebrow = "Πρόβλεψη",
                title = "Μετά τις επόμενες κινήσεις",
                supporting = "Αναμενόμενο διαθέσιμο με βάση το συγχρονισμένο πλάνο",
            )
            MyFinHubHeroValue(formatCanonicalPlanEuro(forecastEndBalance))
            if (largeFont) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    MyFinHubHeroMetric("Υποχρεώσεις", formatCanonicalPlanEuro(obligationsTotal))
                    MyFinHubHeroMetric("Αναμενόμενα έσοδα", formatCanonicalPlanEuro(incomeTotal))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                ) {
                    MyFinHubHeroMetric("Υποχρεώσεις", formatCanonicalPlanEuro(obligationsTotal), Modifier.weight(1f))
                    MyFinHubHeroMetric("Έσοδα", formatCanonicalPlanEuro(incomeTotal), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlanSnapshotMetric(label: String, amount: Double, tone: FinanceTone) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    MyFinHubAmountText(
        text = formatCanonicalPlanEuro(amount),
        tone = tone,
        style = MaterialTheme.typography.titleMedium,
    )
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
            icon = when (tone) {
                FinanceTone.Income -> MyFinHubIcons.Income
                FinanceTone.Transfer -> MyFinHubIcons.Transfer
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {},
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MyFinHubIconBadge(
                                icon = when (item.flow) {
                                    PlannedFlow.INCOME -> MyFinHubIcons.Income
                                    PlannedFlow.TRANSFER -> MyFinHubIcons.Transfer
                                    PlannedFlow.OBLIGATION -> if (item.kind == PlannedKind.RECURRING) {
                                        MyFinHubIcons.Plan
                                    } else {
                                        MyFinHubIcons.Attention
                                    }
                                },
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
                                    item.dueLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MyFinHubAmountText(
                                text = formatCanonicalPlanEuro(item.amount),
                                tone = tone,
                            )
                        }
                        if (index != items.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
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
                title = "Μηνιαίο budget",
                subtitle = "Συγχρονισμένο συνολικό όριο",
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
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        MyFinHubSectionHeading(
                            title = "Συνολικό μηνιαίο όριο",
                            subtitle = "Αφορά το συνολικό budget, όχι προσωρινά budgets ανά κατηγορία",
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
                                "Θα ειδοποιείσαι όταν οι δαπάνες πλησιάσουν το $threshold% των ${formatCanonicalPlanEuro(limit)}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MyFinHubPrimaryAction(
                            label = "Αποθήκευση budget",
                            onClick = { onAction(PlanAction.SaveBudget) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = MyFinHubIcons.Savings,
                        )
                        state.message?.takeUnless { limitError || thresholdError }?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text(
                            "Budgets ανά κατηγορία και κανόνες",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Δεν εμφανίζονται editable controls μέχρι να υπάρχει αντίστοιχη canonical αποθήκευση. Έτσι μια τοπική αλλαγή δεν μπορεί να παρουσιαστεί κατά λάθος ως συγχρονισμένη.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatCanonicalPlanEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)