package app.myfinhub.android.feature.quickentry

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubMotion
import app.myfinhub.android.designsystem.MyFinHubOutlinedAction
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSelectorButton
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FastKinds = listOf(
    QuickEntryKind.EXPENSE,
    QuickEntryKind.INCOME,
    QuickEntryKind.TRANSFER,
)

private val GreekDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("el-GR"))

internal val QuickEntryKind.amountHeading: String
    get() = when (this) {
        QuickEntryKind.EXPENSE -> "Ποσό εξόδου"
        QuickEntryKind.INCOME -> "Ποσό εσόδου"
        QuickEntryKind.TRANSFER -> "Ποσό μεταφοράς"
        QuickEntryKind.WITHDRAWAL -> "Ποσό ανάληψης"
        QuickEntryKind.SAVING -> "Ποσό αποταμίευσης"
        QuickEntryKind.REFUND -> "Ποσό επιστροφής"
        QuickEntryKind.LENDING -> "Ποσό που έδωσες"
        QuickEntryKind.REPAYMENT -> "Ποσό που επέστρεψαν"
        QuickEntryKind.CARD_PURCHASE -> "Ποσό αγοράς"
        QuickEntryKind.CARD_PAYMENT -> "Ποσό εξόφλησης"
        QuickEntryKind.RECONCILIATION -> "Πραγματικό υπόλοιπο"
        QuickEntryKind.SPLIT -> "Συνολικό ποσό"
    }

/**
 * One production editor for all canonical finance kinds. The three everyday kinds stay visible,
 * while More exposes the same form with only the fields required by the selected operation.
 */
@Composable
fun ProductionQuickEntryScreen(
    state: QuickEntryUiState,
    onAction: (QuickEntryAction) -> Unit,
    onBack: () -> Unit,
    mutationInFlight: Boolean = false,
) {
    var noteExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedMenuOpen by remember { mutableStateOf(false) }
    var discardDialogOpen by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    val savedLocally = state.awaitingSync
    val declaredAmount = state.amount
    val splitAllocationReady = state.kind != QuickEntryKind.SPLIT || (
        declaredAmount != null &&
            declaredAmount > 0.0 &&
            state.splitRemaining == 0.0 &&
            state.splitParts.size >= 2 &&
            state.splitParts.all { part ->
                val partAmount = part.amount
                partAmount != null &&
                    partAmount > 0.0 &&
                    state.expenseCategories.any { category ->
                        category.name == part.category &&
                            (part.subcategory.isBlank() || part.subcategory in category.subcategories)
                    }
            }
        )
    val requestBack = {
        if (state.dirty && !state.persisted && !savedLocally) discardDialogOpen = true else onBack()
    }

    LaunchedEffect(state.kind) {
        if (state.kind != QuickEntryKind.RECONCILIATION) amountFocus.requestFocus()
    }
    // Connected saves close only after server acknowledgement. Offline saves close after the durable
    // encrypted local enqueue; only that offline path owns pending-sync/Undo semantics.
    LaunchedEffect(state.persisted, savedLocally) {
        if (state.persisted || savedLocally) onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Νέα κίνηση",
                subtitle = state.kind.description,
                navigation = { MyFinHubBackButton(requestBack) },
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = MyFinHubDesignMetrics.cardElevation,
                shadowElevation = MyFinHubDesignMetrics.cardElevation,
            ) {
                MyFinHubPrimaryAction(
                    label = when {
                        mutationInFlight -> "Αποθήκευση…"
                        state.persisted -> "Αποθηκεύτηκε"
                        savedLocally -> "Αποθηκεύτηκε στη συσκευή"
                        else -> "Αποθήκευση ${state.kind.label.lowercase()}"
                    },
                    enabled = splitAllocationReady && !mutationInFlight && !state.persisted && !savedLocally,
                    onClick = {
                        if (!mutationInFlight) onAction(QuickEntryAction.Save)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MyFinHubDesignMetrics.screenHorizontalPadding,
                            vertical = MyFinHubSpacing.sm,
                        ),
                )
            }
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

            TextButton(onClick = { advancedMenuOpen = true }) {
                Text("Περισσότερα")
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                Text(
                    text = "ΠΟΣΟ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = state.kind.amountHeading,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (state.kind == QuickEntryKind.SPLIT) {
                        "Δήλωσε το σύνολο και μοίρασέ το ακριβώς στα επιμέρους μέρη."
                    } else {
                        "Τα υποχρεωτικά στοιχεία αλλάζουν ανάλογα με τον τύπο."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.kind == QuickEntryKind.RECONCILIATION) {
                MyFinHubOutlinedField(
                    value = state.actualBalanceText,
                    onValueChange = { onAction(QuickEntryAction.ActualBalanceChanged(it)) },
                    label = "Πραγματικό υπόλοιπο",
                    suffix = { Text("€") },
                    errorMessage = state.validationMessage.takeIf {
                        it == "Συμπλήρωσε έγκυρο πραγματικό υπόλοιπο."
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                )
            } else {
                MyFinHubOutlinedField(
                    value = state.amountText,
                    onValueChange = { onAction(QuickEntryAction.AmountChanged(it)) },
                    label = if (state.kind == QuickEntryKind.SPLIT) "Συνολικό ποσό" else "Ποσό",
                    suffix = { Text("€") },
                    errorMessage = state.validationMessage.takeIf {
                        it == "Βάλε ποσό μεγαλύτερο από μηδέν."
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    focusRequester = amountFocus,
                )
            }

            if (state.kind.needsPrimaryAccount) {
                CompactChoice(
                    label = productionPrimaryAccountLabel(state.kind),
                    selectedId = state.accountId,
                    choices = state.accounts.map { it.id to it.label },
                    subtitles = state.accounts.associate { it.id to accountKindLabel(it.kind) },
                    onSelected = { onAction(QuickEntryAction.AccountChanged(it)) },
                    searchable = true,
                )
            }

            if (state.kind.needsTransferAccounts || state.kind == QuickEntryKind.CARD_PAYMENT) {
                CompactChoice(
                    label = "Από λογαριασμό",
                    selectedId = state.fromAccountId,
                    choices = state.accounts.map { it.id to it.label },
                    subtitles = state.accounts.associate { it.id to accountKindLabel(it.kind) },
                    onSelected = { onAction(QuickEntryAction.FromAccountChanged(it)) },
                    searchable = true,
                )
            }

            if (state.kind.needsTransferAccounts) {
                val destinations = productionDestinationOptions(state)
                CompactChoice(
                    label = "Προς λογαριασμό",
                    selectedId = state.toAccountId,
                    choices = destinations.map { it.id to it.label },
                    subtitles = destinations.associate { it.id to accountKindLabel(it.kind) },
                    onSelected = { onAction(QuickEntryAction.ToAccountChanged(it)) },
                    searchable = true,
                )
            }

            if (state.kind.needsCard) {
                CompactChoice(
                    label = "Πιστωτική κάρτα",
                    selectedId = state.cardId,
                    choices = state.creditCards.map { it.id to it.label },
                    subtitles = state.creditCards
                        .filter { it.provider.isNotBlank() }
                        .associate { it.id to it.provider },
                    onSelected = { onAction(QuickEntryAction.CardChanged(it)) },
                    searchable = true,
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
                CompactChoice(
                    label = "Κατηγορία",
                    selectedId = state.category,
                    choices = state.activeCategoryOptions.map { it.name to it.name },
                    onSelected = { onAction(QuickEntryAction.CategoryChanged(it)) },
                    searchable = true,
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

            ProductionDateChoice(
                value = state.dateText,
                onValueChange = { onAction(QuickEntryAction.DateChanged(it)) },
                errorMessage = state.validationMessage.takeIf { it == "Συμπλήρωσε έγκυρη ημερομηνία." },
            )

            if (state.kind == QuickEntryKind.LENDING || state.kind == QuickEntryKind.REPAYMENT) {
                MyFinHubOutlinedField(
                    value = state.person,
                    onValueChange = { onAction(QuickEntryAction.PersonChanged(it)) },
                    label = "Πρόσωπο",
                    errorMessage = state.validationMessage.takeIf {
                        it == "Συμπλήρωσε το πρόσωπο για τα δανεικά."
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
            }

            if (state.kind == QuickEntryKind.LENDING) {
                ProductionDateChoice(
                    value = state.expectedReturnDateText,
                    onValueChange = { onAction(QuickEntryAction.ExpectedReturnDateChanged(it)) },
                    errorMessage = state.validationMessage.takeIf {
                        it == "Η αναμενόμενη επιστροφή δεν είναι έγκυρη." ||
                            it == "Η αναμενόμενη επιστροφή δεν μπορεί να είναι πριν από την ημερομηνία κίνησης."
                    },
                    label = "Αναμενόμενη επιστροφή · προαιρετική",
                    optional = true,
                )
            }

            ProductionTransactionSemanticsHint(state.kind)

            if (state.kind == QuickEntryKind.SPLIT) {
                ProductionSplitEditor(state = state, onAction = onAction)
            }

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
            AnimatedVisibility(
                visible = noteExpanded || state.note.isNotBlank(),
                enter = fadeIn(tween(MyFinHubMotion.QuickDurationMillis)) +
                    expandVertically(tween(MyFinHubMotion.StandardDurationMillis)),
                exit = fadeOut(tween(MyFinHubMotion.QuickDurationMillis)) +
                    shrinkVertically(tween(MyFinHubMotion.StandardDurationMillis)),
            ) {
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

        }
    }

    if (advancedMenuOpen) {
        AdvancedKindSheet(
            selectedKind = state.kind,
            onDismiss = { advancedMenuOpen = false },
            onSelected = { kind ->
                advancedMenuOpen = false
                onAction(QuickEntryAction.SelectKind(kind))
            },
        )
    }

    if (discardDialogOpen) {
        AlertDialog(
            onDismissRequest = { discardDialogOpen = false },
            title = { Text("Απόρριψη νέας κίνησης;") },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactChoice(
    label: String,
    selectedId: String,
    choices: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    subtitles: Map<String, String> = emptyMap(),
    searchable: Boolean = false,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selectedLabel = choices.firstOrNull { it.first == selectedId }?.second ?: "Επιλογή"

    MyFinHubSelectorButton(
        label = label,
        onClick = { sheetOpen = true },
        enabled = choices.isNotEmpty(),
    ) {
        Text(selectedLabel, modifier = Modifier.weight(1f))
        Text("Αλλαγή", style = MaterialTheme.typography.labelMedium)
    }

    if (sheetOpen) {
        val normalizedQuery = query.trim()
        val filteredChoices = if (normalizedQuery.isBlank()) {
            choices
        } else {
            choices.filter { (id, text) ->
                text.contains(normalizedQuery, ignoreCase = true) ||
                    subtitles[id]?.contains(normalizedQuery, ignoreCase = true) == true
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                sheetOpen = false
                query = ""
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MyFinHubDesignMetrics.screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            ) {
                Text(label, style = MaterialTheme.typography.titleLarge)
                if (searchable) {
                    MyFinHubOutlinedField(
                        value = query,
                        onValueChange = { query = it },
                        label = "Αναζήτηση",
                        singleLine = true,
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = MyFinHubSpacing.sm),
                ) {
                    items(filteredChoices, key = { it.first }) { (id, text) ->
                        ListItem(
                            headlineContent = { Text(text) },
                            supportingContent = subtitles[id]?.let { subtitle ->
                                { Text(subtitle) }
                            },
                            trailingContent = if (id == selectedId) {
                                { Text("Επιλεγμένο", color = MaterialTheme.colorScheme.primary) }
                            } else {
                                null
                            },
                            modifier = Modifier.clickable {
                                onSelected(id)
                                sheetOpen = false
                                query = ""
                            },
                        )
                        HorizontalDivider()
                    }
                }
                if (filteredChoices.isEmpty()) {
                    Text(
                        "Δεν βρέθηκε επιλογή.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = MyFinHubSpacing.sm),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedKindSheet(
    selectedKind: QuickEntryKind,
    onDismiss: () -> Unit,
    onSelected: (QuickEntryKind) -> Unit,
) {
    val kinds = QuickEntryKind.entries
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MyFinHubDesignMetrics.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        ) {
            Text("Περισσότεροι τύποι κίνησης", style = MaterialTheme.typography.titleLarge)
            Text(
                "Διάλεξε ειδικό τύπο μόνο όταν δεν είναι απλό έξοδο, έσοδο ή μεταφορά.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .testTag("quick_entry_kind_list"),
                contentPadding = PaddingValues(bottom = MyFinHubSpacing.sm),
            ) {
                items(kinds, key = { it.name }) { kind ->
                    ListItem(
                        headlineContent = { Text(kind.label) },
                        supportingContent = { Text(kind.description) },
                        trailingContent = if (kind == selectedKind) {
                            { Text("Επιλεγμένο", color = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable { onSelected(kind) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ProductionSplitEditor(
    state: QuickEntryUiState,
    onAction: (QuickEntryAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
    ) {
        Text("Κατανομή ποσού", style = MaterialTheme.typography.titleMedium)
        state.splitParts.forEachIndexed { index, part ->
            Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Μέρος ${index + 1}", style = MaterialTheme.typography.labelLarge)
                    if (state.splitParts.size > 2) {
                        TextButton(onClick = { onAction(QuickEntryAction.RemoveSplitPart(part.id)) }) {
                            Text("Αφαίρεση")
                        }
                    }
                }
                MyFinHubOutlinedField(
                    value = part.amountText,
                    onValueChange = {
                        onAction(QuickEntryAction.SplitPartAmountChanged(part.id, it))
                    },
                    label = "Ποσό μέρους ${index + 1}",
                    suffix = { Text("€") },
                    errorMessage = state.validationMessage.takeIf {
                        it == "Το ποσό στο μέρος ${index + 1} πρέπει να είναι θετικό."
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                )
                CompactChoice(
                    label = "Κατηγορία μέρους ${index + 1}",
                    selectedId = part.category,
                    choices = state.expenseCategories.map { it.name to it.name },
                    onSelected = {
                        onAction(QuickEntryAction.SplitPartCategoryChanged(part.id, it))
                    },
                    searchable = true,
                )
                val subcategories = state.expenseCategories.firstOrNull {
                    it.name == part.category
                }?.subcategories.orEmpty()
                if (subcategories.isNotEmpty()) {
                    CompactChoice(
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
                    onValueChange = {
                        onAction(QuickEntryAction.SplitPartLabelChanged(part.id, it))
                    },
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
            modifier = Modifier.fillMaxWidth(),
        )
        val remaining = state.splitRemaining
        Text(
            "Κατανομή: ${formatProductionMoney(state.splitTotal)} €",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = when {
                remaining == null -> "Συμπλήρωσε το συνολικό ποσό."
                remaining == 0.0 -> "Η κατανομή είναι πλήρης."
                remaining > 0.0 -> "Απομένουν ${formatProductionMoney(remaining)} €."
                else -> "Υπέρβαση κατά ${formatProductionMoney(-remaining)} €."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (remaining == 0.0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun ProductionTransactionSemanticsHint(kind: QuickEntryKind) {
    val text = when (kind) {
        QuickEntryKind.TRANSFER -> "Η εσωτερική μεταφορά αλλάζει υπόλοιπα, όχι έσοδα ή έξοδα."
        QuickEntryKind.WITHDRAWAL -> "Η ανάληψη μετακινεί χρήματα από τράπεζα σε μετρητά."
        QuickEntryKind.SAVING -> "Η αποταμίευση μεταφέρει πραγματικά χρήματα στον λογαριασμό αποταμίευσης."
        QuickEntryKind.CARD_PURCHASE -> "Η αγορά αυξάνει την οφειλή της συγκεκριμένης κάρτας."
        QuickEntryKind.CARD_PAYMENT -> "Η πληρωμή μειώνει την οφειλή της κάρτας από τον λογαριασμό πληρωμής."
        QuickEntryKind.RECONCILIATION -> "Καταχωρίζεται η διαφορά από το υπολογισμένο υπόλοιπο, όχι νέο έσοδο ή έξοδο."
        else -> return
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun productionDestinationOptions(state: QuickEntryUiState): List<QuickEntryAccountOption> {
    val filtered = when (state.kind) {
        QuickEntryKind.WITHDRAWAL -> state.accounts.filter { it.kind == "cash" }
        QuickEntryKind.SAVING -> state.accounts.filter { it.kind == "savings" }
        else -> state.accounts
    }.filter { it.id != state.fromAccountId }
    return filtered.ifEmpty { state.accounts.filter { it.id != state.fromAccountId } }
}

private fun productionPrimaryAccountLabel(kind: QuickEntryKind): String = when (kind) {
    QuickEntryKind.INCOME, QuickEntryKind.REFUND, QuickEntryKind.REPAYMENT -> "Προς λογαριασμό"
    QuickEntryKind.RECONCILIATION -> "Λογαριασμός διόρθωσης"
    QuickEntryKind.SPLIT -> "Λογαριασμός πληρωμής"
    else -> "Από λογαριασμό"
}

private fun formatProductionMoney(value: Double): String = if (value % 1.0 == 0.0) {
    value.toLong().toString()
} else {
    String.format(Locale.US, "%.2f", value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    optional: Boolean = false,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val displayValue = when {
        value.isBlank() && optional -> "Δεν έχει οριστεί"
        value.isBlank() -> "Επιλογή ημερομηνίας"
        else -> value.toGreekDateLabel()
    }

    MyFinHubSelectorButton(
        label = label,
        onClick = { pickerOpen = true },
        modifier = modifier,
        errorMessage = errorMessage,
    ) { Text(displayValue) }

    if (optional && errorMessage == null) {
        Text(
            "Προαιρετικό",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (pickerOpen) {
        val pickerState = remember(value) {
            DatePickerState(
                locale = Locale.forLanguageTag("el-GR"),
                initialSelectedDateMillis = value.toDatePickerMillis(),
            )
        }
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onValueChange(it.toIsoDateText()) }
                        pickerOpen = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text("Επιλογή") }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("Ακύρωση") }
            },
        ) {
            GreekDatePicker(
                selectedDate = pickerState.selectedDateMillis?.toIsoDateText()?.toGreekDateLabel()
                    ?: displayValue,
            ) {
                DatePicker(
                    state = pickerState,
                    title = { Text("Επιλογή ημερομηνίας") },
                    headline = { Text(it) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductionDateChoice(
    value: String,
    onValueChange: (String) -> Unit,
    errorMessage: String?,
    label: String = "Ημερομηνία",
    optional: Boolean = false,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val displayValue = when {
        value.isBlank() && optional -> "Δεν έχει οριστεί"
        else -> value.toGreekDateLabel()
    }

    MyFinHubSelectorButton(
        label = label,
        onClick = { pickerOpen = true },
        errorMessage = errorMessage,
    ) {
        Text(displayValue, modifier = Modifier.weight(1f))
        Icon(
            imageVector = MyFinHubIcons.Plan,
            contentDescription = null,
            modifier = Modifier.size(MyFinHubDesignMetrics.standardIconSize),
        )
    }

    if (pickerOpen) {
        val pickerState = remember(value) {
            DatePickerState(
                locale = Locale.forLanguageTag("el-GR"),
                initialSelectedDateMillis = value.toDatePickerMillis(),
            )
        }
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onValueChange(millis.toIsoDateText())
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
            GreekDatePicker(
                selectedDate = pickerState.selectedDateMillis?.toIsoDateText()?.toGreekDateLabel()
                    ?: displayValue,
            ) {
                DatePicker(
                    state = pickerState,
                    title = { Text("Επιλογή ημερομηνίας") },
                    headline = { Text(it) },
                )
            }
        }
    }
}

@Composable
internal fun GreekDatePicker(
    selectedDate: String,
    content: @Composable (String) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val greekConfiguration = remember(configuration) {
        Configuration(configuration).apply { setLocale(Locale.forLanguageTag("el-GR")) }
    }
    val greekContext = remember(context, greekConfiguration) {
        context.createConfigurationContext(greekConfiguration)
    }
    CompositionLocalProvider(
        LocalConfiguration provides greekConfiguration,
        LocalContext provides greekContext,
    ) {
        content(selectedDate)
    }
}

private fun accountKindLabel(kind: String): String = when (kind) {
    "cash" -> "Μετρητά"
    "savings" -> "Αποταμιευτικός λογαριασμός"
    "bank" -> "Τραπεζικός λογαριασμός"
    else -> "Λογαριασμός"
}

private fun String.toDatePickerMillis(): Long? = runCatching {
    LocalDate.parse(this).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
}.getOrNull()

private fun Long.toIsoDateText(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneOffset.UTC)
    .toLocalDate()
    .toString()

internal fun String.toGreekDateLabel(): String = runCatching {
    LocalDate.parse(this).format(GreekDateFormatter)
}.getOrDefault(this)
