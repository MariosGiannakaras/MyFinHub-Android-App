package app.myfinhub.android.feature.quickentry

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    QuickEntryKind.CARD_PAYMENT,
)

/**
 * Production fast path for the four everyday transaction types. Advanced transaction semantics are
 * still fully available by selecting another type, which falls back to the complete editor.
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

    var moreFields by rememberSaveable { mutableStateOf(false) }
    var advancedMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Νέα κίνηση",
                subtitle = "Γρήγορη καταχώριση",
                navigation = { MyFinHubBackButton(onBack) },
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
            )

            Text("Τύπος", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                state.kind == QuickEntryKind.TRANSFER || state.kind == QuickEntryKind.CARD_PAYMENT -> {
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

            if (state.kind == QuickEntryKind.CARD_PAYMENT) {
                CompactChoice(
                    label = "Κάρτα",
                    selectedId = state.cardId,
                    choices = state.creditCards.map { it.id to it.label },
                    onSelected = { onAction(QuickEntryAction.CardChanged(it)) },
                )
            }

            if (state.kind.usesCategory) {
                CompactChoice(
                    label = "Κατηγορία",
                    selectedId = state.category,
                    choices = state.activeCategoryOptions.map { it.name to it.name },
                    onSelected = { onAction(QuickEntryAction.CategoryChanged(it)) },
                )
            }

            if (state.validationMessage != null && state.validationMessage != "Βάλε ποσό μεγαλύτερο από μηδέν.") {
                Text(
                    state.validationMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            TextButton(onClick = { moreFields = !moreFields }) {
                Text(if (moreFields) "Λιγότερα στοιχεία" else "Περισσότερα στοιχεία")
            }
            if (moreFields) {
                MyFinHubOutlinedField(
                    value = state.dateText,
                    onValueChange = { onAction(QuickEntryAction.DateChanged(it)) },
                    label = "Ημερομηνία",
                    supportingText = "YYYY-MM-DD",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                if (state.activeSubcategoryOptions.isNotEmpty() && state.kind.usesCategory) {
                    CompactChoice(
                        label = "Υποκατηγορία",
                        selectedId = state.subcategory,
                        choices = listOf("" to "Χωρίς υποκατηγορία") + state.activeSubcategoryOptions.map { it to it },
                        onSelected = { onAction(QuickEntryAction.SubcategoryChanged(it)) },
                    )
                }
                MyFinHubOutlinedField(
                    value = state.note,
                    onValueChange = { onAction(QuickEntryAction.NoteChanged(it)) },
                    label = "Περιγραφή · προαιρετική",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                )
            }

            Box {
                TextButton(onClick = { advancedMenuOpen = true }) { Text("Άλλος τύπος κίνησης") }
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
                label = if (state.persisted) "Αποθηκεύτηκε" else "Αποθήκευση κίνησης",
                enabled = !state.persisted,
                onClick = { onAction(QuickEntryAction.Save) },
                modifier = Modifier.fillMaxWidth(),
            )

            state.savedSummary?.let { summary ->
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
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