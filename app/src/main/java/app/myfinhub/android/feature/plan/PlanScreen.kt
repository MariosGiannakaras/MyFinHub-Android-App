package app.myfinhub.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    state: PlanUiState,
    onAction: (PlanAction) -> Unit,
    onOpenItem: (String) -> Unit = {},
    onOpenBudgets: () -> Unit = {},
    onOpenForecast: () -> Unit = {},
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val activeItems = state.items.filterNot(PlannedItem::paused)
    val upcomingTotal = activeItems.sumOf(PlannedItem::amount)
    val enabledCategoryBudgets = state.categoryBudgets.count(CategoryBudget::enabled)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Πλάνο", modifier = Modifier.semantics { heading() }) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Επόμενο διάστημα", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${activeItems.size} ενεργές υποχρεώσεις · ${formatEuro(upcomingTotal)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Προβλεπόμενο διαθέσιμο: ${formatEuro(state.forecastEndBalance)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (largeFont) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onOpenBudgets, modifier = Modifier.fillMaxWidth()) {
                                    Text("Budgets & κανόνες")
                                }
                                Button(onClick = onOpenForecast, modifier = Modifier.fillMaxWidth()) {
                                    Text("Άνοιγμα forecast")
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onOpenBudgets, modifier = Modifier.weight(1f)) {
                                    Text("Budgets")
                                }
                                Button(onClick = onOpenForecast, modifier = Modifier.weight(1f)) {
                                    Text("Forecast")
                                }
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Επόμενες υποχρεώσεις") }
            items(state.items, key = PlannedItem::id) { item ->
                ElevatedCard(
                    onClick = { onOpenItem(item.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        if (largeFont) {
                            Text(item.title, fontWeight = FontWeight.SemiBold)
                            Text(plannedMeta(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatEuro(item.amount), fontWeight = FontWeight.SemiBold)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, fontWeight = FontWeight.SemiBold)
                                    Text(plannedMeta(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(formatEuro(item.amount), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (item.paused) {
                            Text("Σε παύση", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }

            state.itemMessage?.let { message ->
                item {
                    Text(
                        message,
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionTitle("Budgets & κανόνες") }
            item {
                ElevatedCard(
                    onClick = onOpenBudgets,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Συνολικό μηνιαίο όριο", style = MaterialTheme.typography.labelLarge)
                        Text(
                            state.budget.monthlyLimitText.toMoneyPreview(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "$enabledCategoryBudgets ενεργά category budgets · ${state.rules.count(PlanningRule::enabled)} ενεργοί κανόνες",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Ρύθμιση budgets και κανόνων", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item { SectionTitle("Πρόβλεψη ταμειακής ροής") }
            item {
                ElevatedCard(
                    onClick = onOpenForecast,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text("30 ημέρες", style = MaterialTheme.typography.labelLarge)
                        Text(
                            formatEuro(state.forecastEndBalance),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Δες 30/60/90 ημέρες, αναμενόμενες εισροές, εκροές και τις υποχρεώσεις που επηρεάζουν το αποτέλεσμα.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanItemEditorScreen(
    item: PlannedItem?,
    onAction: (PlanAction) -> Unit,
    onBack: () -> Unit,
) {
    var title by remember(item?.id) { mutableStateOf(item?.title.orEmpty()) }
    var amountText by remember(item?.id) { mutableStateOf(item?.amount?.let { moneyInput(it) }.orEmpty()) }
    var dueLabel by remember(item?.id) { mutableStateOf(item?.dueLabel.orEmpty()) }
    var category by remember(item?.id) { mutableStateOf(item?.category.orEmpty()) }
    var accountLabel by remember(item?.id) { mutableStateOf(item?.accountLabel.orEmpty()) }
    var note by remember(item?.id) { mutableStateOf(item?.note.orEmpty()) }
    var kind by remember(item?.id) { mutableStateOf(item?.kind ?: PlannedKind.RECURRING) }
    var validationError by remember(item?.id) { mutableStateOf<String?>(null) }
    val largeFont = LocalDensity.current.fontScale >= 1.3f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (item == null) "Υποχρέωση" else "Επεξεργασία") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Πίσω") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (item == null) {
                Text("Η υποχρέωση δεν είναι διαθέσιμη.")
                return@Column
            }

            Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(
                "Το edit flow είναι frontend-first. Οι αλλαγές παραμένουν στο Android UI μέχρι να συνδεθεί αργότερα το αντίστοιχο canonical write contract.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Τύπος", style = MaterialTheme.typography.titleMedium)
            if (largeFont) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindChip(kind == PlannedKind.RECURRING, "Επαναλαμβανόμενο") { kind = PlannedKind.RECURRING }
                    KindChip(kind == PlannedKind.SCHEDULED, "Προγραμματισμένο") { kind = PlannedKind.SCHEDULED }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindChip(kind == PlannedKind.RECURRING, "Επαναλαμβανόμενο") { kind = PlannedKind.RECURRING }
                    KindChip(kind == PlannedKind.SCHEDULED, "Προγραμματισμένο") { kind = PlannedKind.SCHEDULED }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it; validationError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Όνομα") },
                singleLine = true,
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it; validationError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ποσό") },
                suffix = { Text("€") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
            OutlinedTextField(
                value = dueLabel,
                onValueChange = { dueLabel = it; validationError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (kind == PlannedKind.RECURRING) "Επόμενη ημερομηνία / συχνότητα" else "Ημερομηνία") },
                singleLine = true,
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Κατηγορία") },
                singleLine = true,
            )
            OutlinedTextField(
                value = accountLabel,
                onValueChange = { accountLabel = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Λογαριασμός") },
                singleLine = true,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Σημείωση") },
                minLines = 3,
            )

            validationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Button(
                onClick = {
                    val amount = amountText.replace(',', '.').toDoubleOrNull()
                    when {
                        title.isBlank() -> validationError = "Συμπλήρωσε όνομα."
                        amount == null || amount <= 0.0 -> validationError = "Το ποσό πρέπει να είναι μεγαλύτερο από μηδέν."
                        dueLabel.isBlank() -> validationError = "Συμπλήρωσε ημερομηνία ή συχνότητα."
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
            ) {
                Text("Αποθήκευση draft")
            }

            OutlinedButton(
                onClick = { onAction(PlanAction.TogglePlannedItemPause(item.id)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (item.paused) "Επανενεργοποίηση" else "Παύση υποχρέωσης")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanBudgetsScreen(
    state: PlanUiState,
    onAction: (PlanAction) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets & κανόνες", modifier = Modifier.semantics { heading() }) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Πίσω") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle("Συνολικό μηνιαίο budget") }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.budget.monthlyLimitText,
                        onValueChange = { onAction(PlanAction.MonthlyLimitChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Συνολικό όριο") },
                        suffix = { Text("€") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.budget.alertThresholdText,
                        onValueChange = { onAction(PlanAction.AlertThresholdChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ειδοποίηση στο") },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    Button(onClick = { onAction(PlanAction.SaveBudget) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Αποθήκευση συνολικού budget")
                    }
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            item { SectionTitle("Budgets ανά κατηγορία") }
            items(state.categoryBudgets, key = CategoryBudget::id) { budget ->
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(budget.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Δαπάνες ${formatEuro(budget.spent)}",
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
                        if (budget.enabled && limit != null && limit > 0.0) {
                            LinearProgressIndicator(
                                progress = { (budget.spent / limit).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        OutlinedTextField(
                            value = budget.monthlyLimitText,
                            onValueChange = { onAction(PlanAction.CategoryBudgetLimitChanged(budget.id, it)) },
                            enabled = budget.enabled,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Μηνιαίο όριο") },
                            suffix = { Text("€") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = budget.alertThresholdText,
                            onValueChange = { onAction(PlanAction.CategoryBudgetThresholdChanged(budget.id, it)) },
                            enabled = budget.enabled,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Ειδοποίηση") },
                            suffix = { Text("%") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Button(onClick = { onAction(PlanAction.SaveCategoryBudgets) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Έλεγχος category budgets")
                    }
                    state.categoryBudgetMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { SectionTitle("Κανόνες σχεδιασμού") }
            items(state.rules, key = PlanningRule::id) { rule ->
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(rule.title, fontWeight = FontWeight.SemiBold)
                            Text(rule.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { onAction(PlanAction.ToggleRule(rule.id)) },
                        )
                    }
                }
            }
            item {
                Text(
                    "Οι category budgets και οι νέοι planning rules είναι αυτή τη στιγμή frontend drafts. Δεν δηλώνεται server persistence μέχρι το επόμενο backend integration pass.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanForecastScreen(
    state: PlanUiState,
    onAction: (PlanAction) -> Unit,
    onBack: () -> Unit,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val selected = state.forecastWindows.firstOrNull { it.days == state.forecastHorizonDays }
        ?: state.forecastWindows.first()
    val endBalance = state.forecastEndBalance + selected.balanceDeltaFromThirtyDays
    val netFlow = selected.expectedIncome - selected.expectedOutflow

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Forecast", modifier = Modifier.semantics { heading() }) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Πίσω") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Ορίζοντας", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (largeFont) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.forecastWindows.forEach { window ->
                                HorizonChip(window, state.forecastHorizonDays == window.days) {
                                    onAction(PlanAction.ForecastHorizonChanged(window.days))
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.forecastWindows.forEach { window ->
                                HorizonChip(window, state.forecastHorizonDays == window.days) {
                                    onAction(PlanAction.ForecastHorizonChanged(window.days))
                                }
                            }
                        }
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Προβλεπόμενο διαθέσιμο", style = MaterialTheme.typography.labelLarge)
                        Text(formatEuro(endBalance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (netFlow >= 0.0) "+${formatEuro(netFlow)} καθαρή μεταβολή" else "${formatEuro(netFlow)} καθαρή μεταβολή",
                            color = if (netFlow >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                        HorizontalDivider()
                        MetricRow("Αναμενόμενες εισροές", formatEuro(selected.expectedIncome), largeFont)
                        MetricRow("Αναμενόμενες εκροές", formatEuro(selected.expectedOutflow), largeFont)
                    }
                }
            }

            item { SectionTitle("Τι επηρεάζει το forecast") }
            items(state.items.filterNot(PlannedItem::paused).take(8), key = PlannedItem::id) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Medium)
                        Text(item.dueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("−${formatEuro(item.amount)}", fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                Text(
                    "Το forecast είναι UI εκτίμηση. Η τελική ενοποίηση θα χρησιμοποιήσει τα canonical recurring/scheduled δεδομένα χωρίς δεύτερη Android βάση αλήθειας.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun KindChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun HorizonChip(window: ForecastWindow, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(window.label) })
}

@Composable
private fun MetricRow(label: String, value: String, stacked: Boolean) {
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).semantics { heading() },
    )
}

private fun plannedMeta(item: PlannedItem): String =
    "${if (item.kind == PlannedKind.RECURRING) "Επαναλαμβανόμενο" else "Προγραμματισμένο"} · ${item.dueLabel} · ${item.category}"

private fun String.toMoneyPreview(): String =
    replace(',', '.').toDoubleOrNull()?.let(::formatEuro) ?: if (isBlank()) "Δεν έχει οριστεί" else "$this €"

private fun moneyInput(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value)

private fun formatEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
