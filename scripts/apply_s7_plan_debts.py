from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path.cwd()


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, text: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, got {count}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


def replace_between(path: str, start_marker: str, end_marker: str, replacement: str) -> None:
    text = read(path)
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"{path}: start marker not found: {start_marker!r}")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"{path}: end marker not found: {end_marker!r}")
    write(path, text[:start] + replacement + text[end:])


# ---------------------------------------------------------------------------
# S7 state/projection truth: canonical production forecast is exactly 30 days.
# ---------------------------------------------------------------------------
replace_once(
    "app/src/main/java/app/myfinhub/android/feature/plan/PlanUiState.kt",
    "    val forecastHorizonDays: Int = 30,\n    val forecastStartBalance: Double = 0.0,",
    "    val forecastHorizonDays: Int = 30,\n    val forecastStartDateIso: String = \"\",\n    val forecastEndDateIso: String = \"\",\n    val forecastStartBalance: Double = 0.0,",
)

replace_once(
    "app/src/main/java/app/myfinhub/android/app/CanonicalPlanProjection.kt",
    "    val horizonDays = previous?.forecastHorizonDays?.takeIf { it > 0 } ?: 30\n    val horizonEnd = today.plusDays(horizonDays.toLong())",
    "    // H7 production forecast is a fixed, explainable 30-day window. Legacy preview-only\n"
    "    // horizon controls must never change canonical production semantics.\n"
    "    val horizonDays = 30\n"
    "    val horizonEnd = today.plusDays(horizonDays.toLong())",
)
replace_once(
    "app/src/main/java/app/myfinhub/android/app/CanonicalPlanProjection.kt",
    "        forecastHorizonDays = horizonDays,\n        forecastStartBalance = startBalance,",
    "        forecastHorizonDays = horizonDays,\n"
    "        forecastStartDateIso = today.toString(),\n"
    "        forecastEndDateIso = horizonEnd.toString(),\n"
    "        forecastStartBalance = startBalance,",
)

# ---------------------------------------------------------------------------
# Navigation / production routing.
# ---------------------------------------------------------------------------
replace_once(
    "app/src/main/java/app/myfinhub/android/app/AppRoute.kt",
    "    @Serializable data object Plan : AppRoute\n    @Serializable data class PlanItem(val itemId: String) : AppRoute",
    "    @Serializable data object Plan : AppRoute\n"
    "    @Serializable data object PlanForecast : AppRoute\n"
    "    @Serializable data class PlanItem(val itemId: String) : AppRoute",
)

app_path = "app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt"
app = read(app_path)
app = app.replace(
    "import app.myfinhub.android.feature.plan.CanonicalBudgetScreen\nimport app.myfinhub.android.feature.plan.CanonicalPlanScreen",
    "import app.myfinhub.android.feature.plan.CanonicalBudget2026Screen\n"
    "import app.myfinhub.android.feature.plan.CanonicalPlan2026Screen\n"
    "import app.myfinhub.android.feature.plan.CanonicalPlanForecastScreen",
    1,
)
if "import app.myfinhub.android.feature.plan.PlannedFlow" not in app:
    app = app.replace(
        "import app.myfinhub.android.feature.plan.PlanViewModel\n",
        "import app.myfinhub.android.feature.plan.PlanViewModel\n"
        "import app.myfinhub.android.feature.plan.PlannedFlow\n",
        1,
    )
write(app_path, app)

plan_block = '''                entry<AppRoute.Plan> {
                    if (canonicalProductMode) {
                        CanonicalPlan2026Screen(
                            state = planState,
                            onOpenForecast = { planBackStack.pushIfNew(AppRoute.PlanForecast) },
                            onOpenBudget = { planBackStack.pushIfNew(AppRoute.PlanBudgets) },
                        )
                    } else {
                        Plan2026Screen(
                            state = planState,
                            onAction = onPlanAction,
                            onOpenItem = { itemId -> planBackStack.pushIfNew(AppRoute.PlanItem(itemId)) },
                            onOpenBudgets = { planBackStack.pushIfNew(AppRoute.PlanBudgets) },
                        )
                    }
                }
                entry<AppRoute.PlanForecast> {
                    CanonicalPlanForecastScreen(
                        state = planState,
                        onBack = { planBackStack.removeLastOrNull() },
                        onRecordItem = { item ->
                            val kind = when (item.flow) {
                                PlannedFlow.OBLIGATION -> QuickEntryKind.EXPENSE
                                PlannedFlow.INCOME -> QuickEntryKind.INCOME
                                PlannedFlow.TRANSFER -> null
                            }
                            if (kind != null) {
                                onQuickEntryAction(QuickEntryAction.Reset)
                                onQuickEntryAction(QuickEntryAction.SelectKind(kind))
                                onQuickEntryAction(QuickEntryAction.AmountChanged(item.amount.toString()))
                                if (item.dueDateIso.isNotBlank()) {
                                    onQuickEntryAction(QuickEntryAction.DateChanged(item.dueDateIso))
                                }
                                val categoryExists = when (kind) {
                                    QuickEntryKind.INCOME -> quickEntryState.incomeCategories
                                    else -> quickEntryState.expenseCategories
                                }.any { option -> option.name == item.category }
                                if (item.category.isNotBlank() && categoryExists) {
                                    onQuickEntryAction(QuickEntryAction.CategoryChanged(item.category))
                                }
                                onQuickEntryAction(QuickEntryAction.NoteChanged(item.note.ifBlank { item.title }))
                                planBackStack.pushIfNew(AppRoute.QuickEntry)
                            }
                        },
                    )
                }
'''
replace_between(
    app_path,
    "                entry<AppRoute.Plan> {",
    "                entry<AppRoute.PlanItem> {",
    plan_block,
)
replace_once(
    app_path,
    "                        CanonicalBudgetScreen(\n",
    "                        CanonicalBudget2026Screen(\n",
)

# Add contextual repayment only where a real canonical claim row exists.
app = read(app_path)
old_lending = '''                        CanonicalLendingScreen(
                            state = moneyState,
                            onBack = { moneyBackStack.removeLastOrNull() },
                        )'''
new_lending = '''                        CanonicalLendingScreen(
                            state = moneyState,
                            onBack = { moneyBackStack.removeLastOrNull() },
                            onRecordRepayment = { lending ->
                                onQuickEntryAction(QuickEntryAction.Reset)
                                onQuickEntryAction(QuickEntryAction.SelectKind(QuickEntryKind.REPAYMENT))
                                onQuickEntryAction(QuickEntryAction.AmountChanged(lending.amount.toString()))
                                onQuickEntryAction(QuickEntryAction.PersonChanged(lending.personLabel))
                                if (lending.note.isNotBlank()) {
                                    onQuickEntryAction(QuickEntryAction.NoteChanged(lending.note))
                                }
                                moneyBackStack.pushIfNew(AppRoute.QuickEntry)
                            },
                        )'''
if app.count(old_lending) != 1:
    raise SystemExit("MyFinHubApp: canonical lending route did not match exactly")
write(app_path, app.replace(old_lending, new_lending, 1))

# ---------------------------------------------------------------------------
# Wallet debts: honest aggregate-only labels + large-font amount layout.
# ---------------------------------------------------------------------------
wallet_path = "app/src/main/java/app/myfinhub/android/feature/money/WalletAccountsScreens.kt"
replace_once(
    wallet_path,
    "    initiallyShowCards: Boolean = false,\n    onOpenAccount: (String) -> Unit,",
    "    initiallyShowCards: Boolean = false,\n"
    "    initiallyShowDebts: Boolean = false,\n"
    "    onOpenAccount: (String) -> Unit,",
)
replace_once(
    wallet_path,
    '''    var selectedName by rememberSaveable(accountsRequest, initiallyShowCards) {
        mutableStateOf(if (initiallyShowCards) WalletSection.CARDS.name else WalletSection.ACCOUNTS.name)
    }''',
    '''    var selectedName by rememberSaveable(accountsRequest, initiallyShowCards, initiallyShowDebts) {
        mutableStateOf(
            when {
                initiallyShowDebts -> WalletSection.DEBTS.name
                initiallyShowCards -> WalletSection.CARDS.name
                else -> WalletSection.ACCOUNTS.name
            },
        )
    }''',
)
replace_once(
    wallet_path,
    'subtitle = if (state.aggregateCreditOutstanding != null) "Συνολικό canonical υπόλοιπο, μαζί με μη ενεργές κάρτες" else "Μερική εικόνα από τις διαθέσιμες κάρτες",',
    'subtitle = if (state.aggregateCreditOutstanding != null) "Συνολικό υπόλοιπο · περιλαμβάνει και μη ενεργές κάρτες" else "Μερική εικόνα από διαθέσιμες πιστωτικές",',
)
replace_once(
    wallet_path,
    'subtitle = if (state.loans.isEmpty()) "Συνολικό υπόλοιπο" else "${state.loans.size} διαθέσιμες εγγραφές",',
    'subtitle = if (state.loans.isEmpty()) "Συνολικό υπόλοιπο · χωρίς αναλυτικές εγγραφές" else "${state.loans.size} διαθέσιμες εγγραφές",',
)
replace_once(
    wallet_path,
    'subtitle = if (state.lendingItems.isEmpty()) "Ποσά που αναμένεις να επιστραφούν" else "${state.lendingItems.size} διαθέσιμες εγγραφές",',
    'subtitle = if (state.lendingItems.isEmpty()) "Συνολικό ποσό · χωρίς αναλυτικές εγγραφές" else "${state.lendingItems.size} διαθέσιμες εγγραφές",',
)
replace_once(
    wallet_path,
    '"Τα συνολικά ποσά παραμένουν χρήσιμα ακόμη κι όταν η canonical πηγή δεν παρέχει επιμέρους εγγραφές.",',
    '"Όπου δεν υπάρχουν αναλυτικές εγγραφές, εμφανίζεται το διαθέσιμο συνολικό ποσό χωρίς να υπονοείται μηδενική οφειλή ή απαίτηση.",',
)

old_aggregate = '''@Composable
private fun WalletAggregateRow(
    title: String,
    subtitle: String,
    amount: Double,
    amountsVisible: Boolean,
    onClick: () -> Unit,
    receivable: Boolean = false,
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.padding(vertical = MyFinHubSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MyFinHubIconBadge(if (receivable) MyFinHubIcons.Income else MyFinHubIcons.Plan, FinanceTone.Neutral, null)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MyFinHubAmountText(walletAmountText(amount, amountsVisible), FinanceTone.Neutral)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}'''
new_aggregate = '''@Composable
private fun WalletAggregateRow(
    title: String,
    subtitle: String,
    amount: Double,
    amountsVisible: Boolean,
    onClick: () -> Unit,
    receivable: Boolean = false,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        if (largeFont) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = MyFinHubSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    MyFinHubIconBadge(if (receivable) MyFinHubIcons.Income else MyFinHubIcons.Plan, FinanceTone.Neutral, null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                MyFinHubAmountText(
                    walletAmountText(amount, amountsVisible),
                    FinanceTone.Neutral,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(vertical = MyFinHubSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MyFinHubIconBadge(if (receivable) MyFinHubIcons.Income else MyFinHubIcons.Plan, FinanceTone.Neutral, null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MyFinHubAmountText(walletAmountText(amount, amountsVisible), FinanceTone.Neutral)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}'''
replace_once(wallet_path, old_aggregate, new_aggregate)

# ---------------------------------------------------------------------------
# Claim detail: only real rows get an existing Quick Entry repayment action.
# ---------------------------------------------------------------------------
money_path = "app/src/main/java/app/myfinhub/android/feature/money/CanonicalMoneyScreens.kt"
replace_once(
    money_path,
    '''fun CanonicalLendingScreen(
    state: MoneyUiState,
    onBack: () -> Unit,
) {''',
    '''fun CanonicalLendingScreen(
    state: MoneyUiState,
    onBack: () -> Unit,
    onRecordRepayment: (LendingItem) -> Unit = {},
) {''',
)
old_lending_card = '''                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MyFinHubIconBadge(MyFinHubIcons.Income, FinanceTone.Income, null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.personLabel, style = MaterialTheme.typography.titleMedium)
                                if (item.dueLabel.isNotBlank()) {
                                    Text(
                                        item.dueLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            MyFinHubAmountText(formatCanonicalEuro(item.amount), FinanceTone.Income)
                        }
                    }'''
new_lending_card = '''                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MyFinHubIconBadge(MyFinHubIcons.Income, FinanceTone.Income, null)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.personLabel, style = MaterialTheme.typography.titleMedium)
                                    if (item.dueLabel.isNotBlank()) {
                                        Text(
                                            item.dueLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                MyFinHubAmountText(formatCanonicalEuro(item.amount), FinanceTone.Income)
                            }
                            TextButton(onClick = { onRecordRepayment(item) }) {
                                Text("Καταχώριση επιστροφής")
                            }
                        }
                    }'''
replace_once(money_path, old_lending_card, new_lending_card)

# ---------------------------------------------------------------------------
# New production Plan / forecast / budget surfaces.
# ---------------------------------------------------------------------------
write(
    "app/src/main/java/app/myfinhub/android/feature/plan/CanonicalPlanS7Screens.kt",
    r'''package app.myfinhub.android.feature.plan

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
''',
)

# ---------------------------------------------------------------------------
# Unit contracts: hierarchy, stable source identity and fixed 30-day semantics.
# ---------------------------------------------------------------------------
write(
    "app/src/test/java/app/myfinhub/android/feature/plan/S7PlanPresentationTest.kt",
    r'''package app.myfinhub.android.feature.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S7PlanPresentationTest {
    private val state = PlanUiState(
        items = listOf(
            PlannedItem("late", "Ενοίκιο", "8 Σεπ", 680.0, PlannedKind.SCHEDULED, dueDateIso = "2026-09-08", urgency = PlannedUrgency.OVERDUE),
            PlannedItem("soon", "Internet", "12 Σεπ", 35.0, PlannedKind.RECURRING, dueDateIso = "2026-09-12", urgency = PlannedUrgency.THIS_WEEK),
            PlannedItem("later", "Ασφάλεια", "25 Σεπ", 120.0, PlannedKind.SCHEDULED, dueDateIso = "2026-09-25", urgency = PlannedUrgency.LATER),
            PlannedItem("income", "Μισθός", "15 Σεπ", 1_500.0, PlannedKind.SCHEDULED, flow = PlannedFlow.INCOME, dueDateIso = "2026-09-15", urgency = PlannedUrgency.THIS_WEEK),
            PlannedItem("outside", "Μελλοντικό", "20 Νοε", 40.0, PlannedKind.SCHEDULED, dueDateIso = "2026-11-20", urgency = PlannedUrgency.LATER),
        ),
        forecastHorizonDays = 30,
        forecastStartDateIso = "2026-09-10",
        forecastEndDateIso = "2026-10-10",
    )

    @Test
    fun urgentAndRemaining_doNotDuplicateCanonicalSources() {
        val urgent = planUrgentObligations(state)
        val remaining = planRemainingItems(state)
        assertEquals(setOf("late", "soon"), urgent.map { it.id }.toSet())
        assertFalse(remaining.any { item -> plannedItemSourceKey(item) in urgent.map(::plannedItemSourceKey) })
        assertEquals(state.items.map(::plannedItemSourceKey).toSet(), (urgent + remaining).map(::plannedItemSourceKey).toSet())
    }

    @Test
    fun stableSourceKey_keepsSameRawIdFromDifferentCanonicalSourcesDistinct() {
        val scheduled = PlannedItem("same", "Λογαριασμός", "12 Σεπ", 20.0, PlannedKind.SCHEDULED)
        val recurring = scheduled.copy(kind = PlannedKind.RECURRING)
        assertTrue(plannedItemSourceKey(scheduled) != plannedItemSourceKey(recurring))
    }

    @Test
    fun forecast_keepsEveryEligibleStableSource_withoutPresentationCap() {
        val many = (1..25).map { index ->
            PlannedItem("bill-$index", "Λογαριασμός", "12 Σεπ", 10.0, PlannedKind.SCHEDULED, dueDateIso = "2026-09-12")
        }
        val projected = state.copy(items = many + state.items.first { it.id == "outside" })
        val included = planForecastIncludedItems(projected)
        assertEquals(25, included.size)
        assertEquals(25, included.map(::plannedItemSourceKey).distinct().size)
        assertTrue(included.none { it.id == "outside" })
    }

    @Test
    fun forecastScope_exposesExactStartAndEndDates() {
        val label = planForecastScopeLabel(state)
        assertTrue(label.contains("10 Σεπ 2026"))
        assertTrue(label.contains("10 Οκτ 2026"))
    }
}
''',
)

write(
    "app/src/test/java/app/myfinhub/android/app/S7CanonicalPlanProjectionContractTest.kt",
    r'''package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.feature.plan.PlanUiState
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class S7CanonicalPlanProjectionContractTest {
    @Test
    fun productionForecast_ignoresLegacyPreviewHorizonAndStaysThirtyDays() {
        val state = projectCanonicalPlanState(
            document = fixture(),
            today = LocalDate.of(2026, 9, 10),
            previous = PlanUiState(forecastHorizonDays = 90),
        )

        assertEquals(30, state.forecastHorizonDays)
        assertEquals("2026-09-10", state.forecastStartDateIso)
        assertEquals("2026-10-10", state.forecastEndDateIso)
        assertEquals(100.0, state.forecastObligations, 0.001)
    }

    @Test
    fun scheduledAndRecurringWithSameRawId_areBothRetained() {
        val state = projectCanonicalPlanState(
            document = fixture(),
            today = LocalDate.of(2026, 9, 10),
            previous = null,
        )
        assertEquals(2, state.items.count { it.id == "same" })
    }

    private fun fixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(
        Json.parseToJsonElement(
            """
            {
              "schemaVersion":3,
              "seed":{
                "accounts":[{"id":"bank","name":"Κύριος","kind":"bank"}],
                "snapshots":[{"date":"2026-09-01","balances":{"bank":1000.0}}],
                "transactions":[],
                "recurring":[{"id":"same","name":"Internet","amount":40.0,"firstExpectedDate":"2026-09-12","active":true,"accountId":"bank"}],
                "loans":[],
                "lending":[]
              },
              "state":{
                "settings":{"excludedFromAvailable":[],"accountNames":{}},
                "events":[],
                "scheduled":[
                  {"id":"same","dueDate":"2026-09-12","kind":"expense","amount":60.0,"note":"Internet","accountId":"bank","status":"pending"},
                  {"id":"outside","dueDate":"2026-11-01","kind":"expense","amount":900.0,"accountId":"bank","status":"pending"}
                ],
                "cards":[],
                "budgets":[]
              }
            }
            """.trimIndent(),
        ).jsonObject,
    )
}
''',
)

# ---------------------------------------------------------------------------
# Instrumented contracts for root hierarchy, contextual recording, budget copy,
# aggregate-only debt labels and real-claim repayment routing.
# ---------------------------------------------------------------------------
write(
    "app/src/androidTest/java/app/myfinhub/android/feature/plan/S7PlanSurfaceTest.kt",
    r'''package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class S7PlanSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val obligation = PlannedItem(
        id = "rent",
        title = "Ενοίκιο",
        dueLabel = "8 Σεπ",
        amount = 680.0,
        kind = PlannedKind.SCHEDULED,
        flow = PlannedFlow.OBLIGATION,
        category = "Στέγαση",
        dueDateIso = "2026-09-08",
        urgency = PlannedUrgency.OVERDUE,
    )
    private val state = PlanUiState(
        items = listOf(obligation),
        budget = BudgetDraft("800", "80"),
        forecastHorizonDays = 30,
        forecastStartDateIso = "2026-09-10",
        forecastEndDateIso = "2026-10-10",
        forecastStartBalance = 1_200.0,
        forecastObligations = 680.0,
        forecastEndBalance = 520.0,
        budgetSpent = 700.0,
        budgetMonthLabel = "Σεπ 2026",
    )

    @Test
    fun plan_exposesUrgencyForecastAndBudget_asDecisionHierarchy() {
        composeRule.setContent { MyFinHubTheme { CanonicalPlan2026Screen(state, {}, {}) } }
        composeRule.onNodeWithText("Πρώτα").assertIsDisplayed()
        composeRule.onNodeWithTag("s7_forecast_link").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("s7_budget_link").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun forecast_contextualRecording_returnsExactCanonicalSource() {
        var recorded: PlannedItem? = null
        composeRule.setContent {
            MyFinHubTheme { CanonicalPlanForecastScreen(state, onBack = {}, onRecordItem = { recorded = it }) }
        }
        composeRule.onNodeWithTag("s7_forecast_expand").performClick()
        composeRule.onNodeWithTag("s7_forecast_record_SCHEDULED:rent").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("rent", recorded?.id) }
    }

    @Test
    fun budget_usesProgressWarning_withoutPushNotificationPromise() {
        composeRule.setContent { MyFinHubTheme { CanonicalBudget2026Screen(state, {}, {}) } }
        composeRule.onNodeWithTag("s7_budget_threshold_warning").assertIsDisplayed()
        composeRule.onNodeWithText("Θα ειδοποιείσαι", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Όριο προειδοποίησης").performScrollTo().assertIsDisplayed()
    }
}
''',
)

write(
    "app/src/androidTest/java/app/myfinhub/android/feature/money/S7DebtSurfaceTest.kt",
    r'''package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class S7DebtSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aggregateOnlyDebt_remainsVisibleWithoutInventingEmptyDetail() {
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalWalletScreen(
                    state = MoneyUiState(
                        loanOutstanding = 4_240.0,
                        lendingReceivable = 310.0,
                        aggregateCreditOutstanding = 875.0,
                    ),
                    initiallyShowDebts = true,
                    onOpenAccount = {},
                    onOpenNetPosition = {},
                    onOpenCard = {},
                    onAddCard = {},
                    onOpenLoans = {},
                    onOpenLending = {},
                    amountsVisibleOverride = true,
                )
            }
        }
        composeRule.onNodeWithText("Πιστωτικό χρέος").assertIsDisplayed()
        composeRule.onNodeWithText("Δάνεια").assertIsDisplayed()
        composeRule.onNodeWithText("Απαιτήσεις").assertIsDisplayed()
        composeRule.onNodeWithText("Συνολικό υπόλοιπο · χωρίς αναλυτικές εγγραφές").assertIsDisplayed()
    }

    @Test
    fun availableLendingItem_recordsRepaymentThroughExplicitAction() {
        var recorded: LendingItem? = null
        val item = LendingItem("lend-1", "Νίκος", 80.0, "20 Σεπ", "Καφές")
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalLendingScreen(
                    state = MoneyUiState(lendingReceivable = 80.0, lendingItems = listOf(item)),
                    onBack = {},
                    onRecordRepayment = { recorded = it },
                )
            }
        }
        composeRule.onNodeWithText("Καταχώριση επιστροφής").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("lend-1", recorded?.id) }
    }
}
''',
)

# ---------------------------------------------------------------------------
# Twelve fresh render candidates: Plan / Forecast / Budget / Wallet Debts,
# each light, dark and 150% font. References are intentionally not committed by
# this applicator; they require human visual inspection first.
# ---------------------------------------------------------------------------
write(
    "app/src/screenshotTest/kotlin/app/myfinhub/android/feature/plan/S7PlanScreenshotTest.kt",
    r'''package app.myfinhub.android.feature.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private fun s7PlanState() = PlanUiState(
    items = listOf(
        PlannedItem("rent", "Ενοίκιο", "8 Σεπ", 680.0, PlannedKind.SCHEDULED, category = "Στέγαση", accountLabel = "Πειραιώς Μισθοδοσίας", dueDateIso = "2026-09-08", urgency = PlannedUrgency.OVERDUE),
        PlannedItem("internet", "Internet", "12 Σεπ", 34.90, PlannedKind.RECURRING, category = "Λογαριασμοί", accountLabel = "Πειραιώς Μισθοδοσίας", dueDateIso = "2026-09-12", urgency = PlannedUrgency.THIS_WEEK),
        PlannedItem("salary", "Μισθός", "15 Σεπ", 1_650.0, PlannedKind.SCHEDULED, flow = PlannedFlow.INCOME, category = "Μισθός", accountLabel = "Πειραιώς Μισθοδοσίας", dueDateIso = "2026-09-15", urgency = PlannedUrgency.THIS_WEEK),
        PlannedItem("transfer", "Μεταφορά στην αποταμίευση", "18 Σεπ", 200.0, PlannedKind.SCHEDULED, flow = PlannedFlow.TRANSFER, accountLabel = "Από Πειραιώς Μισθοδοσίας → Προς Πειραιώς Αποταμίευση", dueDateIso = "2026-09-18", urgency = PlannedUrgency.LATER),
        PlannedItem("loan", "Δόση δανείου", "25 Σεπ", 185.0, PlannedKind.SCHEDULED, category = "Δάνειο", accountLabel = "Πειραιώς Μισθοδοσίας", dueDateIso = "2026-09-25", urgency = PlannedUrgency.LATER),
    ),
    budget = BudgetDraft("800", "80"),
    forecastHorizonDays = 30,
    forecastStartDateIso = "2026-09-10",
    forecastEndDateIso = "2026-10-10",
    forecastStartBalance = 1_695.0,
    forecastExpectedIncome = 1_650.0,
    forecastObligations = 899.90,
    forecastTransferImpact = 0.0,
    forecastEndBalance = 2_445.10,
    forecastEndDateLabel = "10 Οκτ 2026",
    budgetSpent = 680.0,
    budgetMonthLabel = "Σεπ 2026",
)

@PreviewTest
@Preview(name = "s7_plan_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun S7PlanLight() { MyFinHubTheme(false) { CanonicalPlan2026Screen(s7PlanState(), {}, {}) } }

@PreviewTest
@Preview(name = "s7_plan_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun S7PlanDark() { MyFinHubTheme(true) { CanonicalPlan2026Screen(s7PlanState(), {}, {}) } }

@PreviewTest
@Preview(name = "s7_plan_large", widthDp = 412, heightDp = 1200, fontScale = 1.5f, showBackground = true)
@Composable
fun S7PlanLarge() { MyFinHubTheme(false) { CanonicalPlan2026Screen(s7PlanState(), {}, {}) } }

@PreviewTest
@Preview(name = "s7_forecast_light", widthDp = 412, heightDp = 1100, showBackground = true)
@Composable
fun S7ForecastLight() { MyFinHubTheme(false) { CanonicalPlanForecastScreen(s7PlanState(), {}, {}) } }

@PreviewTest
@Preview(name = "s7_forecast_dark", widthDp = 412, heightDp = 1100, showBackground = true)
@Composable
fun S7ForecastDark() { MyFinHubTheme(true) { CanonicalPlanForecastScreen(s7PlanState(), {}, {}) } }

@PreviewTest
@Preview(name = "s7_forecast_large", widthDp = 412, heightDp = 1500, fontScale = 1.5f, showBackground = true)
@Composable
fun S7ForecastLarge() { MyFinHubTheme(false) { CanonicalPlanForecastScreen(s7PlanState(), {}, {}) } }

@PreviewTest
@Preview(name = "s7_budget_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun S7BudgetLight() { MyFinHubTheme(false) { CanonicalBudget2026Screen(s7PlanState(), {}, {}) } }

@PreviewTest
@Preview(name = "s7_budget_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun S7BudgetDark() { MyFinHubTheme(true) { CanonicalBudget2026Screen(s7PlanState(), {}, {}) } }

@PreviewTest
@Preview(name = "s7_budget_large", widthDp = 412, heightDp = 1100, fontScale = 1.5f, showBackground = true)
@Composable
fun S7BudgetLarge() { MyFinHubTheme(false) { CanonicalBudget2026Screen(s7PlanState(), {}, {}) } }
''',
)

write(
    "app/src/screenshotTest/kotlin/app/myfinhub/android/feature/money/S7DebtScreenshotTest.kt",
    r'''package app.myfinhub.android.feature.money

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private fun s7DebtState() = MoneyUiState(
    loanOutstanding = 4_240.0,
    lendingReceivable = 310.0,
    aggregateCreditOutstanding = 875.40,
)

@Composable
private fun DebtFixture(dark: Boolean) {
    MyFinHubTheme(dark) {
        CanonicalWalletScreen(
            state = s7DebtState(),
            initiallyShowDebts = true,
            onOpenAccount = {},
            onOpenNetPosition = {},
            onOpenCard = {},
            onAddCard = {},
            onOpenLoans = {},
            onOpenLending = {},
            amountsVisibleOverride = true,
        )
    }
}

@PreviewTest
@Preview(name = "s7_debts_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun S7DebtsLight() = DebtFixture(false)

@PreviewTest
@Preview(name = "s7_debts_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun S7DebtsDark() = DebtFixture(true)

@PreviewTest
@Preview(name = "s7_debts_large", widthDp = 412, heightDp = 1100, fontScale = 1.5f, showBackground = true)
@Composable
fun S7DebtsLarge() = DebtFixture(false)
''',
)

# ---------------------------------------------------------------------------
# Tracking: S7 candidate exists, but no subtask is completed before evidence.
# ---------------------------------------------------------------------------
tracking_path = ROOT / "tracking/android-project-state.json"
tracking = json.loads(tracking_path.read_text())
tracking["updated_at"] = "2026-09-14"
work = tracking["active_workstream"]
work["status"] = "android_redesign_s7_implementation_candidate"
work["summary"] = (
    "S1–S6 are complete. S7 now has one coherent Android-only implementation candidate for Plan urgency, "
    "the fixed 30-day forecast, overall monthly budget, Wallet debt/receivable consolidation and contextual "
    "repayment. S7 remains unaccepted until targeted tests, fresh Compose render inspection and the standard "
    "exact-PR-head gates pass."
)
work["next"] = [
    "Run targeted unit/instrumentation/lint/compile checks and render the twelve S7 light/dark/150% candidates.",
    "Open the S7 draft PR only after the coherent candidate compiles, personally inspect all new renders, then run exact-head Android CI, Project Tracking and Android UI Quality/S24 gates.",
]
current = tracking["current_redesign_pass"]
current["branch"] = "android/redesign-s7-plan-debts"
current["pr"] = None
current["current_slice"] = "S7.1 / S7.2 / S7.3 / S7.4"
current["checkpoint"] = "s7_implementation_candidate"
current["next_action"] = (
    "Validate the coherent S7 candidate, inspect all twelve fresh Plan/forecast/budget/debt renders, then publish "
    "a draft PR and complete S7 only from exact-head hosted evidence."
)
tracking_path.write_text(json.dumps(tracking, ensure_ascii=False, indent=2) + "\n")

# Generated mirrors must match canonical tracking.
import subprocess
subprocess.run(["python3", "scripts/render_project_tracking.py"], check=True)
