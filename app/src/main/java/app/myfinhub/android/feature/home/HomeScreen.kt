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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
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
    onOpenDataTransfer: () -> Unit = {},
    onOpenChangeHistory: () -> Unit = {},
) {
    if (state.quickEntryOpen) {
        QuickEntrySheet(
            selectedType = state.selectedQuickEntryType,
            onSelect = { type -> onAction(HomeAction.SelectQuickEntry(type)) },
            onDismiss = { onAction(HomeAction.CloseQuickEntry) },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "MyFinHub",
                subtitle = "Έξυπνα οικονομικά, κάθε μέρα.",
            )
        },
        floatingActionButton = {
            MyFinHubPrimaryAction(
                label = "Νέα κίνηση",
                onClick = { onAction(HomeAction.OpenQuickEntry) },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            if (maxWidth >= 840.dp) {
                HomeExpandedContent(
                    state = state,
                    onAction = onAction,
                    onOpenAttention = onOpenAttention,
                    onOpenSettings = onOpenSettings,
                    onOpenDataTransfer = onOpenDataTransfer,
                    onOpenChangeHistory = onOpenChangeHistory,
                )
            } else {
                HomeCompactContent(
                    state = state,
                    onAction = onAction,
                    onOpenAttention = onOpenAttention,
                    onOpenSettings = onOpenSettings,
                    onOpenDataTransfer = onOpenDataTransfer,
                    onOpenChangeHistory = onOpenChangeHistory,
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
    onOpenSettings: () -> Unit,
    onOpenDataTransfer: () -> Unit,
    onOpenChangeHistory: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home_list"),
        contentPadding = PaddingValues(
            start = MyFinHubSpacing.lg,
            top = MyFinHubSpacing.xs,
            end = MyFinHubSpacing.lg,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
    ) {
        item { HomeHeading() }
        item { PositionCard(state = state, onToggleAmounts = { onAction(HomeAction.ToggleAmounts) }) }
        item { AccountsCard(state = state) }
        item { AttentionCard(items = state.attentionItems, onOpen = onOpenAttention) }
        item { UpcomingCard(items = state.upcomingItems, amountsVisible = state.amountsVisible) }
        item { QuickEntryCard(onOpen = { onAction(HomeAction.OpenQuickEntry) }) }
        item { MonthFlowCard(flow = state.monthFlow, amountsVisible = state.amountsVisible) }
        item {
            UtilitiesCard(
                onOpenSettings = onOpenSettings,
                onOpenDataTransfer = onOpenDataTransfer,
                onOpenChangeHistory = onOpenChangeHistory,
            )
        }
    }
}

@Composable
private fun HomeExpandedContent(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOpenAttention: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDataTransfer: () -> Unit,
    onOpenChangeHistory: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.lg),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            HomeHeading()
            PositionCard(state = state, onToggleAmounts = { onAction(HomeAction.ToggleAmounts) })
            AccountsCard(state = state)
            MonthFlowCard(flow = state.monthFlow, amountsVisible = state.amountsVisible)
            Spacer(modifier = Modifier.height(88.dp))
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            AttentionCard(items = state.attentionItems, onOpen = onOpenAttention)
            UpcomingCard(items = state.upcomingItems, amountsVisible = state.amountsVisible)
            QuickEntryCard(onOpen = { onAction(HomeAction.OpenQuickEntry) })
            UtilitiesCard(
                onOpenSettings = onOpenSettings,
                onOpenDataTransfer = onOpenDataTransfer,
                onOpenChangeHistory = onOpenChangeHistory,
            )
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
private fun HomeHeading() {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
        Text(
            text = "Η οικονομική σου εικόνα",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Τι έχεις διαθέσιμο, τι χρειάζεται προσοχή και τι ακολουθεί.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PositionCard(
    state: HomeUiState,
    onToggleAmounts: () -> Unit,
) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MyFinHubIconBadge(
                    icon = MyFinHubIcons.Account,
                    tone = FinanceTone.Savings,
                    contentDescription = null,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ΔΙΑΘΕΣΙΜΑ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Μετρητά και λογαριασμοί καθημερινής χρήσης",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = displayAmount(state.liquidTotal, state.amountsVisible),
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
    SectionCard(
        title = "Λογαριασμοί",
        subtitle = "Ρευστότητα και αποταμίευση",
    ) {
        state.accounts.forEachIndexed { index, account ->
            AccountRow(account = account, amountsVisible = state.amountsVisible)
            if (index != state.accounts.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = MyFinHubSpacing.xs),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: HomeAccount,
    amountsVisible: Boolean,
) {
    val tone = if (account.group == HomeAccountGroup.SAVINGS) FinanceTone.Savings else FinanceTone.Neutral
    val icon = if (account.group == HomeAccountGroup.SAVINGS) MyFinHubIcons.Savings else MyFinHubIcons.Account
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MyFinHubIconBadge(icon = icon, tone = tone, contentDescription = null)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(account.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = account.role,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = displayAmount(account.balance, amountsVisible),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AttentionCard(
    items: List<HomeAttentionItem>,
    onOpen: (String) -> Unit,
) {
    SectionCard(
        title = "Χρειάζεται προσοχή",
        subtitle = "Οι επόμενες χρήσιμες ενέργειες",
    ) {
        if (items.isEmpty()) {
            Text(
                "Δεν υπάρχει κάτι που χρειάζεται άμεση ενέργεια.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            items.forEachIndexed { index, item ->
                AttentionRow(item = item, onOpen = onOpen)
                if (index != items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = MyFinHubSpacing.xs),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttentionRow(
    item: HomeAttentionItem,
    onOpen: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        MyFinHubIconBadge(
            icon = MyFinHubIcons.Attention,
            tone = if (item.tone == HomeAttentionTone.URGENT) FinanceTone.Expense else FinanceTone.Attention,
            contentDescription = "Χρειάζεται προσοχή",
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = item.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item.dueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (item.tone == HomeAttentionTone.URGENT) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            TextButton(
                onClick = { onOpen(item.id) },
                modifier = Modifier.testTag("attention-${item.id}"),
            ) {
                Text("Έλεγχος")
            }
        }
    }
}

@Composable
private fun UpcomingCard(
    items: List<HomeUpcomingItem>,
    amountsVisible: Boolean,
) {
    SectionCard(
        title = "Επόμενα",
        subtitle = "Προγραμματισμένες υποχρεώσεις",
    ) {
        if (items.isEmpty()) {
            Text(
                "Δεν υπάρχουν προγραμματισμένες υποχρεώσεις.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MyFinHubIconBadge(
                        icon = MyFinHubIcons.Plan,
                        tone = FinanceTone.Neutral,
                        contentDescription = null,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = item.dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = displayAmount(item.amount, amountsVisible),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (index != items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = MyFinHubSpacing.xs),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickEntryCard(onOpen: () -> Unit) {
    SectionCard(
        title = "Γρήγορη καταχώριση",
        subtitle = "Καθημερινή κίνηση χωρίς περιττά βήματα",
    ) {
        MyFinHubPrimaryAction(
            label = "Επίλεξε τύπο κίνησης",
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun UtilitiesCard(
    onOpenSettings: () -> Unit,
    onOpenDataTransfer: () -> Unit,
    onOpenChangeHistory: () -> Unit,
) {
    SectionCard(
        title = "Ρυθμίσεις & δεδομένα",
        subtitle = "Προτιμήσεις, αντίγραφα και ασφαλές ιστορικό",
    ) {
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Ρυθμίσεις")
        }
        OutlinedButton(
            onClick = onOpenDataTransfer,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Εισαγωγή & αντίγραφα")
        }
        OutlinedButton(
            onClick = onOpenChangeHistory,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Ιστορικό αλλαγών")
        }
    }
}

@Composable
private fun MonthFlowCard(
    flow: HomeMonthFlow,
    amountsVisible: Boolean,
) {
    SectionCard(
        title = "Αυτόν τον μήνα",
        subtitle = "Η ροή με μια ματιά",
    ) {
        FlowMetric(
            label = "Έσοδα",
            value = displayAmount(flow.income, amountsVisible),
            tone = FinanceTone.Income,
            icon = MyFinHubIcons.Income,
        )
        FlowMetric(
            label = "Έξοδα",
            value = displayAmount(flow.expense, amountsVisible),
            tone = FinanceTone.Expense,
            icon = MyFinHubIcons.Expense,
        )
        FlowMetric(
            label = "Αποταμίευση",
            value = displayAmount(flow.saving, amountsVisible),
            tone = FinanceTone.Savings,
            icon = MyFinHubIcons.Savings,
        )
        Spacer(modifier = Modifier.height(MyFinHubSpacing.xxs))
        LinearProgressIndicator(
            progress = { flow.budgetProgress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
        )
        Text(
            text = "${(flow.budgetProgress * 100).toInt()}% του μηνιαίου προϋπολογισμού",
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
        MyFinHubIconBadge(icon = icon, tone = tone, contentDescription = null)
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MyFinHubAmountText(text = value, tone = tone)
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(1.dp))
            content()
        }
    }
}

@Composable
private fun QuickEntrySheet(
    selectedType: HomeQuickEntryType?,
    onSelect: (HomeQuickEntryType) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                start = MyFinHubSpacing.lg,
                end = MyFinHubSpacing.lg,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        ) {
            Text(
                text = "Γρήγορη καταχώριση",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Επίλεξε πρώτα τον τύπο της κίνησης.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HomeQuickEntryType.entries.forEach { type ->
                if (selectedType == type) {
                    Button(
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(type.label)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(type.label)
                    }
                }
            }
            selectedType?.let { type ->
                Text(
                    text = "Επιλέχθηκε: ${type.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Κλείσιμο")
            }
        }
    }
}

private fun displayAmount(value: Double, visible: Boolean): String = if (visible) {
    formatEuro(value)
} else {
    "•••• €"
}

private fun formatEuro(value: Double): String {
    val symbols = DecimalFormatSymbols(Locale.forLanguageTag("el-GR"))
    return DecimalFormat("#,##0.00 '€'", symbols).format(value)
}
