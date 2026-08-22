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
import androidx.compose.foundation.lazy.items
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
                ExpandedHomeContent(
                    state = state,
                    onAction = onAction,
                )
            } else {
                CompactHomeContent(
                    state = state,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun CompactHomeContent(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PositionSection(state = state, onAction = onAction)
        }
        item {
            AccountsSection(state = state)
        }
        if (state.attentionItems.isNotEmpty()) {
            item {
                AttentionSection(state = state)
            }
        }
        if (state.upcomingItems.isNotEmpty()) {
            item {
                UpcomingSection(state = state)
            }
        }
        item {
            QuickEntrySection(onAction = onAction)
        }
        item {
            MonthlyFlowSection(state = state)
        }
    }
}

@Composable
private fun ExpandedHomeContent(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1.15f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PositionSection(state = state, onAction = onAction)
            AccountsSection(state = state)
            QuickEntrySection(onAction = onAction)
        }
        Column(
            modifier = Modifier
                .weight(0.85f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.attentionItems.isNotEmpty()) {
                AttentionSection(state = state)
            }
            if (state.upcomingItems.isNotEmpty()) {
                UpcomingSection(state = state)
            }
            MonthlyFlowSection(state = state)
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
private fun PositionSection(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionHeading("Διαθέσιμη θέση")
                Text(
                    text = state.formattedAmount(state.liquidPositionCents),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics {
                        contentDescription = if (state.amountsVisible) {
                            "Διαθέσιμη θέση ${state.formattedAmount(state.liquidPositionCents)}"
                        } else {
                            "Διαθέσιμη θέση, ποσό κρυφό"
                        }
                    },
                )
            }
            TextButton(onClick = { onAction(HomeAction.ToggleAmounts) }) {
                Text(if (state.amountsVisible) "Απόκρυψη" else "Εμφάνιση")
            }
        }
        Text(
            text = "Μετρητά και άμεσα διαθέσιμα υπόλοιπα",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountsSection(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading("Λογαριασμοί")
        state.accounts.forEach { account ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(account.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            account.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = state.formattedAmount(account.balanceCents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttentionSection(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading("Χρειάζεται προσοχή")
        state.attentionItems.forEach { item ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(item.detail, style = MaterialTheme.typography.bodyMedium)
                    if (item.amountCents != null) {
                        Text(
                            state.formattedAmount(item.amountCents),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingSection(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading("Επόμενα")
        state.upcomingItems.forEach { item ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            item.whenText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(state.formattedAmount(item.amountCents), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun QuickEntrySection(onAction: (HomeAction) -> Unit) {
    SectionCard {
        SectionHeading("Γρήγορη καταχώριση")
        Text(
            "Καταχώρισε νέα κίνηση χωρίς να ψάχνεις σε μενού.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onAction(HomeAction.OpenQuickEntry) }) {
            Text("Νέα κίνηση")
        }
    }
}

@Composable
private fun MonthlyFlowSection(state: HomeUiState) {
    SectionCard {
        SectionHeading("Αυτός ο μήνας")
        FlowRow("Έσοδα", state.formattedAmount(state.monthlyFlow.incomeCents))
        FlowRow("Έξοδα", state.formattedAmount(state.monthlyFlow.expenseCents))
        HorizontalDivider()
        FlowRow("Καθαρή ροή", state.formattedAmount(state.monthlyFlow.netCents), emphasize = true)
        LinearProgressIndicator(
            progress = { state.monthlyFlow.savingsRate },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Αποταμίευση ${(state.monthlyFlow.savingsRate * 100).toInt()}% των εσόδων",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FlowRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(value, fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickEntrySheet(
    selectedType: QuickEntryType?,
    onSelect: (QuickEntryType) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeading("Τι θέλεις να καταχωρίσεις;")
            QuickEntryType.entries.forEach { type ->
                val selected = selectedType == type
                if (selected) {
                    FilledTonalButton(
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
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

internal fun formatEuroCents(cents: Long): String {
    val symbols = DecimalFormatSymbols(Locale("el", "GR")).apply {
        decimalSeparator = ','
        groupingSeparator = '.'
    }
    val formatter = DecimalFormat("#,##0.00", symbols)
    return "${formatter.format(cents / 100.0)} €"
}
