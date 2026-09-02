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
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubFieldIconButton
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedAction
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSelectorButton
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
    val validationMessage = state.validationMessage
    val amountError = validationMessage == "Βάλε ποσό μεγαλύτερο από μηδέν."
    val dateError = validationMessage == "Συμπλήρωσε έγκυρη ημερομηνία."
    val accountError = validationMessage == "Διάλεξε διαθέσιμο λογαριασμό."
    val fromAccountError = validationMessage == "Διάλεξε λογαριασμό προέλευσης."
    val toAccountError = validationMessage == "Διάλεξε λογαριασμό προορισμού." ||
        validationMessage == "Οι λογαριασμοί πρέπει να είναι διαφορετικοί." ||
        validationMessage == "Η ανάληψη πρέπει να καταλήγει σε λογαριασμό μετρητών." ||
        validationMessage == "Η αποταμίευση πρέπει να καταλήγει σε λογαριασμό αποταμίευσης."
    val cardError = validationMessage == "Διάλεξε ενεργή πιστωτική κάρτα."
    val categoryError = validationMessage == "Διάλεξε διαθέσιμη κατηγορία."
    val subcategoryError = validationMessage == "Διάλεξε διαθέσιμη υποκατηγορία."
    val personError = validationMessage == "Συμπλήρωσε το πρόσωπο για τα δανεικά."
    val expectedDateError = validationMessage == "Η αναμενόμενη επιστροφή δεν είναι έγκυρη." ||
        validationMessage == "Η αναμενόμενη επιστροφή δεν μπορεί να είναι πριν από την ημερομηνία κίνησης."
    val actualBalanceError = validationMessage == "Συμπλήρωσε έγκυρο πραγματικό υπόλοιπο."

    val amountFocus = remember { FocusRequester() }
    val dateFocus = remember { FocusRequester() }
    val accountFocus = remember { FocusRequester() }
    val fromAccountFocus = remember { FocusRequester() }
    val toAccountFocus = remember { FocusRequester() }
    val cardFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    val subcategoryFocus = remember { FocusRequester() }
    val personFocus = remember { FocusRequester() }
    val expectedDateFocus = remember { FocusRequester() }
    val actualBalanceFocus = remember { FocusRequester() }

    LaunchedEffect(validationMessage) {
        when {
            amountError -> amountFocus
            dateError -> dateFocus
            accountError -> accountFocus
            fromAccountError -> fromAccountFocus
            toAccountError -> toAccountFocus
            cardError -> cardFocus
            categoryError -> categoryFocus
            subcategoryError -> subcategoryFocus
            personError -> personFocus
            expectedDateError -> expectedDateFocus
            actualBalanceError -> actualBalanceFocus
            else -> null
        }?.requestFocus()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Νέα κίνηση",
                subtitle = "Πλήρης καταχώριση",
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
                        MyFinHubOutlinedField(
                            value = state.amountText,
                            onValueChange = { onAction(QuickEntryAction.AmountChanged(it)) },
                            label = "Ποσό",
                            suffix = { Text("€") },
                            errorMessage = validationMessage.takeIf { amountError },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next,
                            ),
                            focusRequester = amountFocus,
                        )
                    }

                    DateEntryField(
                        value = state.dateText,
                        onValueChange = { onAction(QuickEntryAction.DateChanged(it)) },
                        label = "Ημερομηνία",
                        errorMessage = validationMessage.takeIf { dateError },
                        modifier = Modifier.focusRequester(dateFocus),
                    )

                    if (state.kind.needsPrimaryAccount) {
                        SelectionField(
                            label = primaryAccountLabel(state.kind),
                            selectedId = state.accountId,
                            choices = state.accounts.map { it.id to it.label },
                            onSelected = { onAction(QuickEntryAction.AccountChanged(it)) },
                            errorMessage = validationMessage.takeIf { accountError },
                            modifier = Modifier.focusRequester(accountFocus),
                        )
                    }

                    if (state.kind.needsTransferAccounts || state.kind == QuickEntryKind.CARD_PAYMENT) {
                        SelectionField(
                            label = "Από λογαριασμό",
                            selectedId = state.fromAccountId,
                            choices = state.accounts.map { it.id to it.label },
                            onSelected = { onAction(QuickEntryAction.FromAccountChanged(it)) },
                            errorMessage = validationMessage.takeIf { fromAccountError },
                            modifier = Modifier.focusRequester(fromAccountFocus),
                        )
                    }

                    if (state.kind.needsTransferAccounts) {
                        val destinations = destinationOptions(state)
                        SelectionField(
                            label = "Προς λογαριασμό",
                            selectedId = state.toAccountId,
                            choices = destinations.map { it.id to it.label },
                            onSelected = { onAction(QuickEntryAction.ToAccountChanged(it)) },
                            errorMessage = validationMessage.takeIf { toAccountError },
                            modifier = Modifier.focusRequester(toAccountFocus),
                        )
                        TransactionSemanticsHint(state.kind)
                    }

                    if (state.kind.needsCard) {
                        SelectionField(
                            label = "Πιστωτική κάρτα",
                            selectedId = state.cardId,
                            choices = state.creditCards.map { it.id to it.label },
                            onSelected = { onAction(QuickEntryAction.CardChanged(it)) },
                            errorMessage = validationMessage.takeIf { cardError },
                            modifier = Modifier.focusRequester(cardFocus),
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
                            errorMessage = validationMessage.takeIf { categoryError },
                            modifier = Modifier.focusRequester(categoryFocus),
                        )
                        if (state.activeSubcategoryOptions.isNotEmpty()) {
                            SelectionField(
                                label = "Υποκατηγορία",
                                selectedId = state.subcategory,
                                choices = listOf("" to "Χωρίς υποκατηγορία") +
                                    state.activeSubcategoryOptions.map { it to it },
                                onSelected = { onAction(QuickEntryAction.SubcategoryChanged(it)) },
                                errorMessage = validationMessage.takeIf { subcategoryError },
                                modifier = Modifier.focusRequester(subcategoryFocus),
                            )
                        }
                    }

                    if (state.kind == QuickEntryKind.LENDING || state.kind == QuickEntryKind.REPAYMENT) {
                        MyFinHubOutlinedField(
                            value = state.person,
                            onValueChange = { onAction(QuickEntryAction.PersonChanged(it)) },
                            label = "Πρόσωπο",
                            errorMessage = validationMessage.takeIf { personError },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next,
                            ),
                            focusRequester = personFocus,
                        )
                    }
                    if (state.kind == QuickEntryKind.LENDING) {
                        DateEntryField(
                            value = state.expectedReturnDateText,
                            onValueChange = { onAction(QuickEntryAction.ExpectedReturnDateChanged(it)) },
                            label = "Αναμενόμενη επιστροφή · προαιρετική",
                            errorMessage = validationMessage.takeIf { expectedDateError },
                            modifier = Modifier.focusRequester(expectedDateFocus),
                            optional = true,
                        )
                    }

                    if (state.kind == QuickEntryKind.RECONCILIATION) {
                        MyFinHubOutlinedField(
                            value = state.actualBalanceText,
                            onValueChange = { onAction(QuickEntryAction.ActualBalanceChanged(it)) },
                            label = "Πραγματικό υπόλοιπο",
                            suffix = { Text("€") },
                            errorMessage = validationMessage.takeIf { actualBalanceError },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next,
                            ),
                            focusRequester = actualBalanceFocus,
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
                MyFinHubOutlinedField(
                    value = state.note,
                    onValueChange = { onAction(QuickEntryAction.NoteChanged(it)) },
                    label = "Περιγραφή · προαιρετική",
                    supportingText = "Αν μείνει κενή, χρησιμοποιείται το όνομα του τύπου κίνησης.",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = false,
                )
            }

            validationMessage?.takeUnless(::isInlineFieldValidation)?.let { message ->
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

            MyFinHubPrimaryAction(
                label = "Αποθήκευση κίνησης",
                onClick = { onAction(QuickEntryAction.Save) },
                modifier = Modifier.fillMaxWidth(),
                icon = null,
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    optional: Boolean = false,
) {
    var pickerOpen by remember { mutableStateOf(false) }

    MyFinHubOutlinedField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        supportingText = if (optional) "Προαιρετικό · YYYY-MM-DD" else "YYYY-MM-DD",
        errorMessage = errorMessage,
        trailingIcon = {
            MyFinHubFieldIconButton(
                icon = MyFinHubIcons.Plan,
                contentDescription = "Επιλογή ημερομηνίας",
                onClick = { pickerOpen = true },
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Next,
        ),
    )

    if (pickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value.toDatePickerMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onValueChange(millis.toDateText())
                        }
                        pickerOpen = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) {
                    Text("Επιλογή")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text("Ακύρωση")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
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
                val splitAmountError = state.validationMessage ==
                    "Το ποσό στο μέρος ${index + 1} πρέπει να είναι θετικό."
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
                    MyFinHubOutlinedField(
                        value = part.amountText,
                        onValueChange = { onAction(QuickEntryAction.SplitPartAmountChanged(part.id, it)) },
                        label = "Ποσό μέρους ${index + 1}",
                        suffix = { Text("€") },
                        errorMessage = state.validationMessage.takeIf { splitAmountError },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
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
                    MyFinHubOutlinedField(
                        value = part.label,
                        onValueChange = { onAction(QuickEntryAction.SplitPartLabelChanged(part.id, it)) },
                        label = "Ετικέτα μέρους · προαιρετική",
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                    )
                }
            }

            MyFinHubOutlinedAction(
                label = "+ Προσθήκη μέρους",
                onClick = { onAction(QuickEntryAction.AddSplitPart) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Προσθήκη μέρους σύνθετης αγοράς" },
            )
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
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.first == selectedId }?.second.orEmpty()
    Box(modifier = Modifier.fillMaxWidth()) {
        MyFinHubSelectorButton(
            label = label,
            onClick = { if (choices.isNotEmpty()) expanded = true },
            modifier = modifier,
            enabled = choices.isNotEmpty(),
            errorMessage = errorMessage,
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

private fun isInlineFieldValidation(message: String): Boolean = when {
    message == "Βάλε ποσό μεγαλύτερο από μηδέν." -> true
    message == "Συμπλήρωσε έγκυρη ημερομηνία." -> true
    message == "Διάλεξε διαθέσιμο λογαριασμό." -> true
    message == "Διάλεξε λογαριασμό προέλευσης." -> true
    message == "Διάλεξε λογαριασμό προορισμού." -> true
    message == "Οι λογαριασμοί πρέπει να είναι διαφορετικοί." -> true
    message == "Η ανάληψη πρέπει να καταλήγει σε λογαριασμό μετρητών." -> true
    message == "Η αποταμίευση πρέπει να καταλήγει σε λογαριασμό αποταμίευσης." -> true
    message == "Διάλεξε ενεργή πιστωτική κάρτα." -> true
    message == "Διάλεξε διαθέσιμη κατηγορία." -> true
    message == "Διάλεξε διαθέσιμη υποκατηγορία." -> true
    message == "Συμπλήρωσε το πρόσωπο για τα δανεικά." -> true
    message == "Η αναμενόμενη επιστροφή δεν είναι έγκυρη." -> true
    message == "Η αναμενόμενη επιστροφή δεν μπορεί να είναι πριν από την ημερομηνία κίνησης." -> true
    message == "Συμπλήρωσε έγκυρο πραγματικό υπόλοιπο." -> true
    message.startsWith("Το ποσό στο μέρος ") -> true
    else -> false
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

private fun String.toDatePickerMillis(): Long? = runCatching {
    LocalDate.parse(this).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
}.getOrNull()

private fun Long.toDateText(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneOffset.UTC)
    .toLocalDate()
    .toString()

private fun formatSplitTotal(value: Double): String = if (value % 1.0 == 0.0) {
    value.toLong().toString()
} else {
    String.format(java.util.Locale.US, "%.2f", value)
}
