from __future__ import annotations

from pathlib import Path

ROOT = Path.cwd()


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, text: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, got {count}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


def replace_between(path: str, start_marker: str, end_marker: str, replacement: str) -> None:
    text = read(path)
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"{path}: start marker not found: {start_marker!r}")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"{path}: end marker not found: {end_marker!r}")
    write(path, text[:start] + replacement + text[end:])


# Production projection must come from canonical data. Editor drafts live in the editor itself.
replace_once(
    "app/src/main/java/app/myfinhub/android/app/CanonicalPlanProjection.kt",
    '''        budget = previous?.budget ?: BudgetDraft(
            monthlyLimitText = canonicalBudget?.amount?.toPlainPlanMoney() ?: "",
            alertThresholdText = (canonicalBudget?.alertThreshold ?: 80).toString(),
        ),''',
    '''        budget = BudgetDraft(
            monthlyLimitText = canonicalBudget?.amount?.toPlainPlanMoney() ?: "",
            alertThresholdText = (canonicalBudget?.alertThreshold ?: 80).toString(),
        ),''',
)

# Give the production budget editor one atomic mutation entry point; do not mutate UI projection first.
vm_path = "app/src/main/java/app/myfinhub/android/app/FinanceProductViewModel.kt"
vm_marker = '''    fun onPlanAction(action: PlanAction) {
'''
vm_method = '''    fun saveOverallBudget(monthlyLimitText: String, alertThresholdText: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val amount = monthlyLimitText.replace(',', '.').toDoubleOrNull()
        val threshold = alertThresholdText.toIntOrNull()
        if (amount == null || amount <= 0.0 || threshold == null || threshold !in 1..100) return

        applyMutation(
            UpsertOverallBudget(
                month = YearMonth.now().toString(),
                amount = amount,
                alertThreshold = threshold,
                budgetId = "budget-android-${UUID.randomUUID()}",
                nowIso = Instant.now().toString(),
            ),
        )
    }

'''
text = read(vm_path)
if vm_method.strip() in text:
    raise SystemExit("FinanceProductViewModel: saveOverallBudget already present")
if text.count(vm_marker) != 1:
    raise SystemExit("FinanceProductViewModel: onPlanAction marker mismatch")
write(vm_path, text.replace(vm_marker, vm_method + vm_marker, 1))

# Root forwards the mutation lifecycle and direct budget mutation callback to the editor.
root_path = "app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt"
replace_once(
    root_path,
    '''                            onPlanAction = financeViewModel::onPlanAction,
                            onCardDetailOpened = cardSecretViewModel::openCard,''',
    '''                            onPlanAction = financeViewModel::onPlanAction,
                            onSaveBudget = financeViewModel::saveOverallBudget,
                            onCardDetailOpened = cardSecretViewModel::openCard,''',
)
replace_once(
    root_path,
    '''    onPlanAction: (app.myfinhub.android.feature.plan.PlanAction) -> Unit,
    onCardDetailOpened: (String) -> Unit,''',
    '''    onPlanAction: (app.myfinhub.android.feature.plan.PlanAction) -> Unit,
    onSaveBudget: (String, String) -> Unit,
    onCardDetailOpened: (String) -> Unit,''',
)
replace_once(
    root_path,
    '''                    planState = projection.planState,
                    onPlanAction = onPlanAction,
                    insightsState = projection.insightsState,''',
    '''                    planState = projection.planState,
                    onPlanAction = onPlanAction,
                    onSaveBudget = onSaveBudget,
                    planMutationInFlight = state.saving,
                    planMutationBlocked = state.issue != null,
                    insightsState = projection.insightsState,''',
)

# Production navigation: recording a scheduled item creates today's actual movement, not a backdated one.
app_path = "app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt"
replace_once(
    app_path,
    '''import app.myfinhub.android.feature.plan.PlanViewModel
import app.myfinhub.android.feature.plan.PlannedFlow''',
    '''import app.myfinhub.android.feature.plan.PlanViewModel
import app.myfinhub.android.feature.plan.PlannedFlow
import app.myfinhub.android.feature.plan.PlannedItem''',
)
replace_once(
    app_path,
    '''    planState: PlanUiState = PlanUiState(),
    onPlanAction: (PlanAction) -> Unit = {},
    insightsState: InsightsUiState = InsightsUiState(),''',
    '''    planState: PlanUiState = PlanUiState(),
    onPlanAction: (PlanAction) -> Unit = {},
    onSaveBudget: (String, String) -> Unit = { _, _ -> },
    planMutationInFlight: Boolean = false,
    planMutationBlocked: Boolean = false,
    insightsState: InsightsUiState = InsightsUiState(),''',
)
old_record = '''                        onRecordItem = { item ->
                            val kind = when (item.flow) {
                                PlannedFlow.OBLIGATION -> QuickEntryKind.EXPENSE
                                PlannedFlow.INCOME -> QuickEntryKind.INCOME
                                PlannedFlow.TRANSFER -> null
                            }
                            if (kind != null) {
                                onQuickEntryAction(QuickEntryAction.Reset)
                                onQuickEntryAction(QuickEntryAction.SelectKind(kind))
                                onQuickEntryAction(QuickEntryAction.AmountChanged(item.amount.toString()))
                                if (item.dueDateIso.isNotBlank()) {
                                    onQuickEntryAction(QuickEntryAction.DateChanged(item.dueDateIso))
                                }
                                val categoryExists = when (kind) {
                                    QuickEntryKind.INCOME -> quickEntryState.incomeCategories
                                    else -> quickEntryState.expenseCategories
                                }.any { option -> option.name == item.category }
                                if (item.category.isNotBlank() && categoryExists) {
                                    onQuickEntryAction(QuickEntryAction.CategoryChanged(item.category))
                                }
                                onQuickEntryAction(QuickEntryAction.NoteChanged(item.note.ifBlank { item.title }))
                                planBackStack.pushIfNew(AppRoute.QuickEntry)
                            }
                        },'''
new_record = '''                        onRecordItem = { item ->
                            val actions = plannedItemQuickEntryPrefillActions(item, quickEntryState)
                            if (actions.isNotEmpty()) {
                                actions.forEach(onQuickEntryAction)
                                planBackStack.pushIfNew(AppRoute.QuickEntry)
                            }
                        },'''
replace_once(app_path, old_record, new_record)
replace_once(
    app_path,
    '''                        CanonicalBudget2026Screen(
                            state = planState,
                            onAction = onPlanAction,
                            onBack = { planBackStack.removeLastOrNull() },
                        )''',
    '''                        CanonicalBudget2026Screen(
                            state = planState,
                            onSaveBudget = onSaveBudget,
                            mutationInFlight = planMutationInFlight,
                            mutationBlocked = planMutationBlocked,
                            onBack = { planBackStack.removeLastOrNull() },
                        )''',
)

# Pure prefill policy makes it testable that a due date is never reused as an actual transaction date.
app = read(app_path)
insert_marker = '''private fun HomeQuickEntryType.toQuickEntryKind(): QuickEntryKind = when (this) {'''
helper = '''internal fun plannedItemQuickEntryPrefillActions(
    item: PlannedItem,
    quickEntryState: QuickEntryUiState,
): List<QuickEntryAction> {
    val kind = when (item.flow) {
        PlannedFlow.OBLIGATION -> QuickEntryKind.EXPENSE
        PlannedFlow.INCOME -> QuickEntryKind.INCOME
        PlannedFlow.TRANSFER -> return emptyList()
    }
    val categoryOptions = when (kind) {
        QuickEntryKind.INCOME -> quickEntryState.incomeCategories
        else -> quickEntryState.expenseCategories
    }
    return buildList {
        add(QuickEntryAction.Reset)
        add(QuickEntryAction.SelectKind(kind))
        add(QuickEntryAction.AmountChanged(item.amount.toString()))
        if (item.category.isNotBlank() && categoryOptions.any { option -> option.name == item.category }) {
            add(QuickEntryAction.CategoryChanged(item.category))
        }
        add(QuickEntryAction.NoteChanged(item.note.ifBlank { item.title }))
    }
}

'''
if app.count(insert_marker) != 1:
    raise SystemExit("MyFinHubApp: helper insertion marker mismatch")
write(app_path, app.replace(insert_marker, helper + insert_marker, 1))

# Replace budget editor with a lifecycle-aware canonical editor.
screen_path = "app/src/main/java/app/myfinhub/android/feature/plan/CanonicalPlanS7Screens.kt"
replace_once(
    screen_path,
    '''import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue''',
    '''import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue''',
)
new_budget = r'''/** Overall canonical monthly budget only; unsupported category-budget drafts remain out of production. */
@Composable
fun CanonicalBudget2026Screen(
    state: PlanUiState,
    onSaveBudget: (String, String) -> Unit,
    onBack: () -> Unit,
    mutationInFlight: Boolean = false,
    mutationBlocked: Boolean = false,
) {
    var savedLimit by rememberSaveable { mutableStateOf(state.budget.monthlyLimitText) }
    var savedThreshold by rememberSaveable { mutableStateOf(state.budget.alertThresholdText) }
    var limitDraft by rememberSaveable { mutableStateOf(state.budget.monthlyLimitText) }
    var thresholdDraft by rememberSaveable { mutableStateOf(state.budget.alertThresholdText) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var discardDialogOpen by rememberSaveable { mutableStateOf(false) }
    var saveRequested by rememberSaveable { mutableStateOf(false) }

    val savedBudget = BudgetDraft(savedLimit, savedThreshold)
    val draftBudget = BudgetDraft(limitDraft, thresholdDraft)
    val dirty = !budgetDraftEquivalent(savedBudget, draftBudget)
    val previewState = state.copy(budget = draftBudget)
    val progress = canonicalBudgetProgress(previewState)
    val canonicalMatchesDraft = budgetDraftEquivalent(state.budget, draftBudget)
    val canonicalChangedFromSaved = !budgetDraftEquivalent(state.budget, savedBudget)

    LaunchedEffect(
        state.budget.monthlyLimitText,
        state.budget.alertThresholdText,
        mutationInFlight,
        mutationBlocked,
        saveRequested,
        dirty,
    ) {
        if (!saveRequested && !dirty) {
            savedLimit = state.budget.monthlyLimitText
            savedThreshold = state.budget.alertThresholdText
            limitDraft = state.budget.monthlyLimitText
            thresholdDraft = state.budget.alertThresholdText
        }
        if (
            saveRequested &&
            !mutationInFlight &&
            !mutationBlocked &&
            canonicalMatchesDraft &&
            canonicalChangedFromSaved
        ) {
            onBack()
        }
    }

    fun requestBack() {
        when {
            mutationInFlight -> Unit
            saveRequested && !mutationBlocked -> Unit
            saveRequested && mutationBlocked -> onBack()
            dirty -> discardDialogOpen = true
            else -> onBack()
        }
    }

    BackHandler(enabled = dirty || mutationInFlight || saveRequested) { requestBack() }

    if (discardDialogOpen) {
        AlertDialog(
            onDismissRequest = { discardDialogOpen = false },
            title = { Text("Απόρριψη αλλαγών;") },
            text = { Text("Οι μη αποθηκευμένες αλλαγές του προϋπολογισμού θα χαθούν.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        discardDialogOpen = false
                        onBack()
                    },
                ) { Text("Απόρριψη") }
            },
            dismissButton = {
                TextButton(onClick = { discardDialogOpen = false }) { Text("Συνέχεια επεξεργασίας") }
            },
        )
    }

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Μηνιαίος προϋπολογισμός",
                subtitle = state.budgetMonthLabel.ifBlank { "Συνολικό όριο τρέχοντος μήνα" },
                navigation = { MyFinHubBackButton(::requestBack) },
            )
        },
        bottomBar = {
            Surface {
                MyFinHubPrimaryAction(
                    label = when {
                        mutationInFlight -> "Αποθήκευση…"
                        saveRequested -> "Αναμονή επιβεβαίωσης…"
                        mutationBlocked -> "Χρειάζεται συγχρονισμός"
                        else -> "Αποθήκευση προϋπολογισμού"
                    },
                    enabled = dirty && !saveRequested && !mutationInFlight && !mutationBlocked,
                    onClick = {
                        val limit = limitDraft.replace(',', '.').toDoubleOrNull()
                        val threshold = thresholdDraft.toIntOrNull()
                        localError = when {
                            limit == null || limit <= 0.0 -> "Το μηνιαίο όριο πρέπει να είναι μεγαλύτερο από μηδέν."
                            threshold == null || threshold !in 1..100 -> "Το όριο προειδοποίησης πρέπει να είναι από 1 έως 100%."
                            else -> null
                        }
                        if (localError == null) {
                            saveRequested = true
                            onSaveBudget(limitDraft, thresholdDraft)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("s7_budget_save")
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = MyFinHubDesignMetrics.screenHorizontalPadding, vertical = MyFinHubSpacing.sm),
                    icon = MyFinHubIcons.Savings,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("s7_budget_root"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = padding.calculateTopPadding() + MyFinHubSpacing.xs,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = padding.calculateBottomPadding() + MyFinHubSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
        ) {
            if (progress != null) {
                item("budget-progress") {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                            MyFinHubSectionHeading(
                                title = "Πρόοδος μήνα",
                                subtitle = "${formatPlanS7Euro(progress.spent)} από ${formatPlanS7Euro(progress.limit)}",
                                icon = MyFinHubIcons.Savings,
                                tone = FinanceTone.Savings,
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (progress.remaining >= 0.0) "Υπόλοιπο" else "Υπέρβαση")
                                MyFinHubAmountText(
                                    formatPlanS7Euro(abs(progress.remaining)),
                                    if (progress.remaining >= 0.0) FinanceTone.Savings else FinanceTone.Expense,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (progress.thresholdReached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "${progress.percent}% του ορίου",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (progress.thresholdReached) {
                                Text(
                                    "Οι δαπάνες έχουν φτάσει το όριο προειδοποίησης ${progress.threshold}%.",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.testTag("s7_budget_threshold_warning"),
                                )
                            }
                        }
                    }
                }
            }

            item("budget-editor") {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        MyFinHubOutlinedField(
                            value = limitDraft,
                            onValueChange = {
                                limitDraft = it
                                localError = null
                            },
                            label = "Μηνιαίο όριο",
                            modifier = Modifier.testTag("s7_budget_limit"),
                            suffix = { Text("€") },
                            errorMessage = localError.takeIf { it?.contains("μηνιαίο όριο") == true },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            enabled = !mutationInFlight && !saveRequested,
                        )
                        MyFinHubOutlinedField(
                            value = thresholdDraft,
                            onValueChange = {
                                thresholdDraft = it.filter(Char::isDigit).take(3)
                                localError = null
                            },
                            label = "Όριο προειδοποίησης",
                            modifier = Modifier.testTag("s7_budget_threshold"),
                            suffix = { Text("%") },
                            errorMessage = localError.takeIf { it?.contains("προειδοποίησης") == true },
                            supportingText = if (localError?.contains("προειδοποίησης") == true) null else
                                "Από 1 έως 100% · επισημαίνει την πρόοδο μέσα στο MyFinHub",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            enabled = !mutationInFlight && !saveRequested,
                        )
                        progress?.threshold?.let { threshold ->
                            Text(
                                "Η ένδειξη προόδου επισημαίνεται στο $threshold% του συνολικού ορίου.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun budgetDraftEquivalent(first: BudgetDraft, second: BudgetDraft): Boolean {
    val firstLimit = first.monthlyLimitText.replace(',', '.').toDoubleOrNull()
    val secondLimit = second.monthlyLimitText.replace(',', '.').toDoubleOrNull()
    val sameLimit = when {
        firstLimit == null || secondLimit == null -> first.monthlyLimitText.trim() == second.monthlyLimitText.trim()
        else -> abs(firstLimit - secondLimit) < 0.005
    }
    return sameLimit && first.alertThresholdText.toIntOrNull() == second.alertThresholdText.toIntOrNull()
}

'''
replace_between(
    screen_path,
    "/** Overall canonical monthly budget only; unsupported category-budget drafts remain out of production. */",
    "private fun planS7Tone(flow: PlannedFlow): FinanceTone = when (flow) {",
    new_budget,
)

# Instrumented lifecycle contracts for the editor.
test_path = "app/src/androidTest/java/app/myfinhub/android/feature/plan/S7PlanSurfaceTest.kt"
text = read(test_path)
text = text.replace(
    "import androidx.compose.ui.test.assertIsDisplayed\n",
    "import androidx.compose.ui.test.assertIsDisplayed\nimport androidx.compose.ui.test.assertIsEnabled\nimport androidx.compose.ui.test.assertIsNotEnabled\n",
    1,
)
text = text.replace(
    "import androidx.compose.ui.test.performScrollTo\n",
    "import androidx.compose.ui.test.performScrollTo\nimport androidx.compose.ui.test.performTextReplacement\n",
    1,
)
old_test = '''    @Test
    fun budget_usesProgressWarning_withoutPushNotificationPromise() {
        composeRule.setContent { MyFinHubTheme { CanonicalBudget2026Screen(state, {}, {}) } }
        composeRule.onNodeWithTag("s7_budget_threshold_warning").assertIsDisplayed()
        composeRule.onNodeWithText("Θα ειδοποιείσαι", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Όριο προειδοποίησης").performScrollTo().assertIsDisplayed()
    }
}'''
new_test = '''    @Test
    fun budget_usesProgressWarning_withoutPushNotificationPromise() {
        composeRule.setContent { MyFinHubTheme { CanonicalBudget2026Screen(state, { _, _ -> }, {}) } }
        composeRule.onNodeWithTag("s7_budget_threshold_warning").assertIsDisplayed()
        composeRule.onNodeWithText("Θα ειδοποιείσαι", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Όριο προειδοποίησης").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun budget_saveIsChangeDriven_andBlocksDuplicateSubmitWhileInFlight() {
        var saveCalls = 0
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalBudget2026Screen(
                    state = state,
                    onSaveBudget = { _, _ -> saveCalls += 1 },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("s7_budget_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("s7_budget_limit").performTextReplacement("850")
        composeRule.onNodeWithTag("s7_budget_save").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, saveCalls) }
        composeRule.onNodeWithTag("s7_budget_save").assertIsNotEnabled()
    }

    @Test
    fun budget_inFlightState_disablesFieldsAndShowsProgressCopy() {
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalBudget2026Screen(
                    state = state,
                    onSaveBudget = { _, _ -> },
                    onBack = {},
                    mutationInFlight = true,
                )
            }
        }
        composeRule.onNodeWithTag("s7_budget_save").assertIsNotEnabled()
        composeRule.onNodeWithText("Αποθήκευση…").assertIsDisplayed()
        composeRule.onNodeWithTag("s7_budget_limit").assertIsNotEnabled()
        composeRule.onNodeWithTag("s7_budget_threshold").assertIsNotEnabled()
    }
}'''
if text.count(old_test) != 1:
    raise SystemExit("S7PlanSurfaceTest: target test block mismatch")
write(test_path, text.replace(old_test, new_test, 1))

# Pure routing contract: contextual recording never backdates an actual transaction to the due date.
write(
    "app/src/test/java/app/myfinhub/android/app/S7PlanQuickEntryPrefillTest.kt",
    r'''package app.myfinhub.android.app

import app.myfinhub.android.feature.plan.PlannedFlow
import app.myfinhub.android.feature.plan.PlannedItem
import app.myfinhub.android.feature.plan.PlannedKind
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S7PlanQuickEntryPrefillTest {
    @Test
    fun overdueObligation_prefillsContextButKeepsActualTransactionDateAtToday() {
        val today = LocalDate.of(2026, 9, 14).toString()
        val actions = plannedItemQuickEntryPrefillActions(
            item = PlannedItem(
                id = "rent",
                title = "Ενοίκιο",
                dueLabel = "8 Σεπ",
                amount = 680.0,
                kind = PlannedKind.SCHEDULED,
                flow = PlannedFlow.OBLIGATION,
                category = "Στέγαση",
                dueDateIso = "2026-09-08",
            ),
            quickEntryState = QuickEntryUiState(dateText = today),
        )

        assertTrue(actions.contains(QuickEntryAction.SelectKind(QuickEntryKind.EXPENSE)))
        assertTrue(actions.contains(QuickEntryAction.AmountChanged("680.0")))
        assertFalse(actions.any { it is QuickEntryAction.DateChanged })
    }

    @Test
    fun transfer_hasNoFakeCompletionRecordingAction() {
        val actions = plannedItemQuickEntryPrefillActions(
            item = PlannedItem(
                id = "move",
                title = "Αποταμίευση",
                dueLabel = "18 Σεπ",
                amount = 200.0,
                kind = PlannedKind.SCHEDULED,
                flow = PlannedFlow.TRANSFER,
            ),
            quickEntryState = QuickEntryUiState(),
        )
        assertEquals(emptyList<QuickEntryAction>(), actions)
    }
}
''',
)

# Canonical projection must not preserve a stale local budget draft through a server projection.
proj_test = "app/src/test/java/app/myfinhub/android/app/S7CanonicalPlanProjectionContractTest.kt"
text = read(proj_test)
text = text.replace(
    "import app.myfinhub.android.feature.plan.PlanUiState\n",
    "import app.myfinhub.android.feature.plan.BudgetDraft\nimport app.myfinhub.android.feature.plan.PlanUiState\n",
    1,
)
insert = '''    @Test
    fun canonicalBudget_replacesStalePreviousDraft() {
        val document = CanonicalFinanceDocument(
            Json.parseToJsonElement(
                """
                {
                  "schemaVersion":3,
                  "seed":{"accounts":[],"snapshots":[],"transactions":[],"recurring":[],"loans":[],"lending":[]},
                  "state":{
                    "settings":{"excludedFromAvailable":[],"accountNames":{}},
                    "events":[],"scheduled":[],"cards":[],
                    "budgets":[{"id":"budget-sep","month":"2026-09","scope":"overall","amount":950.0,"alertThreshold":85}]
                  }
                }
                """.trimIndent(),
            ).jsonObject,
        )
        val state = projectCanonicalPlanState(
            document = document,
            today = LocalDate.of(2026, 9, 14),
            previous = PlanUiState(budget = BudgetDraft("700", "75")),
        )
        assertEquals("950", state.budget.monthlyLimitText)
        assertEquals("85", state.budget.alertThresholdText)
    }

'''
marker = "    private fun fixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(\n"
if text.count(marker) != 1:
    raise SystemExit("S7CanonicalPlanProjectionContractTest: fixture marker mismatch")
write(proj_test, text.replace(marker, insert + marker, 1))
