package app.myfinhub.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubActionCard
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSectionHeading
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.text.NumberFormat
import java.util.Locale

@Composable
fun Plan2026Screen(
    state: PlanUiState,
    onAction: (PlanAction) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenForecast: () -> Unit,
) {
    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Πλάνο",
                subtitle = "Υποχρεώσεις, budgets και πρόβλεψη",
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
                MyFinHubSectionHeading(
                    title = "Επόμενες υποχρεώσεις",
                    subtitle = "Προγραμματισμένες και επαναλαμβανόμενες κινήσεις",
                    icon = MyFinHubIcons.Plan,
                    tone = FinanceTone.Attention,
                )
            }
            items(state.items, key = PlannedItem::id) { item ->
                val tone = if (item.paused) FinanceTone.Neutral else FinanceTone.Expense
                MyFinHubActionCard(onClick = { onOpenItem(item.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MyFinHubIconBadge(
                            icon = if (item.kind == PlannedKind.RECURRING) MyFinHubIcons.Plan else MyFinHubIcons.Attention,
                            tone = tone,
                            contentDescription = null,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${item.dueLabel} · ${item.category}${if (item.paused) " · Σε παύση" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MyFinHubAmountText(formatEuroPlan2026(item.amount), tone)
                    }
                }
            }
            item {
                MyFinHubActionCard(onClick = onOpenBudgets, modifier = Modifier.fillMaxWidth()) {
                    MyFinHubSectionHeading(
                        title = "Budgets & κανόνες",
                        subtitle = "Μηνιαία όρια, κατηγορίες και ειδοποιήσεις",
                        icon = MyFinHubIcons.Savings,
                        tone = FinanceTone.Savings,
                    )
                    val overall = state.budget.monthlyLimitText.replace(',', '.').toDoubleOrNull()
                    overall?.let {
                        MyFinHubAmountText(
                            text = formatEuroPlan2026(it),
                            tone = FinanceTone.Savings,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
            item {
                MyFinHubActionCard(onClick = onOpenForecast, modifier = Modifier.fillMaxWidth()) {
                    MyFinHubSectionHeading(
                        title = "Ταμειακή πρόβλεψη",
                        subtitle = "Πώς αλλάζει το διαθέσιμο με τις επόμενες υποχρεώσεις",
                        icon = MyFinHubIcons.Insights,
                        tone = FinanceTone.Transfer,
                    )
                    MyFinHubAmountText(
                        text = formatEuroPlan2026(state.forecastEndBalance),
                        tone = if (state.forecastEndBalance >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            state.itemMessage?.let { message ->
                item { Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun PlanItemEditor2026Screen(
    item: PlannedItem?,
    onAction: (PlanAction) -> Unit,
    onBack: () -> Unit,
) {
    var title by remember(item?.id) { mutableStateOf(item?.title.orEmpty()) }
    var dueLabel by remember(item?.id) { mutableStateOf(item?.dueLabel.orEmpty()) }
    var amountText by remember(item?.id) { mutableStateOf(item?.amount?.toPlanInput2026().orEmpty()) }
    var kind by remember(item?.id) { mutableStateOf(item?.kind ?: PlannedKind.SCHEDULED) }
    var category by remember(item?.id) { mutableStateOf(item?.category.orEmpty()) }
    var accountLabel by remember(item?.id) { mutableStateOf(item?.accountLabel.orEmpty()) }
    var note by remember(item?.id) { mutableStateOf(item?.note.orEmpty()) }
    var validationError by remember(item?.id) { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Επεξεργασία",
                subtitle = item?.title ?: "Η υποχρέωση δεν είναι διαθέσιμη",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            if (item == null) {
                Text("Η υποχρέωση δεν είναι διαθέσιμη.")
            } else {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            PlannedKind.entries.forEach { option ->
                                FilterChip(
                                    selected = kind == option,
                                    onClick = { kind = option },
                                    label = { Text(if (option == PlannedKind.RECURRING) "Επαναλαμβανόμενη" else "Προγραμματισμένη") },
                                )
                            }
                        }
                        OutlinedTextField(value = title, onValueChange = { title = it; validationError = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Τίτλος") }, singleLine = true)
                        OutlinedTextField(value = dueLabel, onValueChange = { dueLabel = it; validationError = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Ημερομηνία") }, singleLine = true)
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it; validationError = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Ποσό") },
                            suffix = { Text("€") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                        )
                        OutlinedTextField(value = category, onValueChange = { category = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Κατηγορία") }, singleLine = true)
                        OutlinedTextField(value = accountLabel, onValueChange = { accountLabel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Λογαριασμός") }, singleLine = true)
                        OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Σημείωση") }, minLines = 3)
                        validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        MyFinHubPrimaryAction(
                            label = "Αποθήκευση",
                            onClick = {
                                val amount = amountText.replace(',', '.').toDoubleOrNull()
                                when {
                                    title.isBlank() -> validationError = "Συμπλήρωσε τίτλο."
                                    dueLabel.isBlank() -> validationError = "Συμπλήρωσε ημερομηνία."
                                    amount == null || amount <= 0.0 -> validationError = "Το ποσό πρέπει να είναι μεγαλύτερο από μηδέν."
                                    else -> {
                                        onAction(
                                            PlanAction.UpdatePlannedItem(
                                                id = item.id,
                                                title = title,
                                                dueLabel = dueLabel,
                                                amount = amount,
                                                kind = kind,
                                                category = category,
                                                accountLabel = accountLabel,
                                                note = note,
                                            ),
                                        )
                                        onBack()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            icon = MyFinHubIcons.Plan,
                        )
                        OutlinedButton(
                            onClick = { onAction(PlanAction.TogglePlannedItemPause(item.id)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (item.paused) "Επανενεργοποίηση" else "Παύση υποχρέωσης")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlanBudgets2026Screen(
    state: PlanUiState,
    onAction: (PlanAction) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Budgets & κανόνες",
                subtitle = "Όρια που παραμένουν εύκολα ελέγξιμα",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    MyFinHubSectionHeading(
                        title = "Συνολικό μηνιαίο budget",
                        icon = MyFinHubIcons.Savings,
                        tone = FinanceTone.Savings,
                    )
                    OutlinedTextField(
                        value = state.budget.monthlyLimitText,
                        onValueChange = { onAction(PlanAction.MonthlyLimitChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Μηνιαίο όριο") },
                        suffix = { Text("€") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.budget.alertThresholdText,
                        onValueChange = { onAction(PlanAction.AlertThresholdChanged(it.filter(Char::isDigit).take(3))) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ειδοποίηση στο") },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    MyFinHubPrimaryAction(
                        label = "Αποθήκευση συνολικού budget",
                        onClick = { onAction(PlanAction.SaveBudget) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = MyFinHubIcons.Savings,
                    )
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            Text("Budgets ανά κατηγορία", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            state.categoryBudgets.forEach { budget ->
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        ) {
                            MyFinHubIconBadge(MyFinHubIcons.Expense, FinanceTone.Expense, null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(budget.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Δαπάνη ${formatEuroPlan2026(budget.spent)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = budget.enabled,
                                onCheckedChange = { onAction(PlanAction.ToggleCategoryBudget(budget.id)) },
                            )
                        }
                        val limit = budget.monthlyLimitText.replace(',', '.').toDoubleOrNull()
                        if (limit != null && limit > 0.0) {
                            LinearProgressIndicator(
                                progress = { (budget.spent / limit).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        OutlinedTextField(
                            value = budget.monthlyLimitText,
                            onValueChange = { onAction(PlanAction.CategoryBudgetLimitChanged(budget.id, it)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = budget.enabled,
                            label = { Text("Όριο") },
                            suffix = { Text("€") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = budget.alertThresholdText,
                            onValueChange = { onAction(PlanAction.CategoryBudgetThresholdChanged(budget.id, it.filter(Char::isDigit).take(3))) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = budget.enabled,
                            label = { Text("Ειδοποίηση") },
                            suffix = { Text("%") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                }
            }
            MyFinHubPrimaryAction(
                label = "Αποθήκευση budgets",
                onClick = { onAction(PlanAction.SaveCategoryBudgets) },
                modifier = Modifier.fillMaxWidth(),
                icon = MyFinHubIcons.Savings,
            )
            state.categoryBudgetMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("Κανόνες σχεδιασμού", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    state.rules.forEachIndexed { index, rule ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.title, style = MaterialTheme.typography.titleMedium)
                                Text(rule.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { onAction(PlanAction.ToggleRule(rule.id)) },
                            )
                        }
                        if (index != state.rules.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun PlanForecast2026Screen(
    state: PlanUiState,
    onAction: (PlanAction) -> Unit,
    onBack: () -> Unit,
) {
    val selected = state.forecastWindows.firstOrNull { it.days == state.forecastHorizonDays }
        ?: state.forecastWindows.firstOrNull()

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Πρόβλεψη",
                subtitle = "Δες το επόμενο διάστημα χωρίς να χάνεις την αιτία",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                state.forecastWindows.forEach { window ->
                    FilterChip(
                        selected = state.forecastHorizonDays == window.days,
                        onClick = { onAction(PlanAction.ForecastHorizonChanged(window.days)) },
                        label = { Text(window.label) },
                    )
                }
            }
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    MyFinHubSectionHeading(
                        title = "Προβλεπόμενο διαθέσιμο",
                        icon = MyFinHubIcons.Insights,
                        tone = FinanceTone.Transfer,
                    )
                    MyFinHubAmountText(
                        text = formatEuroPlan2026(state.forecastEndBalance + (selected?.balanceDeltaFromThirtyDays ?: 0.0)),
                        tone = FinanceTone.Income,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text("Τι επηρεάζει την πρόβλεψη", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    ForecastMetric2026("Αναμενόμενα έσοδα", selected?.expectedIncome ?: 0.0, FinanceTone.Income)
                    ForecastMetric2026("Αναμενόμενες εκροές", selected?.expectedOutflow ?: 0.0, FinanceTone.Expense)
                    Text(
                        "Η πρόβλεψη συνυπολογίζει τις ενεργές υποχρεώσεις και τους κανόνες σχεδιασμού. Δεν αποτελεί υπόσχεση μελλοντικού υπολοίπου.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastMetric2026(label: String, value: Double, tone: FinanceTone) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        MyFinHubAmountText(formatEuroPlan2026(value), tone)
    }
}

private fun Double.toPlanInput2026(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.US, "%.2f", this)

private fun formatEuroPlan2026(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
