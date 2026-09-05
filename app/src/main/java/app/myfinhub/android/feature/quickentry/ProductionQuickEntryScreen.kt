package app.myfinhub.android.feature.quickentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSelectorButton
import app.myfinhub.android.designsystem.MyFinHubSpacing

private val FastKinds = listOf(
    QuickEntryKind.EXPENSE,
    QuickEntryKind.INCOME,
    QuickEntryKind.TRANSFER,
)

/**
 * Production fast path for the three everyday cash-flow types. Account/category/subcategory choices
 * are the canonical choices projected from the synchronized finance document. Less-frequent finance
 * semantics remain available through the complete editor.
 */
@Composable
fun ProductionQuickEntryScreen(
    state: QuickEntryUiState,
    onAction: (QuickEntryAction) -> Unit,
    onBack: () -> Unit,
) {
    if (state.kind !in FastKinds) {
        QuickEntryScreen(state = state, onAction = onAction, onBack = onBack)
        return
    }

    var noteExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedMenuOpen by remember { mutableStateOf(false) }
    var discardDialogOpen by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    val hasEnteredDraft = state.amountText.isNotBlank() || state.note.isNotBlank()
    val savedLocally = state.awaitingSync
    val requestBack = {
        if (!state.persisted && !savedLocally && hasEnteredDraft) discardDialogOpen = true else onBack()
    }

    LaunchedEffect(Unit) {
        amountFocus.requestFocus()
    }
    // Local encrypted enqueue is the successful mobile form submission boundary. Sync/Undo remains
    // visible centrally, so keeping the form open after a safe enqueue only creates a dead-end screen.
    LaunchedEffect(savedLocally) {
        if (savedLocally) onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Νέα κίνηση",
                subtitle = "Γρήγορη καταχώριση",
                navigation = { MyFinHubBackButton(requestBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = MyFinHubDesignMetrics.screenHorizontalPadding,
                    vertical = MyFinHubSpacing.sm,
                ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            Text(
                "Πόσο;",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            MyFinHubOutlinedField(
                value = state.amountText,
                onValueChange = { onAction(QuickEntryAction.AmountChanged(it)) },
                label = "Ποσό",
                suffix = { Text("€") },
                errorMessage = state.validationMessage.takeIf { it == "Βάλε ποσό μεγαλύτερο από μηδέν." },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                focusRequester = amountFocus,
            )

            Text("Τύπος", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                FastKinds.forEach { kind ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = { onAction(QuickEntryAction.SelectKind(kind)) },
                        label = { Text(kind.label) },
                    )
                }
            }

            when {
                state.kind.needsPrimaryAccount -> {
                    CompactChoice(
                        label = if (state.kind == QuickEntryKind.INCOME) "Σε λογαριασμό" else "Από λογαριασμό",
                        selectedId = state.accountId,
                        choices = state.accounts.map { it.id to it.label },
                        onSelected = { onAction(QuickEntryAction.AccountChanged(it)) },
                    )
                }
                state.kind == QuickEntryKind.TRANSFER -> {
                    CompactChoice(
                        label = "Από λογαριασμό",
                        selectedId = state.fromAccountId,
                        choices = state.accounts.map { it.id to it.label },
                        onSelected = { onAction(QuickEntryAction.FromAccountChanged(it)) },
                    )
                }
            }

            if (state.kind == QuickEntryKind.TRANSFER) {
                CompactChoice(
                    label = "Προς λογαριασμό",
                    selectedId = state.toAccountId,
                    choices = state.accounts.filter { it.id != state.fromAccountId }.map { it.id to it.label },
                    onSelected = { onAction(QuickEntryAction.ToAccountChanged(it)) },
                )
            }

            if (state.kind.usesCategory) {
                CompactChoice(
                    label = "Κατηγορία",
                    selectedId = state.category,
                    choices = state.activeCategoryOptions.map { it.name to it.name },
                    onSelected = { onAction(QuickEntryAction.CategoryChanged(it)) },
                )
                if (state.activeSubcategoryOptions.isNotEmpty()) {
                    CompactChoice(
                        label = "Υποκατηγορία",
                        selectedId = state.subcategory,
                        choices = listOf("" to "Χωρίς υποκατηγορία") +
                            state.activeSubcategoryOptions.map { it to it },
                        onSelected = { onAction(QuickEntryAction.SubcategoryChanged(it)) },
                    )
                }
            }

            DateEntryField(
                value = state.dateText,
                onValueChange = { onAction(QuickEntryAction.DateChanged(it)) },
                label = "Ημερομηνία",
                errorMessage = state.validationMessage.takeIf { it == "Συμπλήρωσε έγκυρη ημερομηνία." },
            )

            if (state.validationMessage != null &&
                state.validationMessage != "Βάλε ποσό μεγαλύτερο από μηδέν." &&
                state.validationMessage != "Συμπλήρωσε έγκυρη ημερομηνία."
            ) {
                Text(
                    state.validationMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            TextButton(onClick = { noteExpanded = !noteExpanded }) {
                Text(if (noteExpanded) "Απόκρυψη σημείωσης" else "Προσθήκη σημείωσης")
            }
            if (noteExpanded || state.note.isNotBlank()) {
                MyFinHubOutlinedField(
                    value = state.note,
                    onValueChange = { onAction(QuickEntryAction.NoteChanged(it)) },
                    label = "Σημείωση · προαιρετική",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                )
            }

            Box {
                TextButton(onClick = { advancedMenuOpen = true }) { Text("Περισσότεροι τύποι κίνησης") }
                DropdownMenu(expanded = advancedMenuOpen, onDismissRequest = { advancedMenuOpen = false }) {
                    QuickEntryKind.entries.filterNot(FastKinds::contains).forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(kind.label) },
                            onClick = {
                                advancedMenuOpen = false
                                onAction(QuickEntryAction.SelectKind(kind))
                            },
                        )
                    }
                }
            }

            MyFinHubPrimaryAction(
                label = when {
                    state.persisted -> "Αποθηκεύτηκε"
                    savedLocally -> "Αποθηκεύτηκε στη συσκευή"
                    else -> "Αποθήκευση κίνησης"
                },
                enabled = !state.persisted && !savedLocally,
                onClick = { onAction(QuickEntryAction.Save) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (discardDialogOpen) {
        AlertDialog(
            onDismissRequest = { discardDialogOpen = false },
            title = { Text("Απόρριψη καταχώρισης;") },
            text = { Text("Τα στοιχεία που συμπλήρωσες δεν έχουν αποθηκευτεί.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        discardDialogOpen = false
                        onAction(QuickEntryAction.Reset)
                        onBack()
                    },
                ) { Text("Απόρριψη") }
            },
            dismissButton = {
                TextButton(onClick = { discardDialogOpen = false }) { Text("Συνέχεια") }
            },
        )
    }
}

@Composable
private fun CompactChoice(
    label: String,
    selectedId: String,
    choices: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.first == selectedId }?.second ?: "Επιλογή"
    Box {
        MyFinHubSelectorButton(
            label = label,
            onClick = { expanded = true },
            enabled = choices.isNotEmpty(),
        ) {
            Text(selectedLabel, modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
