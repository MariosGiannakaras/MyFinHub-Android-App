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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MyFinHub",
                        modifier = Modifier.semantics { heading() },
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAction(HomeAction.OpenQuickEntry) },
                icon = { Text("+") },
                text = { Text("Νέα κίνηση") },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HomeHeading()
            PositionCard(state = state, onToggleAmounts = { onAction(HomeAction.ToggleAmounts) })
            AccountsCard(state = state)
            MonthFlowCard(flow = state.monthFlow, amountsVisible = state.amountsVisible)
            Spacer(modifier = Modifier.height(88.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Η οικονομική σου εικόνα",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "ΔΙΑΘΕΣΙΜΑ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = displayAmount(state.liquidTotal, state.amountsVisible),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics {
                    contentDescription = if (state.amountsVisible) {
                        "Διαθέσιμα ${formatEuro(state.liquidTotal)}"
                    } else {
                        "Διαθέσιμα ποσά κρυφά"
                    }
                },
            )
            Text(
                text = "Μετρητά και λογαριασμοί καθημερινής χρήσης",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: HomeAccount,
    amountsVisible: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
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
            Text("Δεν υπάρχει κάτι που χρειάζεται άμεση ενέργεια.")
        } else {
            items.forEachIndexed { index, item ->
                AttentionRow(item = item, onOpen = onOpen)
                if (index != items.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (item.tone == HomeAttentionTone.URGENT) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ) {
            Text(
                text = if (item.tone == HomeAttentionTone.URGENT) "!" else "i",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleSmall)
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
            Text("Δεν υπάρχουν προγραμματισμένες υποχρεώσεις.")
        } else {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = item.dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = displayAmount(item.amount, amountsVisible),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (index != items.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickEntryCard(onOpen: () -> Unit) {
    SectionCard(
        title = "Γρήγορη καταχώριση",
        subtitle = "Για μια καθημερινή κίνηση χωρίς περιττά βήματα",
    ) {
        FilledTonalButton(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Επίλεξε τύπο κίνησης")
        }
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
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Ρυθμίσεις")
        }
        OutlinedButton(onClick = onOpenDataTransfer, modifier = Modifier.fillMaxWidth()) {
            Text("Εισαγωγή & αντίγραφα")
        }
        OutlinedButton(onClick = onOpenChangeHistory, modifier = Modifier.fillMaxWidth()) {
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
        subtitle = "Καθαρή εικόνα πριν το αναλυτικό report",
    ) {
        FlowMetric(label = "Έσοδα", value = displayAmount(flow.income, amountsVisible))
        FlowMetric(label = "Έξοδα", value = displayAmount(flow.expense, amountsVisible))
        FlowMetric(label = "Αποταμίευση", value = displayAmount(flow.saving, amountsVisible))
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { flow.budgetProgress },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${(flow.budgetProgress * 100).toInt()}% του μηνιαίου προϋπολογισμού",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FlowMetric(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickEntrySheet(
    selectedType: HomeQuickEntryType?,
    onSelect: (HomeQuickEntryType) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    ) {
                        Text(type.label)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth(),
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
