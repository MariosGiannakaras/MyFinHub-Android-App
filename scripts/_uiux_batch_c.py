from pathlib import Path

# 1) Preserve canonical subcategory in read projections.
p = Path('app/src/main/java/app/myfinhub/android/core/data/CanonicalFinanceDomain.kt')
t = p.read_text()
old = '''data class CanonicalLegacyTransaction(
    val id: String,
    val date: String,
    val type: String,
    val accountId: String?,
    val fromAccountId: String?,
    val toAccountId: String?,
    val amount: Double,
    val note: String,
    val category: String?,
)'''
new = '''data class CanonicalLegacyTransaction(
    val id: String,
    val date: String,
    val type: String,
    val accountId: String?,
    val fromAccountId: String?,
    val toAccountId: String?,
    val amount: Double,
    val note: String,
    val category: String?,
    val subcategory: String? = null,
)'''
assert t.count(old) == 1, 'legacy canonical projection guard failed'
t = t.replace(old, new)

old = '''data class CanonicalEvent(
    val id: String,
    val date: String,
    val kind: String,
    val amount: Double,
    val note: String,
    val category: String?,
    val accountId: String?,'''
new = '''data class CanonicalEvent(
    val id: String,
    val date: String,
    val kind: String,
    val amount: Double,
    val note: String,
    val category: String?,
    val subcategory: String? = null,
    val accountId: String?,'''
assert t.count(old) == 1, 'event canonical projection guard failed'
t = t.replace(old, new)

old = '''            category = event.string("category"),
            accountId = event.string("accountId"),'''
new = '''            category = event.string("category"),
            subcategory = event.string("subcategory"),
            accountId = event.string("accountId"),'''
assert t.count(old) == 1, 'event subcategory parse guard failed'
t = t.replace(old, new)

old = '''    note = string("note").orEmpty(),
    category = string("category"),
)'''
new = '''    note = string("note").orEmpty(),
    category = string("category"),
    subcategory = string("subcategory"),
)'''
assert t.count(old) == 1, 'legacy subcategory parse guard failed'
t = t.replace(old, new)
p.write_text(t)

# 2) Rich edit mutation: date/category/subcategory/note only. Ledger-bearing fields stay untouched.
p = Path('app/src/main/java/app/myfinhub/android/core/data/CanonicalFinanceMutations.kt')
t = p.read_text()
import_guard = 'package app.myfinhub.android.core.data\n\nimport kotlin.math.roundToLong'
assert t.count(import_guard) == 1, 'mutation import guard failed'
t = t.replace(import_guard, 'package app.myfinhub.android.core.data\n\nimport java.time.LocalDate\nimport kotlin.math.roundToLong')
start = t.index('data class EditCanonicalActivity(')
end = t.index('\n/** Removes a transaction', start)
replacement = '''data class EditCanonicalActivity(
    val transactionId: String,
    val note: String,
    val category: String,
    val nowIso: String,
    /** null keeps an older queued edit backward-compatible; non-null is an explicit date edit. */
    val date: String? = null,
    /** null preserves the existing value; an empty string explicitly clears subcategory. */
    val subcategory: String? = null,
) : CanonicalFinanceMutation {
    override val description: String = "Αποθήκευση αλλαγών κίνησης"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val normalizedNote = note.trim()
        require(normalizedNote.isNotBlank()) { "Η σημείωση δεν μπορεί να είναι κενή." }
        val normalizedCategory = category.trim()
        val categoryValue = normalizedCategory.takeIf(String::isNotBlank)?.let(::JsonPrimitive)
        val normalizedDate = date?.trim()?.also { raw ->
            require(runCatching { LocalDate.parse(raw) }.isSuccess) { "Η ημερομηνία δεν είναι έγκυρη." }
        }
        val subcategoryWasSpecified = subcategory != null
        val normalizedSubcategory = subcategory?.trim()
        val subcategoryValue = normalizedSubcategory?.takeIf(String::isNotBlank)?.let(::JsonPrimitive)

        fun commonUpdates(current: JsonObject): JsonObject {
            var updated = current
                .updated("note", JsonPrimitive(normalizedNote))
                .updated("category", categoryValue)
            if (normalizedDate != null) updated = updated.updated("date", JsonPrimitive(normalizedDate))
            if (subcategoryWasSpecified) updated = updated.updated("subcategory", subcategoryValue)
            return updated
        }

        val events = document.state.array("events")
        val eventIndex = events.indexOfFirst { (it as? JsonObject)?.string("id") == transactionId }
        if (eventIndex >= 0) {
            val current = events[eventIndex] as JsonObject
            val updatedEvent = commonUpdates(current).updated("updatedAt", JsonPrimitive(nowIso))
            val updatedEvents = events.toMutableList().apply { this[eventIndex] = updatedEvent }
            return document.withMutableState(document.state.updated("events", JsonArray(updatedEvents)), nowIso)
        }

        val custom = document.state.array("customTransactions")
        val customIndex = custom.indexOfFirst { (it as? JsonObject)?.string("id") == transactionId }
        if (customIndex >= 0) {
            val updatedTx = commonUpdates(custom[customIndex] as JsonObject)
            val updatedCustom = custom.toMutableList().apply { this[customIndex] = updatedTx }
            return document.withMutableState(document.state.updated("customTransactions", JsonArray(updatedCustom)), nowIso)
        }

        val seedTransaction = document.seed.array("transactions")
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("id") == transactionId }
            ?: error("Η κίνηση δεν είναι πλέον διαθέσιμη.")
        val overrides = document.state.obj("overrides")
        val currentOverride = overrides[transactionId] as? JsonObject ?: seedTransaction
        val updatedOverride = commonUpdates(currentOverride)
        return document.withMutableState(
            document.state.updated("overrides", overrides.updated(transactionId, updatedOverride)),
            nowIso,
        )
    }
}
'''.strip()
t = t[:start] + replacement + t[end:]
p.write_text(t)

# 3) Durable queue stays backward-compatible with old note/category-only edit intents.
p = Path('app/src/main/java/app/myfinhub/android/core/data/PendingCanonicalMutation.kt')
t = p.read_text()
old = '''        PendingMutationKind.EDIT_ACTIVITY -> EditCanonicalActivity(
            transactionId = payload.string("transactionId").orEmpty(),
            note = payload.string("note").orEmpty(),
            category = payload.string("category").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
        )'''
new = '''        PendingMutationKind.EDIT_ACTIVITY -> EditCanonicalActivity(
            transactionId = payload.string("transactionId").orEmpty(),
            note = payload.string("note").orEmpty(),
            category = payload.string("category").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
            date = payload.string("date"),
            subcategory = if ("subcategory" in payload) payload.string("subcategory").orEmpty() else null,
        )'''
assert t.count(old) == 1, 'pending edit deserialize guard failed'
t = t.replace(old, new)

old = '''            val expectedNote = payload.string("note").orEmpty().trim()
            val expectedCategory = payload.string("category").orEmpty().trim()
            val transaction = effectiveTransaction(document, id)
            id.isNotBlank() && (
                transaction == null ||
                    (transaction.string("note").orEmpty().trim() == expectedNote &&
                        transaction.string("category").orEmpty().trim() == expectedCategory)
            )'''
new = '''            val expectedNote = payload.string("note").orEmpty().trim()
            val expectedCategory = payload.string("category").orEmpty().trim()
            val expectedDate = payload.string("date")
            val subcategoryWasSpecified = "subcategory" in payload
            val expectedSubcategory = payload.string("subcategory").orEmpty().trim()
            val transaction = effectiveTransaction(document, id)
            id.isNotBlank() && (
                transaction == null ||
                    (transaction.string("note").orEmpty().trim() == expectedNote &&
                        transaction.string("category").orEmpty().trim() == expectedCategory &&
                        (expectedDate == null || transaction.string("date").orEmpty() == expectedDate) &&
                        (!subcategoryWasSpecified || transaction.string("subcategory").orEmpty().trim() == expectedSubcategory))
            )'''
assert t.count(old) == 1, 'pending edit reconciliation guard failed'
t = t.replace(old, new)

old = '''            is EditCanonicalActivity -> PendingCanonicalMutationIntent(
                intentId = intentId,
                kind = PendingMutationKind.EDIT_ACTIVITY,
                payload = JsonObject(
                    mapOf(
                        "transactionId" to JsonPrimitive(mutation.transactionId),
                        "note" to JsonPrimitive(mutation.note),
                        "category" to JsonPrimitive(mutation.category),
                        "nowIso" to JsonPrimitive(mutation.nowIso),
                    ),
                ),
                syncState = syncState,
            )'''
new = '''            is EditCanonicalActivity -> PendingCanonicalMutationIntent(
                intentId = intentId,
                kind = PendingMutationKind.EDIT_ACTIVITY,
                payload = JsonObject(buildMap {
                    put("transactionId", JsonPrimitive(mutation.transactionId))
                    put("note", JsonPrimitive(mutation.note))
                    put("category", JsonPrimitive(mutation.category))
                    put("nowIso", JsonPrimitive(mutation.nowIso))
                    mutation.date?.let { put("date", JsonPrimitive(it)) }
                    mutation.subcategory?.let { put("subcategory", JsonPrimitive(it)) }
                }),
                syncState = syncState,
            )'''
assert t.count(old) == 1, 'pending edit serialize guard failed'
t = t.replace(old, new)
p.write_text(t)

# 4) Activity UI state carries canonical category trees and richer edit intent.
p = Path('app/src/main/java/app/myfinhub/android/feature/activity/ActivityUiState.kt')
t = p.read_text()
marker = 'enum class ActivityKind(val label: String) {'
assert t.count(marker) == 1, 'activity category model insertion guard failed'
t = t.replace(marker, '''data class ActivityCategoryOption(
    val name: String,
    val subcategories: List<String> = emptyList(),
)

''' + marker)

old = '''data class ActivityUiState(
    val query: String = "",
    val filter: ActivityFilter = ActivityFilter.ALL,
    val selectedId: String? = null,
    val items: List<ActivityItem> = syntheticActivityItems(),
) {'''
new = '''data class ActivityUiState(
    val query: String = "",
    val filter: ActivityFilter = ActivityFilter.ALL,
    val selectedId: String? = null,
    val items: List<ActivityItem> = syntheticActivityItems(),
    val expenseCategories: List<ActivityCategoryOption> = emptyList(),
    val incomeCategories: List<ActivityCategoryOption> = emptyList(),
) {'''
assert t.count(old) == 1, 'activity state fields guard failed'
t = t.replace(old, new)

old = '''    val selectedItem: ActivityItem? = items.firstOrNull { it.id == selectedId }
}'''
new = '''    val selectedItem: ActivityItem? = items.firstOrNull { it.id == selectedId }

    fun categoryOptionsFor(item: ActivityItem): List<ActivityCategoryOption> =
        if (item.kind == ActivityKind.INCOME) incomeCategories else expenseCategories
}'''
assert t.count(old) == 1, 'activity category options method guard failed'
t = t.replace(old, new)

old = '    data class SaveEdit(val id: String, val note: String, val category: String) : ActivityAction'
new = '''    data class SaveEdit(
        val id: String,
        val date: String,
        val note: String,
        val category: String,
        val subcategory: String,
    ) : ActivityAction'''
assert t.count(old) == 1, 'activity action guard failed'
t = t.replace(old, new)

old = '''            if (item.id == action.id) {
                item.copy(subtitle = action.note, category = action.category.takeIf(String::isNotBlank))
            } else {'''
new = '''            if (item.id == action.id) {
                item.copy(
                    rawDate = action.date,
                    dateLabel = action.date,
                    subtitle = action.note,
                    category = action.category.takeIf(String::isNotBlank),
                    subcategory = action.subcategory.takeIf(String::isNotBlank),
                )
            } else {'''
assert t.count(old) == 1, 'activity reducer guard failed'
t = t.replace(old, new)
p.write_text(t)

# 5) Canonical projection supplies subcategories and category trees from the same server settings.
p = Path('app/src/main/java/app/myfinhub/android/app/CanonicalProductProjection.kt')
t = p.read_text()
old = 'import app.myfinhub.android.feature.activity.ActivityFilter\nimport app.myfinhub.android.feature.activity.ActivityItem'
new = 'import app.myfinhub.android.feature.activity.ActivityCategoryOption\nimport app.myfinhub.android.feature.activity.ActivityFilter\nimport app.myfinhub.android.feature.activity.ActivityItem'
assert t.count(old) == 1, 'projection activity import guard failed'
t = t.replace(old, new)

old = '''    val oldActivity = previous?.activityState
    val activity = ActivityUiState(
        query = oldActivity?.query.orEmpty(),
        filter = oldActivity?.filter ?: ActivityFilter.ALL,
        selectedId = oldActivity?.selectedId?.takeIf { id -> activityItems.any { it.id == id } },
        items = activityItems,
    )

    val quickEntry = projectQuickEntryState(
        document = document,
        today = today,
        previous = previous?.quickEntryState,
    )'''
new = '''    val quickEntry = projectQuickEntryState(
        document = document,
        today = today,
        previous = previous?.quickEntryState,
    )

    val oldActivity = previous?.activityState
    val activity = ActivityUiState(
        query = oldActivity?.query.orEmpty(),
        filter = oldActivity?.filter ?: ActivityFilter.ALL,
        selectedId = oldActivity?.selectedId?.takeIf { id -> activityItems.any { it.id == id } },
        items = activityItems,
        expenseCategories = quickEntry.expenseCategories.map { ActivityCategoryOption(it.name, it.subcategories) },
        incomeCategories = quickEntry.incomeCategories.map { ActivityCategoryOption(it.name, it.subcategories) },
    )'''
assert t.count(old) == 1, 'projection quick/activity order guard failed'
t = t.replace(old, new)

old = '''                    category = tx.category,
                    rawDate = tx.date,'''
new = '''                    category = tx.category,
                    subcategory = tx.subcategory,
                    rawDate = tx.date,'''
assert t.count(old) == 1, 'legacy activity subcategory projection guard failed'
t = t.replace(old, new)
old = '''                    category = event.category,
                    rawDate = event.date,'''
new = '''                    category = event.category,
                    subcategory = event.subcategory,
                    rawDate = event.date,'''
assert t.count(old) == 1, 'event activity subcategory projection guard failed'
t = t.replace(old, new)
p.write_text(t)

# 6) Controller submits the richer but ledger-safe mutation.
p = Path('app/src/main/java/app/myfinhub/android/app/FinanceProductViewModel.kt')
t = p.read_text()
old = '''                    EditCanonicalActivity(
                        transactionId = action.id,
                        note = action.note,
                        category = action.category,
                        nowIso = Instant.now().toString(),
                    ),'''
new = '''                    EditCanonicalActivity(
                        transactionId = action.id,
                        note = action.note,
                        category = action.category,
                        nowIso = Instant.now().toString(),
                        date = action.date,
                        subcategory = action.subcategory,
                    ),'''
assert t.count(old) == 1, 'controller edit guard failed'
t = t.replace(old, new)
p.write_text(t)

# 7) Shared finance row gains an accessible optional long-press accelerator.
p = Path('app/src/main/java/app/myfinhub/android/designsystem/MyFinHubComponents.kt')
t = p.read_text()
old = 'import androidx.compose.foundation.BorderStroke\n'
new = 'import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.combinedClickable\n'
assert t.count(old) == 1, 'finance row import guard failed'
t = t.replace(old, new)
old = '''    tone: FinanceTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),'''
new = '''    tone: FinanceTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),'''
assert t.count(old) == 1, 'finance row long click guard failed'
t = t.replace(old, new)
p.write_text(t)

# 8) Activity screen: long-press menu, DB-backed selectors and date picker.
p = Path('app/src/main/java/app/myfinhub/android/feature/activity/ActivityScreen.kt')
t = p.read_text()
for old, new, label in [
    ('import androidx.compose.foundation.layout.BoxWithConstraints\n', 'import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.BoxWithConstraints\n', 'box import'),
    ('import androidx.compose.material3.AlertDialog\n', 'import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.DropdownMenu\nimport androidx.compose.material3.DropdownMenuItem\n', 'dropdown import'),
    ('import app.myfinhub.android.designsystem.MyFinHubSectionCard\n', 'import app.myfinhub.android.designsystem.MyFinHubSectionCard\nimport app.myfinhub.android.designsystem.MyFinHubSelectorButton\n', 'selector import'),
    ('import app.myfinhub.android.designsystem.myFinHubCategoryIcon\n', 'import app.myfinhub.android.designsystem.myFinHubCategoryIcon\nimport app.myfinhub.android.feature.quickentry.DateEntryField\n', 'date field import'),
]:
    assert t.count(old) == 1, f'{label} guard failed'
    t = t.replace(old, new, 1)

old = '''                    ActivityDetailContent(
                        item = selected,
                        onSave = { note, category ->
                            onAction(ActivityAction.SaveEdit(selected.id, note, category))
                        },
                        onDelete = { onAction(ActivityAction.Delete(selected.id)) },
                        modifier = Modifier.weight(0.85f),
                    )'''
new = '''                    ActivityDetailContent(
                        item = selected,
                        categoryOptions = state.categoryOptionsFor(selected),
                        onSave = { date, note, category, subcategory ->
                            onAction(ActivityAction.SaveEdit(selected.id, date, note, category, subcategory))
                        },
                        onDelete = { onAction(ActivityAction.Delete(selected.id)) },
                        modifier = Modifier.weight(0.85f),
                    )'''
assert t.count(old) == 1, 'wide detail guard failed'
t = t.replace(old, new)

old = ''') {
    LazyColumn(
        modifier = modifier,'''
new = ''') {
    var contextItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier,'''
assert t.count(old) == 1, 'activity list state guard failed'
t = t.replace(old, new, 1)

old = '''        items(section.items, key = ActivityItem::id) { item ->
            MyFinHubFinanceRow(
                icon = myFinHubCategoryIcon(item.category, item.kind.icon()),
                iconDescription = item.category ?: item.kind.label,
                title = item.title,
                subtitle = item.subtitle,
                meta = if (item.pendingSync) "Εκκρεμεί επιβεβαίωση" else item.accountLabel,
                amountText = formatSignedEuro(item.amount),
                tone = if (item.pendingSync) FinanceTone.Neutral else item.kind.tone(),
                onClick = { onSelect(item.id) },
                modifier = if (item.pendingSync) Modifier.alpha(0.74f) else Modifier,
            )
        }'''
new = '''        items(section.items, key = ActivityItem::id) { item ->
            Box {
                MyFinHubFinanceRow(
                    icon = myFinHubCategoryIcon(item.category, item.kind.icon()),
                    iconDescription = item.category ?: item.kind.label,
                    title = item.title,
                    subtitle = item.subtitle,
                    meta = if (item.pendingSync) "Εκκρεμεί επιβεβαίωση" else item.accountLabel,
                    amountText = formatSignedEuro(item.amount),
                    tone = if (item.pendingSync) FinanceTone.Neutral else item.kind.tone(),
                    onClick = { onSelect(item.id) },
                    onLongClick = { contextItemId = item.id },
                    modifier = if (item.pendingSync) Modifier.alpha(0.74f) else Modifier,
                )
                DropdownMenu(
                    expanded = contextItemId == item.id,
                    onDismissRequest = { contextItemId = null },
                ) {
                    DropdownMenuItem(
                        text = { Text("Λεπτομέρειες / επεξεργασία") },
                        onClick = {
                            contextItemId = null
                            onSelect(item.id)
                        },
                    )
                    if (!item.pendingSync) {
                        DropdownMenuItem(
                            text = { Text("Διαγραφή") },
                            onClick = {
                                contextItemId = null
                                confirmDeleteId = item.id
                            },
                        )
                    }
                }
            }
        }'''
assert t.count(old) == 1, 'activity list row guard failed'
t = t.replace(old, new)

old = '''        }
    }
}


@Composable
private fun ActivityMonthHeader'''
new = '''        }
    }

    val deleteItem = state.items.firstOrNull { it.id == confirmDeleteId }
    if (deleteItem != null && !deleteItem.pendingSync) {
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("Διαγραφή κίνησης;") },
            text = { Text("Η κίνηση θα αφαιρεθεί από τα οικονομικά δεδομένα και θα ενημερωθούν τα σχετικά υπόλοιπα.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = deleteItem.id
                    confirmDeleteId = null
                    onAction(ActivityAction.Delete(id))
                }) { Text("Διαγραφή") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text("Πίσω") }
            },
        )
    }
}


@Composable
private fun ActivityMonthHeader'''
assert t.count(old) == 1, 'activity list dialog insertion guard failed'
t = t.replace(old, new)

old = '''fun ActivityDetailScreen(
    item: ActivityItem?,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
) {'''
new = '''fun ActivityDetailScreen(
    item: ActivityItem?,
    categoryOptions: List<ActivityCategoryOption>,
    onBack: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
    onDelete: () -> Unit,
) {'''
assert t.count(old) == 1, 'detail screen signature guard failed'
t = t.replace(old, new)
old = '''            ActivityDetailContent(
                item = item,
                onSave = onSave,
                onDelete = onDelete,'''
new = '''            ActivityDetailContent(
                item = item,
                categoryOptions = categoryOptions,
                onSave = onSave,
                onDelete = onDelete,'''
assert t.count(old) == 1, 'detail content call guard failed'
t = t.replace(old, new)

old = '''private fun ActivityDetailContent(
    item: ActivityItem,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    var note by rememberSaveable(item.id, item.subtitle) { mutableStateOf(item.subtitle) }
    var category by rememberSaveable(item.id, item.category) { mutableStateOf(item.category.orEmpty()) }
    var confirmDelete by rememberSaveable(item.id) { mutableStateOf(false) }'''
new = '''private fun ActivityDetailContent(
    item: ActivityItem,
    categoryOptions: List<ActivityCategoryOption>,
    onSave: (String, String, String, String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    var date by rememberSaveable(item.id, item.rawDate) { mutableStateOf(item.rawDate.take(10)) }
    var note by rememberSaveable(item.id, item.subtitle) { mutableStateOf(item.subtitle) }
    var category by rememberSaveable(item.id, item.category) { mutableStateOf(item.category.orEmpty()) }
    var subcategory by rememberSaveable(item.id, item.subcategory) { mutableStateOf(item.subcategory.orEmpty()) }
    var confirmDelete by rememberSaveable(item.id) { mutableStateOf(false) }
    val effectiveCategoryOptions = categoryOptions.ifEmpty {
        item.category?.takeIf(String::isNotBlank)?.let { listOf(ActivityCategoryOption(it)) }.orEmpty()
    }
    val subcategoryOptions = effectiveCategoryOptions.firstOrNull { it.name == category }?.subcategories.orEmpty()'''
assert t.count(old) == 1, 'detail content state guard failed'
t = t.replace(old, new)

old = '''        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        MyFinHubOutlinedField(
            value = note,'''
new = '''        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DateEntryField(
            value = date,
            onValueChange = { date = it },
            label = "Ημερομηνία",
            errorMessage = null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (effectiveCategoryOptions.isNotEmpty()) {
            ActivityChoiceField(
                label = "Κατηγορία",
                selectedId = category,
                choices = effectiveCategoryOptions.map { it.name to it.name },
                enabled = !item.pendingSync,
                onSelected = { selected ->
                    category = selected
                    val allowed = effectiveCategoryOptions.firstOrNull { it.name == selected }?.subcategories.orEmpty()
                    if (subcategory !in allowed) subcategory = ""
                },
            )
            if (subcategoryOptions.isNotEmpty()) {
                ActivityChoiceField(
                    label = "Υποκατηγορία",
                    selectedId = subcategory,
                    choices = listOf("" to "Χωρίς υποκατηγορία") + subcategoryOptions.map { it to it },
                    enabled = !item.pendingSync,
                    onSelected = { subcategory = it },
                )
            }
        }
        MyFinHubOutlinedField(
            value = note,'''
assert t.count(old) == 1, 'detail edit fields insertion guard failed'
t = t.replace(old, new)

old = '''        MyFinHubOutlinedField(
            value = category,
            onValueChange = { category = it },
            label = "Κατηγορία",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            enabled = !item.pendingSync,
        )
'''
assert t.count(old) == 1, 'old category field guard failed'
t = t.replace(old, '')

old = '''            onClick = { onSave(note, category) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !item.pendingSync && note.isNotBlank() && (note != item.subtitle || category != item.category.orEmpty()),'''
new = '''            onClick = { onSave(date, note, category, subcategory) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !item.pendingSync && note.isNotBlank() && date.isNotBlank() && (
                date != item.rawDate.take(10) ||
                    note != item.subtitle ||
                    category != item.category.orEmpty() ||
                    subcategory != item.subcategory.orEmpty()
            ),'''
assert t.count(old) == 1, 'save action guard failed'
t = t.replace(old, new)

marker = '\nprivate fun ActivityFilter.icon(): ImageVector = when (this) {'
assert t.count(marker) == 1, 'choice helper marker guard failed'
helper = '''

@Composable
private fun ActivityChoiceField(
    label: String,
    selectedId: String,
    choices: List<Pair<String, String>>,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    var expanded by rememberSaveable(label, selectedId) { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.first == selectedId }?.second
        ?: choices.firstOrNull()?.second
        ?: "Δεν υπάρχει διαθέσιμη επιλογή"
    Box(modifier = Modifier.fillMaxWidth()) {
        MyFinHubSelectorButton(
            label = label,
            onClick = { if (enabled && choices.isNotEmpty()) expanded = true },
            enabled = enabled && choices.isNotEmpty(),
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
'''
t = t.replace(marker, helper + marker)
p.write_text(t)

# 9) App navigation supplies the DB category tree and richer edit callback.
p = Path('app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt')
t = p.read_text()
old = '''                entry<AppRoute.ActivityDetail> { route ->
                    ActivityDetailScreen(
                        item = activityState.items.firstOrNull { it.id == route.eventId },
                        onBack = { activityBackStack.removeLastOrNull() },
                        onSave = { note, category ->
                            onActivityAction(ActivityAction.SaveEdit(route.eventId, note, category))
                        },'''
new = '''                entry<AppRoute.ActivityDetail> { route ->
                    val item = activityState.items.firstOrNull { it.id == route.eventId }
                    ActivityDetailScreen(
                        item = item,
                        categoryOptions = item?.let(activityState::categoryOptionsFor).orEmpty(),
                        onBack = { activityBackStack.removeLastOrNull() },
                        onSave = { date, note, category, subcategory ->
                            onActivityAction(ActivityAction.SaveEdit(route.eventId, date, note, category, subcategory))
                        },'''
assert t.count(old) == 1, 'app activity detail route guard failed'
t = t.replace(old, new)
p.write_text(t)

# 10) Focused regression tests, including pre-change queued intent compatibility.
test = Path('app/src/test/java/app/myfinhub/android/core/data/ActivityEditMutationTest.kt')
assert not test.exists(), 'activity edit test already exists unexpectedly'
test.write_text('''package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ActivityEditMutationTest {
    private fun document(): CanonicalFinanceDocument = CanonicalFinanceDocument(
        Json.parseToJsonElement(
            """{
              "updatedAt":"2026-09-01T00:00:00Z",
              "seed":{"accounts":[],"transactions":[]},
              "state":{"events":[{
                "id":"evt-1","date":"2026-09-01","kind":"expense","amount":12.5,
                "note":"Old note","category":"Food","subcategory":"Groceries",
                "legs":[{"accountId":"cash","amount":-12.5}],
                "createdAt":"2026-09-01T10:00:00Z","updatedAt":"2026-09-01T10:00:00Z"
              }]}
            }""",
        ).jsonObject,
    )

    @Test
    fun editActivity_updatesOnlyLedgerSafeEditableFields() {
        val edited = EditCanonicalActivity(
            transactionId = "evt-1",
            note = "Dinner",
            category = "Food",
            nowIso = "2026-09-02T20:00:00Z",
            date = "2026-09-02",
            subcategory = "Dining",
        ).apply(document())

        val event = edited.state.array("events").first() as JsonObject
        assertEquals("2026-09-02", event.string("date"))
        assertEquals("Dinner", event.string("note"))
        assertEquals("Food", event.string("category"))
        assertEquals("Dining", event.string("subcategory"))
        assertEquals(-12.5, (event.array("legs").first() as JsonObject).number("amount")!!, 0.001)
        assertEquals("2026-09-01T10:00:00Z", event.string("createdAt"))
        assertEquals("2026-09-02T20:00:00Z", event.string("updatedAt"))
    }

    @Test
    fun editActivity_emptySubcategoryExplicitlyClearsIt() {
        val edited = EditCanonicalActivity(
            transactionId = "evt-1",
            note = "Old note",
            category = "Food",
            nowIso = "2026-09-02T20:00:00Z",
            subcategory = "",
        ).apply(document())
        val event = edited.state.array("events").first() as JsonObject
        assertFalse("subcategory" in event)
        assertEquals("2026-09-01", event.string("date"))
    }

    @Test
    fun legacyQueuedEdit_withoutDateOrSubcategory_preservesBoth() {
        val intent = PendingCanonicalMutationIntent(
            intentId = "intent-old",
            kind = PendingMutationKind.EDIT_ACTIVITY,
            payload = JsonObject(
                mapOf(
                    "transactionId" to JsonPrimitive("evt-1"),
                    "note" to JsonPrimitive("Queued old edit"),
                    "category" to JsonPrimitive("Food"),
                    "nowIso" to JsonPrimitive("2026-09-02T20:00:00Z"),
                ),
            ),
        )
        val edited = intent.asMutation().apply(document())
        val event = edited.state.array("events").first() as JsonObject
        assertEquals("2026-09-01", event.string("date"))
        assertEquals("Groceries", event.string("subcategory"))
        assertEquals("Queued old edit", event.string("note"))
    }
}
''')
