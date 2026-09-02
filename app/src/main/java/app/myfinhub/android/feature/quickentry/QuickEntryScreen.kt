package app.myfinhub.android.feature.quickentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubFilterChip
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing

@Composable
fun QuickEntryScreen(
    state: QuickEntryUiState,
    onAction: (QuickEntryAction) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Νέα κίνηση",
                subtitle = "Γρήγορη καταχώριση",
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(MyFinHubIcons.Back, contentDescription = "Πίσω")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MyFinHubSpacing.lg, vertical = MyFinHubSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            Text(
                text = "Τι θέλεις να καταχωρίσεις;",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                QuickEntryKind.entries.take(2).forEach { kind ->
                    MyFinHubFilterChip(
                        selected = state.kind == kind,
                        onClick = { onAction(QuickEntryAction.SelectKind(kind)) },
                        label = kind.label,
                        icon = kind.icon(),
                        tone = kind.tone(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                QuickEntryKind.entries.drop(2).forEach { kind ->
                    MyFinHubFilterChip(
                        selected = state.kind == kind,
                        onClick = { onAction(QuickEntryAction.SelectKind(kind)) },
                        label = kind.label,
                        icon = kind.icon(),
                        tone = kind.tone(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    OutlinedTextField(
                        value = state.amountText,
                        onValueChange = { onAction(QuickEntryAction.AmountChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ποσό") },
                        suffix = { Text("€") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = { onAction(QuickEntryAction.NoteChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Περιγραφή") },
                        shape = MaterialTheme.shapes.medium,
                    )
                    if (
                        state.kind == QuickEntryKind.EXPENSE ||
                        state.kind == QuickEntryKind.CARD_PAYMENT ||
                        state.kind == QuickEntryKind.SPLIT
                    ) {
                        OutlinedTextField(
                            value = state.category,
                            onValueChange = { onAction(QuickEntryAction.CategoryChanged(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Κατηγορία") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                    if (state.kind == QuickEntryKind.TRANSFER) {
                        OutlinedTextField(
                            value = state.destination,
                            onValueChange = { onAction(QuickEntryAction.DestinationChanged(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Προς λογαριασμό") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                        )
                        Text(
                            "Οι εσωτερικές μεταφορές δεν μετρούν ως έσοδα ή έξοδα.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.kind == QuickEntryKind.SPLIT) {
                        Text("Άτομα: ${state.splitPeople}", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            OutlinedButton(
                                onClick = { onAction(QuickEntryAction.SplitPeopleChanged(state.splitPeople - 1)) },
                                modifier = Modifier.weight(1f).semantics { contentDescription = "Μείωση ατόμων" },
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text("−")
                            }
                            OutlinedButton(
                                onClick = { onAction(QuickEntryAction.SplitPeopleChanged(state.splitPeople + 1)) },
                                modifier = Modifier.weight(1f).semantics { contentDescription = "Αύξηση ατόμων" },
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text("+")
                            }
                        }
                    }
                }
            }

            state.validationMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            state.savedSummary?.let { summary ->
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text(
                            text = if (state.persisted) "Αποθηκεύτηκε: $summary" else "Έτοιμο: $summary",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (!state.persisted) {
                            Text(
                                "Το debug/test host κρατά μόνο preview και δεν γράφει canonical δεδομένα.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { onAction(QuickEntryAction.Save) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Αποθήκευση κίνησης")
            }
        }
    }
}

private fun QuickEntryKind.icon(): ImageVector = when (this) {
    QuickEntryKind.EXPENSE -> MyFinHubIcons.Expense
    QuickEntryKind.TRANSFER -> MyFinHubIcons.Transfer
    QuickEntryKind.CARD_PAYMENT -> MyFinHubIcons.Card
    QuickEntryKind.SPLIT -> MyFinHubIcons.Activity
}

private fun QuickEntryKind.tone(): FinanceTone = when (this) {
    QuickEntryKind.EXPENSE -> FinanceTone.Expense
    QuickEntryKind.TRANSFER -> FinanceTone.Transfer
    QuickEntryKind.CARD_PAYMENT -> FinanceTone.Transfer
    QuickEntryKind.SPLIT -> FinanceTone.Neutral
}
