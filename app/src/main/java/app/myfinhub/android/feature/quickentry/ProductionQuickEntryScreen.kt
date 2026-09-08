package app.myfinhub.android.feature.quickentry

import android.content.res.Configuration
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
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubHeroCard
import app.myfinhub.android.designsystem.MyFinHubHeroHeading
import app.myfinhub.android.designsystem.MyFinHubIcons
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

private val GreekDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("el", "GR"))

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
                subtitle = "${state.kind.label} · γρήγορη καταχώριση",
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
                        state.persisted -> "Αποθηκεύτηκε"
                        savedLocally -> "Αποθηκεύτηκε στη συσκευή"
                        else -> "Αποθήκευση ${state.kind.label.lowercase()}"
                    },
                    enabled = !state.persisted && !savedLocally,
                    onClick = { onAction(QuickEntryAction.Save) },
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
            MyFinHubHeroCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = MyFinHubDesignMetrics.cardContentPadding,
                    vertical = MyFinHubSpacing.sm,
                ),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    MyFinHubHeroHeading(
                        eyebrow = "Γρήγορη καταχώριση",
                        title = "Πόσο ${state.kind.label.lowercase()};",
                        supporting = "Συμπλήρωσε το ποσό και έλεγξε τα βασικά στοιχεία πριν την αποθήκευση.",
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Box(modifier = Modifier.padding(MyFinHubSpacing.xs)) {
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
                        }
                    }
                }
            }

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
                        subtitles = state.accounts.associate { it.id to accountKindLabel(it.kind) },
                        onSelected = { onAction(QuickEntryAction.AccountChanged(it)) },
                    )
                }
                state.kind == QuickEntryKind.TRANSFER -> {
                    CompactChoice(
                        label = "Από λογαριασμό",
                        selectedId = state.fromAccountId,
                        choices = state.accounts.map { it.id to it.label },
                        subtitles = state.accounts.associate { it.id to accountKindLabel(it.kind) },
                        onSelected = { onAction(QuickEntryAction.FromAccountChanged(it)) },
                    )
                }
            }

            if (state.kind == QuickEntryKind.TRANSFER) {
                val destinationAccounts = state.accounts.filter { it.id != state.fromAccountId }
                CompactChoice(
                    label = "Προς λογαριασμό",
                    selectedId = state.toAccountId,
                    choices = destinationAccounts.map { it.id to it.label },
                    subtitles = destinationAccounts.associate { it.id to accountKindLabel(it.kind) },
                    onSelected = { onAction(QuickEntryAction.ToAccountChanged(it)) },
                )
            }

            if (state.kind.usesCategory) {
                CompactChoice(
                    label = "Κατηγορία",
                    selectedId = state.category,
                    choices = state.activeCategoryOptions.map { it.name to it.name },
                    onSelected = { onAction(QuickEntryAction.CategoryChanged(it)) },
                    searchable = state.activeCategoryOptions.size > 6,
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

            TextButton(onClick = { advancedMenuOpen = true }) {
                Text("Περισσότεροι τύποι κίνησης")
            }
        }
    }

    if (advancedMenuOpen) {
        AdvancedKindSheet(
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
            choices.filter { (_, text) -> text.contains(normalizedQuery, ignoreCase = true) }
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
    onDismiss: () -> Unit,
    onSelected: (QuickEntryKind) -> Unit,
) {
    val kinds = QuickEntryKind.entries.filterNot(FastKinds::contains)
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
                    .heightIn(max = 460.dp),
                contentPadding = PaddingValues(bottom = MyFinHubSpacing.sm),
            ) {
                items(kinds, key = { it.name }) { kind ->
                    ListItem(
                        headlineContent = { Text(kind.label) },
                        supportingContent = { Text(kind.description) },
                        modifier = Modifier.clickable { onSelected(kind) },
                    )
                    HorizontalDivider()
                }
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
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val displayValue = value.toGreekDateLabel()

    MyFinHubSelectorButton(
        label = "Ημερομηνία",
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
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value.toDatePickerMillis(),
        )
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
private fun GreekDatePicker(
    selectedDate: String,
    content: @Composable (String) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val greekConfiguration = remember(configuration) {
        Configuration(configuration).apply { setLocale(Locale("el", "GR")) }
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

private fun String.toGreekDateLabel(): String = runCatching {
    LocalDate.parse(this).format(GreekDateFormatter)
}.getOrDefault(this)
