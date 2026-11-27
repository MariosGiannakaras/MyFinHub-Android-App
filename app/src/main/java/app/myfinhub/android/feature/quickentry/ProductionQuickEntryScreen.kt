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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
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
import app.myfinhub.android.designsystem.MyFinHubSearchField
import app.myfinhub.android.designsystem.MyFinHubSelectorButton
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.myFinHubCategoryIcon
import app.myfinhub.android.core.data.canonicalCounterpartySuggestion
import app.myfinhub.android.feature.receipt.LocalReceiptImportController
import app.myfinhub.android.feature.utilities.amountVisibilityText
import app.myfinhub.android.feature.utilities.rememberAmountVisibilityPreference
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val FastKinds = listOf(
    QuickEntryKind.EXPENSE,
    QuickEntryKind.INCOME,
    QuickEntryKind.TRANSFER,
)

/** A credit purchase is a contextual expense, not an income or transfer action. */
internal fun showCreditPurchaseShortcut(kind: QuickEntryKind, hasCreditCards: Boolean): Boolean =
    kind == QuickEntryKind.EXPENSE && hasCreditCards

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
        QuickEntryKind.CARD_PAYMENT -> "Ποσό πληρωμής"
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
    mutationBlocked: Boolean = false,
    categoryGlyphOverrides: Map<String, String> = emptyMap(),
) {
    var noteExpanded by rememberSaveable { mutableStateOf(false) }
    // A prefilled note starts open; an explicit user toggle must still be respected.
    var noteVisibilityChosen by rememberSaveable { mutableStateOf(false) }
    var advancedMenuOpen by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    val amountsVisible = rememberAmountVisibilityPreference()
    val receiptImport = LocalReceiptImportController.current
    val savedLocally = state.awaitingSync
    val canSubmit = remember(state, mutationBlocked) {
        !mutationBlocked && state.canSubmitDraft()
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
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.imePadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = MyFinHubDesignMetrics.cardElevation,
                shadowElevation = MyFinHubDesignMetrics.cardElevation,
            ) {
                MyFinHubPrimaryAction(
                    label = when {
                        mutationInFlight -> "Καταχώριση…"
                        state.persisted -> "Καταχωρίστηκε"
                        savedLocally -> "Καταχωρίστηκε στη συσκευή"
                        else -> "Καταχώριση"
                    },
                    enabled = canSubmit && !mutationInFlight &&
                        !state.persisted && !savedLocally,
                    onClick = {
                        if (!mutationInFlight && !mutationBlocked) onAction(QuickEntryAction.Save)
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
            if (mutationBlocked) {
                Text(
                    text = "Η καταχώριση δεν είναι διαθέσιμη όσο εκκρεμεί σφάλμα συγχρονισμού. " +
                        "Έλεγξε την ειδοποίηση σφάλματος και δοκίμασε ξανά.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("quick_entry_mutation_blocked"),
                )
            }
            if (state.recurringId.isNotEmpty()) {
                Text(
                    text = "Συνδεδεμένη πληρωμή παγίου · " +
                        state.note.removePrefix("Πληρωμή: ").ifBlank { "Έξοδο" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("quick_entry_recurring_link"),
                )
            }
            if (receiptImport != null && state.kind == QuickEntryKind.EXPENSE &&
                state.amountText.isBlank() && state.note.isBlank() && state.recurringId.isBlank() &&
                state.counterpartyId.isBlank() && !state.debitExpenseRequiresAccountChoice &&
                !state.persisted && !savedLocally && !state.receiptDraft
            ) {
                OutlinedButton(
                    onClick = receiptImport.open,
                    enabled = !mutationInFlight && !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("quick_entry_receipt"),
                ) { Text("Εισαγωγή απόδειξης / SMS") }
            }
            Text("Τύπος", style = MaterialTheme.typography.labelLarge)
            if (state.kind in FastKinds) {
                val quickKindChip: @Composable (QuickEntryKind, Modifier) -> Unit = { kind, modifier ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = { onAction(QuickEntryAction.SelectKind(kind)) },
                        label = { Text(kind.label) },
                        modifier = modifier.heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget),
                    )
                }
                if (LocalDensity.current.fontScale >= 1.3f) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                    ) {
                        FastKinds.forEach { quickKindChip(it, Modifier.fillMaxWidth()) }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                    ) {
                        FastKinds.forEach { quickKindChip(it, Modifier) }
                    }
                }
                if (showCreditPurchaseShortcut(state.kind, state.creditCards.isNotEmpty())) {
                    TextButton(
                        onClick = { onAction(QuickEntryAction.SelectKind(QuickEntryKind.CARD_PURCHASE)) },
                        modifier = Modifier.heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget)
                            .testTag("quick_entry_credit_purchase_shortcut"),
                    ) {
                        Text("Αγορά με πιστωτική")
                    }
                }
                TextButton(onClick = { advancedMenuOpen = true }) {
                    Text("Περισσότεροι τύποι")
                }
            } else {
                // One visible selection, with all kinds available in the same picker.
                OutlinedButton(
                    onClick = { advancedMenuOpen = true },
                    modifier = Modifier.testTag("quick_entry_selected_special_kind"),
                ) {
                    Text("${state.kind.label} · Αλλαγή τύπου")
                }
            }

            if (state.kind == QuickEntryKind.SPLIT) {
                Text(
                    "Δήλωσε το σύνολο και μοίρασέ το ακριβώς στα επιμέρους μέρη.",
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
                    label = state.kind.amountHeading,
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
                val primaryAccounts = if (state.bankAlertDraft) state.accounts.filter { it.isPiraeusFundingAccount }
                    else state.accounts
                CompactChoice(
                    label = productionPrimaryAccountLabel(state.kind),
                    selectedId = state.accountId,
                    choices = primaryAccounts.map { it.id to it.label },
                    subtitles = primaryAccounts.associate { it.id to accountKindLabel(it.kind) },
                    onSelected = { onAction(QuickEntryAction.AccountChanged(it)) },
                    searchable = true,
                )
            }

            if (state.kind.needsTransferAccounts || state.kind == QuickEntryKind.CARD_PAYMENT) {
                val sources = productionSourceOptions(state)
                if (state.kind == QuickEntryKind.CARD_PAYMENT && sources.isEmpty()) {
                    Text(
                        "Δεν υπάρχει διαθέσιμος λογαριασμός της ίδιας τράπεζας για πληρωμή αυτής της κάρτας.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    CompactChoice(
                        label = "Από λογαριασμό",
                        selectedId = state.fromAccountId,
                        choices = sources.map { it.id to it.label },
                        subtitles = sources.associate { it.id to accountKindLabel(it.kind) },
                        onSelected = { onAction(QuickEntryAction.FromAccountChanged(it)) },
                        searchable = true,
                    )
                }
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
                val eligibleCards = state.bankAlertCreditOptions()
                CompactChoice(
                    label = "Πιστωτική κάρτα",
                    selectedId = state.cardId,
                    choices = eligibleCards.map { it.id to it.label },
                    subtitles = eligibleCards
                        .filter { it.provider.isNotBlank() }
                        .associate { it.id to it.provider },
                    onSelected = { onAction(QuickEntryAction.CardChanged(it)) },
                    searchable = true,
                )
                if (eligibleCards.isEmpty()) {
                    Text(
                        if (state.bankAlertDraft) "Δεν βρέθηκε ενεργή πιστωτική Πειραιώς με τα ίδια τελευταία ψηφία." else "Δεν υπάρχει ενεργή πιστωτική κάρτα.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (state.kind == QuickEntryKind.CARD_PAYMENT) {
                // The optimistic write projection immediately settles the card debt.
                // While the server still acknowledges the payment, zero debt must
                // not appear as a false red validation failure to the owner.
                if (mutationInFlight) {
                    Text(
                        "Η πληρωμή καταχωρίζεται…",
                        modifier = Modifier.testTag("quick_entry_card_debt"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else state.cardPaymentDebt?.let { debt ->
                    val debtLabel = if (debt.isFinite() && debt > 0.005) {
                        "Οφειλή κάρτας: " + amountVisibilityText(
                            String.format(java.util.Locale.forLanguageTag("el-GR"), "%.2f", debt) + " €",
                            amountsVisible,
                        )
                    } else {
                        "Δεν υπάρχει καταγεγραμμένη οφειλή για την επιλεγμένη ημερομηνία."
                    }
                    Text(
                        debtLabel,
                        modifier = Modifier.testTag("quick_entry_card_debt"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (debt.isFinite() && debt > 0.005)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                    )
                }
                val payableStatements = payableCreditStatementOptions(
                    state.cardId, state.creditStatements,
                )
                if (payableStatements.isNotEmpty()) {
                    CompactChoice(
                        label = "Δήλωση πιστωτικής (προαιρετικά)",
                        selectedId = state.statementId,
                        choices = listOf("" to "Χωρίς σύνδεση με δήλωση") +
                            payableStatements.map { it.id to it.label },
                        onSelected = { onAction(QuickEntryAction.StatementChanged(it)) },
                        searchable = true,
                    )
                }
            }

            if (state.kind.usesCategory) {
                CompactChoice(
                    label = "Κατηγορία",
                    selectedId = state.category,
                    choices = state.activeCategoryOptions.map { it.name to it.name },
                    choiceIcon = { category ->
                        quickEntryCategoryIcon(category, null, state.kind == QuickEntryKind.INCOME, categoryGlyphOverrides)
                    },
                    onSelected = { onAction(QuickEntryAction.CategoryChanged(it)) },
                    searchable = true,
                )
                if (state.activeSubcategoryOptions.isNotEmpty()) {
                    CompactChoice(
                        label = "Υποκατηγορία",
                        selectedId = state.subcategory,
                        choices = listOf("" to "Χωρίς υποκατηγορία") +
                            state.activeSubcategoryOptions.map { it to it },
                        choiceIcon = { subcategory ->
                            if (subcategory.isBlank()) null else quickEntryCategoryIcon(
                                state.category, subcategory, state.kind == QuickEntryKind.INCOME, categoryGlyphOverrides,
                            )
                        },
                        onSelected = { onAction(QuickEntryAction.SubcategoryChanged(it)) },
                    )
                }
            }

            val counterpartyKind =
                app.myfinhub.android.core.data.canonicalCounterpartyKindForEvent(
                    state.kind.canonicalKind
                )
            if (counterpartyKind != null &&
                state.counterparties.any { it.kind == counterpartyKind }
            ) {
                val choices = state.counterparties.filter {
                    it.kind == counterpartyKind &&
                        state.counterparties.count { row -> row.id == it.id } == 1
                }.map { it.id to it.displayName }
                CompactChoice(
                    label = if (counterpartyKind == "merchant") "Έμπορος" else "Δικαιούχος",
                    selectedId = state.counterpartyId,
                    choices = listOf("" to "Χωρίς σύνδεση") + choices,
                    onSelected = { onAction(QuickEntryAction.CounterpartyChanged(it)) },
                    searchable = true,
                )
                if (state.counterpartyId.isBlank()) {
                    canonicalCounterpartySuggestion(
                        records = state.counterparties,
                        label = state.note,
                        kind = counterpartyKind,
                    )?.let { suggested ->
                        Text(
                            "Πιθανή αντιστοίχιση: ${suggested.displayName}. " +
                                "Δεν επιλέγεται αυτόματα.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("quick_entry_counterparty_suggestion"),
                        )
                    }
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

            if (state.kind == QuickEntryKind.REPAYMENT && state.person.isNotBlank()) {
                state.lendingOutstanding?.let { debt ->
                    val available = debt.isFinite() && debt > 0.005
                    val overpaid = available && (state.amount ?: 0.0) > debt + 0.01
                    val formatted = if (available) {
                        amountVisibilityText(
                            String.format(java.util.Locale.forLanguageTag("el-GR"), "%.2f", debt) + " €",
                            amountsVisible,
                        )
                    } else ""
                    Text(
                        text = when {
                            !available -> "Δεν υπάρχει καταγεγραμμένη οφειλή για αυτό το πρόσωπο."
                            overpaid -> "Το ποσό ξεπερνά την οφειλή του προσώπου: $formatted."
                            else -> "Οφειλή προσώπου στην επιλεγμένη ημερομηνία: $formatted."
                        },
                        modifier = Modifier.testTag("quick_entry_lending_debt"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!available || overpaid) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                if (state.safeWebExpenseSplitSuggestions().isNotEmpty()) {
                    MyFinHubOutlinedAction(
                        label = "Πρόταση κατανομής από τη σημείωση",
                        onClick = { onAction(QuickEntryAction.ApplySuggestedExpenseSplit) },
                        modifier = Modifier.fillMaxWidth()
                            .testTag("quick_entry_split_suggest"),
                    )
                }
                ProductionSplitEditor(
                    state = state, onAction = onAction, categoryGlyphOverrides = categoryGlyphOverrides,
                )
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

            val noteVisible = if (noteVisibilityChosen) noteExpanded else state.note.isNotBlank()
            TextButton(
                onClick = {
                    noteExpanded = !noteVisible
                    noteVisibilityChosen = true
                },
                modifier = Modifier.heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget),
            ) {
                Text(if (noteVisible) "Απόκρυψη σημείωσης" else if (state.note.isNotBlank()) "Εμφάνιση σημείωσης" else "Προσθήκη σημείωσης")
            }
            AnimatedVisibility(
                visible = noteVisible,
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
    choiceIcon: ((String) -> ImageVector?)? = null,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val identityCounts = choices.groupingBy { it.first }.eachCount()
    val selectedLabel = when {
        identityCounts[selectedId]?.let { it > 1 } == true -> "Η επιλογή δεν είναι μοναδική"
        else -> choices.firstOrNull { it.first == selectedId }?.second ?: when {
            choices.isEmpty() -> "Δεν υπάρχουν διαθέσιμες επιλογές"
            selectedId.isNotBlank() -> "Η επιλογή δεν είναι πλέον διαθέσιμη"
            else -> "Επιλογή"
        }
    }

    MyFinHubSelectorButton(
        label = label,
        onClick = { sheetOpen = true },
        enabled = choices.isNotEmpty(),
        modifier = Modifier.semantics {
            contentDescription = "$label: $selectedLabel. Αλλαγή επιλογής"
        },
    ) {
        choiceIcon?.takeIf { identityCounts[selectedId] == 1 }?.invoke(selectedId)?.let { vector ->
            Icon(imageVector = vector, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Text(selectedLabel, modifier = Modifier.weight(1f))
        Icon(
            imageVector = MyFinHubIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
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
                    MyFinHubSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Αναζήτηση επιλογής",
                        modifier = Modifier.testTag("quick_entry_choice_search"),
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = MyFinHubSpacing.sm),
                ) {
                    // Imported source IDs may repeat. Render every option without using
                    // an ambiguous external ID as the sole Compose LazyColumn key.
                    itemsIndexed(
                        filteredChoices,
                        key = { index, choice -> "choice-$index-${choice.first}" },
                    ) { _, (id, text) ->
                        val uniqueIdentity = identityCounts[id] == 1
                        val vector = choiceIcon?.invoke(id)
                        ListItem(
                            headlineContent = { Text(text) },
                            leadingContent = if (vector == null) null else {
                                { Icon(imageVector = vector, contentDescription = null) }
                            },
                            supportingContent = if (!uniqueIdentity) {
                                {
                                    Text(
                                        "Μη μοναδική ταυτότητα · δεν μπορεί να επιλεγεί",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            } else subtitles[id]?.let { subtitle ->
                                { Text(subtitle) }
                            },
                            trailingContent = if (uniqueIdentity && id == selectedId) {
                                { Text("Επιλεγμένο", color = MaterialTheme.colorScheme.primary) }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget)
                                .clickable(enabled = uniqueIdentity) {
                                    onSelected(id)
                                    sheetOpen = false
                                    query = ""
                                }
                                .semantics { selected = uniqueIdentity && id == selectedId },
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget)
                            .clickable { onSelected(kind) }
                            .semantics { selected = kind == selectedKind },
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
    categoryGlyphOverrides: Map<String, String>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
    ) {
        Text("Κατανομή ποσού", style = MaterialTheme.typography.titleMedium)
        val largeFont = LocalDensity.current.fontScale >= 1.3f
        state.splitParts.forEachIndexed { index, part ->
            Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                val partLabel: @Composable () -> Unit = {
                    Text("Μέρος ${index + 1}", style = MaterialTheme.typography.labelLarge)
                }
                val removePart: @Composable () -> Unit = {
                    if (state.splitParts.size > 2) {
                        TextButton(
                            onClick = { onAction(QuickEntryAction.RemoveSplitPart(part.id)) },
                            modifier = Modifier
                                .heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget)
                                .semantics {
                                    contentDescription = "Αφαίρεση μέρους ${index + 1}"
                                },
                        ) { Text("Αφαίρεση") }
                    }
                }
                if (largeFont) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                        partLabel()
                        removePart()
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        partLabel()
                        removePart()
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
                    choiceIcon = { category ->
                        quickEntryCategoryIcon(category, null, false, categoryGlyphOverrides)
                    },
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
                        choiceIcon = { subcategory ->
                            if (subcategory.isBlank()) null else quickEntryCategoryIcon(
                                part.category, subcategory, false, categoryGlyphOverrides,
                            )
                        },
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
                if (index != state.splitParts.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
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

private fun productionSourceOptions(state: QuickEntryUiState): List<QuickEntryAccountOption> =
    when (state.kind) {
        QuickEntryKind.WITHDRAWAL -> if (state.bankAlertDraft) state.accounts.filter { it.isPiraeusFundingAccount }
            else state.accounts
        QuickEntryKind.SAVING ->
            state.accounts.filter(QuickEntryAccountOption::isSavingContributionSource)
        QuickEntryKind.CARD_PAYMENT -> state.cardPaymentSourceAccountIds?.let { ids ->
            state.accounts.filter { it.id in ids }
        } ?: state.accounts
        else -> state.accounts
    }

private fun productionDestinationOptions(state: QuickEntryUiState): List<QuickEntryAccountOption> {
    val filtered = when (state.kind) {
        QuickEntryKind.WITHDRAWAL -> state.accounts.filter { it.kind == "cash" }
        QuickEntryKind.SAVING -> state.accounts.filter(QuickEntryAccountOption::isSavingsDestination)
        else -> state.accounts
    }.filter { it.id != state.fromAccountId }
    return if (state.kind == QuickEntryKind.SAVING || state.bankAlertDraft) filtered
        else filtered.ifEmpty { state.accounts.filter { it.id != state.fromAccountId } }
}

/**
 * Reuse the selected Web icon preference for Quick Entry, including split part editors.
 * Unknown external SVG keys safely use Android's bundled category fallback; no URL is loaded.
 */
internal fun quickEntryCategoryIcon(
    category: String,
    subcategory: String?,
    isIncome: Boolean,
    overrides: Map<String, String>,
): ImageVector {
    val key = (if (isIncome) "income:" else "expense:") + category
    val preferred = subcategory?.takeIf(String::isNotBlank)?.let { overrides["$key:$it"] }
        ?: overrides[key]
    return myFinHubCategoryIcon(category, MyFinHubIcons.All, preferred)
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
                    modifier = Modifier.heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget),
                ) { Text("Επιλογή") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pickerOpen = false },
                    modifier = Modifier.heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget),
                ) { Text("Ακύρωση") }
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    var pickerRequested by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    // Prefer opening after the keyboard closes, but do not strand the user
    // when an OEM IME or emulator fails to report a final zero inset.
    LaunchedEffect(pickerRequested, imeBottom) {
        if (pickerRequested) {
            if (imeBottom > 0) delay(600)
            pickerOpen = true
            pickerRequested = false
        }
    }
    val displayValue = when {
        value.isBlank() && optional -> "Δεν έχει οριστεί"
        else -> value.toGreekDateLabel()
    }

    MyFinHubSelectorButton(
        label = label,
        // A focused amount field can keep the IME over the calendar on
        // compact windows; opening is deferred until IME insets reach zero.
        onClick = {
            focusManager.clearFocus()
            keyboardController?.hide()
            pickerRequested = true
        },
        modifier = Modifier
            .testTag(if (optional) "quick_entry_optional_date_picker" else "quick_entry_date_picker")
            .semantics { contentDescription = "$label: $displayValue" },
        errorMessage = errorMessage,
    ) {
        Text(displayValue, modifier = Modifier.weight(1f))
        Icon(
            imageVector = MyFinHubIcons.Plan,
            contentDescription = null,
            modifier = Modifier.size(MyFinHubDesignMetrics.standardIconSize),
        )
    }

    if (optional && value.isNotBlank()) {
        TextButton(
            onClick = { onValueChange("") },
            modifier = Modifier.heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget)
                .testTag("quick_entry_optional_date_clear")
                .semantics { contentDescription = "Καθαρισμός: $label" },
        ) { Text("Καθαρισμός ημερομηνίας") }
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
                    modifier = Modifier.heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget),
                ) {
                    Text("Επιλογή")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pickerOpen = false },
                    modifier = Modifier.heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget),
                ) {
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
