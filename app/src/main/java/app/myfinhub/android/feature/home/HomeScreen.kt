package app.myfinhub.android.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBrandMark
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedAction
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOpenAttention: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenChangeHistory: () -> Unit = {},
) {
    if (state.quickEntryOpen) {
        QuickEntrySheet(
            selectedType = state.selectedQuickEntryType,
            onSelect = { type -> onAction(HomeAction.SelectQuickEntry(type)) },
            onDismiss = { onAction(HomeAction.CloseQuickEntry) },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        val largeFont = LocalDensity.current.fontScale >= 1.3f
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                MyFinHubScreenHeader(
                    title = "MyFinHub",
                    subtitle = "Έξυπνα οικονομικά, κάθε μέρα.",
                    navigation = { MyFinHubBrandMark() },
                    trailing = {
                        TextButton(onClick = onOpenSettings) {
                            Text("Ρυθμίσεις")
                        }
                    },
                )
            },
            floatingActionButton = {
                if (!expanded) {
                    if (largeFont) {
                        FloatingActionButton(
                            onClick = { onAction(HomeAction.OpenQuickEntry) },
                        ) {
                            Icon(
                                imageVector = MyFinHubIcons.Add,
                                contentDescription = "Νέα κίνηση",
                            )
                        }
                    } else {
                        MyFinHubPrimaryAction(
                            label = "Νέα κίνηση",
                            onClick = { onAction(HomeAction.OpenQuickEntry) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            if (expanded) {
                HomeExpandedContent(
                    state = state,
                    onAction = onAction,
                    onOpenAttention = onOpenAttention,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            } else {
                HomeCompactContent(
                    state = state,
                    onAction = onAction,
                    onOpenAttention = onOpenAttention,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun HomeCompactContent(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOpenAttention: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("home_list"),
        contentPadding = PaddingValues(
            start = MyFinHubDesignMetrics.screenHorizontalPadding,
            top = MyFinHubSpacing.xs,
            end = MyFinHubDesignMetrics.screenHorizontalPadding,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
    ) {
        item { HomeHeading() }
        item { PositionCard(state, { onAction(HomeAction.ToggleAmounts) }) }
        item { AttentionCard(state.attentionItems, onOpenAttention) }
        item { UpcomingCard(state.upcomingItems, state.amountsVisible) }
        item { MonthFlowCard(state.monthFlow, state.amountsVisible) }
    }
}

@Composable
private fun HomeExpandedContent(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOpenAttention: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 28.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.lg),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            HomeHeading()
            PositionCard(state, { onAction(HomeAction.ToggleAmounts) })
            AccountsCard(state)
            MonthFlowCard(state.monthFlow, state.amountsVisible)
            Spacer(Modifier.height(88.dp))
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            AttentionCard(state.attentionItems, onOpenAttention)
            UpcomingCard(state.upcomingItems, state.amountsVisible)
            QuickEntryCard { onAction(HomeAction.OpenQuickEntry) }
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun HomeHeading() {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
        Text(
            "Η οικονομική σου εικόνα",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Τι έχεις διαθέσιμο, τι χρειάζεται προσοχή και τι ακολουθεί.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PositionCard(state: HomeUiState, onToggleAmounts: () -> Unit) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MyFinHubIconBadge(MyFinHubIcons.Account, FinanceTone.Savings, null)
                Column(modifier = Modifier.weight(1f)) {
                    Text("ΔΙΑΘΕΣΙΜΑ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Μετρητά και λογαριασμοί καθημερινής χρήσης",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                displayAmount(state.liquidTotal, state.amountsVisible),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics {
                    contentDescription = if (state.amountsVisible) {
                        "Διαθέσιμα ${formatEuro(state.liquidTotal)}"
                    } else {
                        "Διαθέσιμα ποσά κρυφά"
                    }
                },
            )
            TextButton(onClick = onToggleAmounts) {
                Text(if (state.amountsVisible) "Απόκρυψη ποσών" else "Εμφάνιση ποσών")
            }
        }
    }
}

@Composable
private fun AccountsCard(state: HomeUiState) {
    SectionCard("Λογαριασμοί", "Ρευστότητα και αποταμίευση") {
        state.accounts.forEachIndexed { index, account ->
            val tone = if (account.group == HomeAccountGroup.SAVINGS) FinanceTone.Savings else FinanceTone.Neutral
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MyFinHubIconBadge(
                    if (account.group == HomeAccountGroup.SAVINGS) MyFinHubIcons.Savings else MyFinHubIcons.Account,
                    tone,
                    null,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.name, style = MaterialTheme.typography.titleMedium)
                    Text(account.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(displayAmount(account.balance, state.amountsVisible), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (index != state.accounts.lastIndex) Divider()
        }
    }
}

@Composable
private fun AttentionCard(items: List<HomeAttentionItem>, onOpen: (String) -> Unit) {
    SectionCard("Χρειάζεται προσοχή", "Οι επόμενες χρήσιμες ενέργειες") {
        if (items.isEmpty()) {
            Text("Δεν υπάρχει κάτι που χρειάζεται άμεση ενέργεια.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    MyFinHubIconBadge(
                        MyFinHubIcons.Attention,
                        if (item.tone == HomeAttentionTone.URGENT) FinanceTone.Expense else FinanceTone.Attention,
                        "Χρειάζεται προσοχή",
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            item.dueLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.tone == HomeAttentionTone.URGENT) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        TextButton(
                            onClick = { onOpen(item.id) },
                            modifier = Modifier.testTag("attention-${item.id}"),
                        ) { Text("Έλεγχος") }
                    }
                }
                if (index != items.lastIndex) Divider()
            }
        }
    }
}

@Composable
private fun UpcomingCard(items: List<HomeUpcomingItem>, amountsVisible: Boolean) {
    SectionCard("Επόμενα", "Προγραμματισμένες υποχρεώσεις") {
        if (items.isEmpty()) {
            Text("Δεν υπάρχουν προγραμματισμένες υποχρεώσεις.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MyFinHubIconBadge(MyFinHubIcons.Plan, FinanceTone.Neutral, null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(displayAmount(item.amount, amountsVisible), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                if (index != items.lastIndex) Divider()
            }
        }
    }
}

@Composable
private fun QuickEntryCard(onOpen: () -> Unit) {
    SectionCard("Γρήγορη καταχώριση", "Καθημερινή κίνηση χωρίς περιττά βήματα") {
        MyFinHubPrimaryAction(
            label = "Επίλεξε τύπο κίνησης",
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MonthFlowCard(flow: HomeMonthFlow, amountsVisible: Boolean) {
    SectionCard("Αυτόν τον μήνα", "Η ροή με μια ματιά") {
        FlowMetric("Έσοδα", displayAmount(flow.income, amountsVisible), FinanceTone.Income, MyFinHubIcons.Income)
        FlowMetric("Έξοδα", displayAmount(flow.expense, amountsVisible), FinanceTone.Expense, MyFinHubIcons.Expense)
        FlowMetric("Αποταμίευση", displayAmount(flow.saving, amountsVisible), FinanceTone.Savings, MyFinHubIcons.Savings)
        Spacer(Modifier.height(MyFinHubSpacing.xxs))
        LinearProgressIndicator(
            progress = { flow.budgetProgress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
        )
        Text(
            "${(flow.budgetProgress * 100).toInt()}% του μηνιαίου προϋπολογισμού",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FlowMetric(
    label: String,
    value: String,
    tone: FinanceTone,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MyFinHubIconBadge(icon, tone, null)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MyFinHubAmountText(value, tone)
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(MyFinHubSpacing.micro))
            content()
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = MyFinHubSpacing.xs),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickEntrySheet(
    selectedType: HomeQuickEntryType?,
    onSelect: (HomeQuickEntryType) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        ) {
            Text("Γρήγορη καταχώριση", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Text("Επίλεξε πρώτα τον τύπο της κίνησης.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HomeQuickEntryType.entries.forEach { type ->
                if (selectedType == type) {
                    MyFinHubPrimaryAction(
                        label = type.label,
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = null,
                    )
                } else {
                    MyFinHubOutlinedAction(
                        label = type.label,
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            selectedType?.let { type ->
                Text("Επιλέχθηκε: ${type.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Κλείσιμο") }
        }
    }
}

private fun displayAmount(value: Double, visible: Boolean): String = if (visible) formatEuro(value) else "•••• €"

private fun formatEuro(value: Double): String {
    val symbols = DecimalFormatSymbols(Locale.forLanguageTag("el-GR"))
    return DecimalFormat("#,##0.00 '€'", symbols).format(value)
}
