package app.myfinhub.android.feature.quickentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
    var discardDialogOpen by remember { mutableStateOf(false) }
    val requestBack = {
        if (state.dirty && !state.persisted) discardDialogOpen = true else onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Νέα κίνηση",
                subtitle = "Πλήρης καταχώριση",
                navigation = {
                    IconButton(onClick = requestBack) {
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
                .padding(horizontal = MyFinHubSpacing.lg, vertical = MyFinHubSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            Text(
                text = "Τι θέλεις να καταχωρίσεις;",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )

            SelectionField(
                label = "Τύπος κίνησης",
                selectedId = state.kind.name,
                choices = QuickEntryKind.entries.map { it.name to it.label },
                onSelected = { raw ->
                    QuickEntryKind.entries.firstOrNull { it.name == raw }?.let {
                        onAction(QuickEntryAction.SelectKind(it))
                    }
                },
            )
            Text(
                text = state.kind.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    if (state.kind != QuickEntryKind.RECONCILIATION && state.kind != QuickEntryKind.SPLIT) {
                        OutlinedTextField(
                            value = state.amountText,
                            onValueChange = { onAction(QuickEntryAction.AmountChanged(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Ποσό") },
                            suffix = { Text("€") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                        )
                    }

                    OutlinedTextField(
                        value = state.dateText,
                        onValueChange = { onAction(QuickEntryAction.DateChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ημερομηνία · YYYY-MM-DD") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )

                    if (state.kind.needsPrimaryAccount) {
                        SelectionField(
                            label = primaryAccountLabel(state.kind),
                            selectedId = state.accountId,
                            choices = state.accounts.map { it.id to it.label },
                            onSelected = { onAction(QuickEntryAction.AccountChanged(it)) },
                        )
                    }

                    if (state.kind.needsTransferAccounts || state.kind == QuickEntryKind.CARD_PAYMENT) {
                        SelectionField(
                            label = "Από λογαριασμό",
                            selectedId = state.fromAccountId,
                            choices = state.accounts.map { it.id to it.label },
                            onSelected = { onAction(QuickEntryAction.FromAccountChanged(it)) },
                        )
                    }

                    if (state.kind.needsTransferAccounts) {
                        val destinations = destinationOptions(state)
                        SelectionField(
                            label = "Προς λογαριασμό",
                            selectedId = state.toAccountId,
                            choices = destinations.map { it.id to it.label },
                            onSelected = { onAction(QuickEntryAction.ToAccountChanged(it)) },
                        )
                        TransactionSemanticsHint(state.kind)
                    }

                    if (state.kind.needsCard) {
                        SelectionField(
                            label = "Πιστωτική κάρτα",
                            selectedId = state.cardId,
                            choices = state.creditCards.map { it.id to it.label },
                            onSelected = { onAction(QuickEntryAction.CardChanged(it)) },
                        )
                        if (state.creditCards.isEmpty()) {
                            Text(
                                "Δεν υπάρχει ενεργή πιστωτική κάρτα.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    if (state.kind.usesCategory) {
                        SelectionField(
                            label = "Κατηγορία",
                            selectedId = state.category,
                            choices = state.activeCategoryOptions.map { it.name to it.name },
                            onSelected = { onAction(QuickEntryAction.CategoryChanged(it)) },
                        )
                        if (state.activeSubcategoryOptions.isNotEmpty()) {
                            SelectionField(
                                label = "Υποκατηγορία",
                                selectedId = state.subcategory,
                                choices = listOf("" to "Χωρίς υποκατηγορία") +
                                    state.activeSubcategoryOptions.map { it to it },
                                onSelected = { onAction(QuickEntryAction.SubcategoryChanged(it)) },
                            )
                        }
                    }

                    if (state.kind == QuickEntryKind.LENDING || state.kind == QuickEntryKind.REPAYMENT) {
                        OutlinedTextField(
                            value = state.person,
                            onValueChange = { onAction(QuickEntryAction.PersonChanged(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Πρόσωπο") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                    if (state.kind == QuickEntryKind.LENDING) {
                        OutlinedTextField(
                            value = state.expectedReturnDateText,
                            onValueChange = { onAction(QuickEntryAction.ExpectedReturnDateChanged(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Αναμενόμενη επιστροφή · προαιρετική") },
                            supportingText = { Text("YYYY-MM-DD") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                        )
                    }

                    if (state.kind == QuickEntryKind.RECONCILIATION) {
                        OutlinedTextField(
                            value = state.actualBalanceText,
                            onValueChange = { onAction(QuickEntryAction.ActualBalanceChanged(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Πραγματικό υπόλοιπο") },
                            suffix = { Text("€") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                        )
                        Text(
                            "Θα καταχωριστεί μόνο η διαφορά από το υπολογισμένο υπόλοιπο. Δεν μετρά ως έσοδο ή έξοδο.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (state.kind == QuickEntryKind.CARD_PAYMENT) {
                        Text(
                            "Η πληρωμή μειώνει την οφειλή της επιλεγμένης κάρτας και δεν μετρά ως νέο έξοδο.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (state.kind == QuickEntryKind.CARD_PURCHASE) {
                        Text(
                            "Η αγορά αυξάνει την οφειλή της συγκεκριμένης πιστωτικής και μετρά ως έξοδο.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.kind == QuickEntryKind.SPLIT) {
                SplitEditor(state = state, onAction = onAction)
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { onAction(QuickEntryAction.NoteChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Περιγραφή · προαιρετική") },
                    supportingText = { Text("Αν μείνει κενή, χρησιμοποιείται το όνομα του τύπου κίνησης.") },
                    shape = MaterialTheme.shapes.medium,
                )
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
                        if (state.persisted) {
                            TextButton(onClick = { onAction(QuickEntryAction.Reset) }) {
                                Text("Νέα καταχώριση")
                            }
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

    if (discardDialogOpen) {
        AlertDialog(
            onDismissRequest = { discardDialogOpen = false },
            title = { Text("Απόρριψη αλλαγών;") },
            text = { Text("Έχεις μη αποθηκευμένα στοιχεία στη νέα κίνηση.") },
            confirmButton = {
                Button(
                    onClick = {
                        discardDialogOpen = false
                        onAction(QuickEntryAction.Reset)
                        onBack()
                    },
                ) {
                    Text("Απόρριψη")
                }
            },
            dismissButton = {
                TextButton(onClick = { discardDialogOpen = false }) {
                    Text("Συνέχεια επεξεργασίας")
                }
            },
        )
    }
}

@Composable
private fun SplitEditor(
    state: QuickEntryUiState,
    onAction: (QuickEntryAction) -> Unit,
) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Μέρη σύνθετης αγοράς", style = MaterialTheme.typography.titleMedium)
            Text(
                "Το συνολικό ποσό προκύπτει από τα επιμέρους ποσά και κάθε μέρος έχει τη δική του κατηγορία.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.splitParts.forEachIndexed { index, part ->
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Μέρος ${index + 1}", style = MaterialTheme.typography.labelLarge)
                        if (state.splitParts.size > 2) {
                            TextButton(
                                onClick = { onAction(QuickEntryAction.RemoveSplitPart(part.id)) },
                                modifier = Modifier.semantics {
                                    contentDescription = "Αφαίρεση μέρους ${index + 1}"
                                },
                            ) {
                                Text("Αφαίρεση")
                            }
                        }
                    }
                    OutlinedTextField(
                        value = part.amountText,
                        onValueChange = { onAction(QuickEntryAction.SplitPartAmountChanged(part.id, it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ποσό μέρους ${index + 1}") },
                        suffix = { Text("€") },
                        singleLine = true,
                    )
                    SelectionField(
                        label = "Κατηγορία μέρους ${index + 1}",
                        selectedId = part.category,
                        choices = state.expenseCategories.map { it.name to it.name },
                        onSelected = { onAction(QuickEntryAction.SplitPartCategoryChanged(part.id, it)) },
                    )
                    val subcategories = state.expenseCategories.firstOrNull { it.name == part.category }
                        ?.subcategories
                        .orEmpty()
                    if (subcategories.isNotEmpty()) {
                        SelectionField(
                            label = "Υποκατηγορία μέρους ${index + 1}",
                            selectedId = part.subcategory,
                            choices = listOf("" to "Χωρίς υποκατηγορία") + subcategories.map { it to it },
                            onSelected = {
                                onAction(QuickEntryAction.SplitPartSubcategoryChanged(part.id, it))
                            },
                        )
                    }
                    OutlinedTextField(
                        value = part.label,
                        onValueChange = { onAction(QuickEntryAction.SplitPartLabelChanged(part.id, it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ετικέτα μέρους · προαιρετική") },
                        singleLine = true,
                    )
                }
            }

            OutlinedButton(
                onClick = { onAction(QuickEntryAction.AddSplitPart) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Προσθήκη μέρους σύνθετης αγοράς" },
            ) {
                Text("+ Προσθήκη μέρους")
            }
            Text(
                "Σύνολο: ${formatSplitTotal(state.splitTotal)} €",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun SelectionField(
    label: String,
    selectedId: String,
    choices: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.first == selectedId }?.second.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { if (choices.isNotEmpty()) expanded = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = choices.isNotEmpty(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(selectedLabel.ifBlank { if (choices.isEmpty()) "Δεν υπάρχει διαθέσιμη επιλογή" else "Επιλογή" })
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                choices.forEach { (id, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            expanded = false
                            onSelected(id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionSemanticsHint(kind: QuickEntryKind) {
    val text = when (kind) {
        QuickEntryKind.TRANSFER -> "Η εσωτερική μεταφορά αλλάζει υπόλοιπα αλλά δεν μετρά ως έσοδο ή έξοδο."
        QuickEntryKind.WITHDRAWAL -> "Η ανάληψη μετακινεί χρήματα σε μετρητά και δεν μετρά ως έξοδο."
        QuickEntryKind.SAVING -> "Η μεταφορά προς αποταμίευση μετρά ως αποταμίευση, όχι ως έξοδο."
        else -> return
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun destinationOptions(state: QuickEntryUiState): List<QuickEntryAccountOption> {
    val filtered = when (state.kind) {
        QuickEntryKind.WITHDRAWAL -> state.accounts.filter { it.kind == "cash" }
        QuickEntryKind.SAVING -> state.accounts.filter { it.kind == "savings" }
        else -> state.accounts
    }.filter { it.id != state.fromAccountId }
    return filtered.ifEmpty { state.accounts.filter { it.id != state.fromAccountId } }
}

private fun primaryAccountLabel(kind: QuickEntryKind): String = when (kind) {
    QuickEntryKind.INCOME, QuickEntryKind.REFUND, QuickEntryKind.REPAYMENT -> "Προς λογαριασμό"
    QuickEntryKind.RECONCILIATION -> "Λογαριασμός διόρθωσης"
    QuickEntryKind.SPLIT -> "Λογαριασμός πληρωμής"
    else -> "Λογαριασμός"
}

private fun formatSplitTotal(value: Double): String = if (value % 1.0 == 0.0) {
    value.toLong().toString()
} else {
    String.format(java.util.Locale.US, "%.2f", value)
}
