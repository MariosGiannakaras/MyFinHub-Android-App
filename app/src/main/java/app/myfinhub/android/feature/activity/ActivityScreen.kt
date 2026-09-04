package app.myfinhub.android.feature.activity

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubDestructiveTextAction
import app.myfinhub.android.designsystem.MyFinHubFilterChip
import app.myfinhub.android.designsystem.MyFinHubFinanceRow
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSearchField
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.myFinHubCategoryIcon
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onAction: (ActivityAction) -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenQuickEntry: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Κινήσεις",
                subtitle = "Η οικονομική σου δραστηριότητα",
            )
        },
        floatingActionButton = {
            MyFinHubPrimaryAction(
                label = "Νέα κίνηση",
                onClick = onOpenQuickEntry,
                modifier = Modifier.semantics {
                    contentDescription = "Δημιουργία νέας κίνησης"
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            if (maxWidth >= 840.dp) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    ActivityList(
                        state = state,
                        onAction = onAction,
                        onSelect = { id -> onAction(ActivityAction.Select(id)) },
                        modifier = Modifier.weight(1.15f),
                    )
                    val selected = state.selectedItem ?: state.visibleItems.firstOrNull()
                    if (selected != null) {
                        ActivityDetailContent(
                            item = selected,
                            onSave = { note, category ->
                                onAction(ActivityAction.SaveEdit(selected.id, note, category))
                            },
                            onDelete = { onAction(ActivityAction.Delete(selected.id)) },
                            modifier = Modifier.weight(0.85f),
                        )
                    }
                }
            } else {
                ActivityList(
                    state = state,
                    onAction = onAction,
                    onSelect = onOpenDetail,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ActivityList(
    state: ActivityUiState,
    onAction: (ActivityAction) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = MyFinHubDesignMetrics.screenHorizontalPadding,
            top = MyFinHubSpacing.xs,
            end = MyFinHubDesignMetrics.screenHorizontalPadding,
            bottom = MyFinHubDesignMetrics.navigationContentBottomClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
    ) {
        item {
            MyFinHubSearchField(
                value = state.query,
                onValueChange = { onAction(ActivityAction.QueryChanged(it)) },
                placeholder = "Αναζήτηση κινήσεων",
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                ActivityFilter.entries.forEach { filter ->
                    MyFinHubFilterChip(
                        selected = state.filter == filter,
                        onClick = { onAction(ActivityAction.FilterChanged(filter)) },
                        label = filter.label,
                        icon = filter.icon(),
                        tone = filter.tone(),
                    )
                }
            }
        }
        if (state.visibleItems.isEmpty()) {
            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Δεν βρέθηκαν κινήσεις με αυτά τα φίλτρα.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.visibleItems, key = ActivityItem::id) { item ->
                MyFinHubFinanceRow(
                    icon = myFinHubCategoryIcon(item.category, item.kind.icon()),
                    iconDescription = item.category ?: item.kind.label,
                    title = item.title,
                    subtitle = item.subtitle,
                    meta = if (item.pendingSync) {
                        "Αναμονή συγχρονισμού"
                    } else {
                        "${item.dateLabel} · ${item.accountLabel}"
                    },
                    amountText = formatSignedEuro(item.amount),
                    tone = if (item.pendingSync) FinanceTone.Neutral else item.kind.tone(),
                    onClick = { onSelect(item.id) },
                    modifier = if (item.pendingSync) Modifier.alpha(0.74f) else Modifier,
                )
            }
        }
    }
}

@Composable
fun ActivityDetailScreen(
    item: ActivityItem?,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Λεπτομέρειες κίνησης",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        if (item == null) {
            MyFinHubSectionCard(
                modifier = Modifier.padding(padding).padding(MyFinHubSpacing.lg).fillMaxWidth(),
            ) {
                Text("Η κίνηση δεν είναι πλέον διαθέσιμη.")
            }
        } else {
            ActivityDetailContent(
                item = item,
                onSave = onSave,
                onDelete = onDelete,
                modifier = Modifier.padding(padding).padding(MyFinHubSpacing.lg),
            )
        }
    }
}

@Composable
private fun ActivityDetailContent(
    item: ActivityItem,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    var note by rememberSaveable(item.id, item.subtitle) { mutableStateOf(item.subtitle) }
    var category by rememberSaveable(item.id, item.category) { mutableStateOf(item.category.orEmpty()) }
    var confirmDelete by rememberSaveable(item.id) { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
    ) {
        MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            ) {
                MyFinHubIconBadge(
                    icon = myFinHubCategoryIcon(item.category, item.kind.icon()),
                    tone = if (item.pendingSync) FinanceTone.Neutral else item.kind.tone(),
                    contentDescription = item.category ?: item.kind.label,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    MyFinHubAmountText(
                        text = formatSignedEuro(item.amount),
                        tone = if (item.pendingSync) FinanceTone.Neutral else item.kind.tone(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "${item.kind.label} · ${item.dateLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.pendingSync) {
                        Text(
                            text = "Αναμονή συγχρονισμού",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        MyFinHubOutlinedField(
            value = note,
            onValueChange = { note = it },
            label = "Σημείωση",
            singleLine = false,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            enabled = !item.pendingSync,
        )
        MyFinHubOutlinedField(
            value = category,
            onValueChange = { category = it },
            label = "Κατηγορία",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            enabled = !item.pendingSync,
        )
        MyFinHubPrimaryAction(
            label = "Αποθήκευση αλλαγών",
            onClick = { onSave(note, category) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !item.pendingSync && note.isNotBlank() && (note != item.subtitle || category != item.category.orEmpty()),
            icon = null,
        )
        MyFinHubDestructiveTextAction(
            label = if (item.pendingSync) "Ακύρωση τοπικής κίνησης" else "Διαγραφή κίνησης",
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth(),
        )
        if (item.kind == ActivityKind.TRANSFER) {
            Text(
                "Η εσωτερική μεταφορά δεν μετρά ως έσοδο ή έξοδο.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(if (item.pendingSync) "Ακύρωση τοπικής κίνησης;" else "Διαγραφή κίνησης;")
            },
            text = {
                Text(
                    if (item.pendingSync) {
                        "Η κίνηση δεν έχει συγχρονιστεί ακόμη και θα αφαιρεθεί μόνο από αυτή τη συσκευή."
                    } else {
                        "Η κίνηση θα αφαιρεθεί από τα οικονομικά δεδομένα και θα ενημερωθούν τα σχετικά υπόλοιπα."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text(if (item.pendingSync) "Ακύρωση κίνησης" else "Διαγραφή")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Πίσω")
                }
            },
        )
    }
}

private fun ActivityFilter.icon(): ImageVector = when (this) {
    ActivityFilter.ALL -> MyFinHubIcons.All
    ActivityFilter.EXPENSE -> MyFinHubIcons.Expense
    ActivityFilter.INCOME -> MyFinHubIcons.Income
    ActivityFilter.TRANSFER -> MyFinHubIcons.Transfer
}

private fun ActivityFilter.tone(): FinanceTone = when (this) {
    ActivityFilter.ALL -> FinanceTone.Neutral
    ActivityFilter.EXPENSE -> FinanceTone.Expense
    ActivityFilter.INCOME -> FinanceTone.Income
    ActivityFilter.TRANSFER -> FinanceTone.Transfer
}

private fun ActivityKind.icon(): ImageVector = when (this) {
    ActivityKind.EXPENSE -> MyFinHubIcons.Expense
    ActivityKind.INCOME -> MyFinHubIcons.Income
    ActivityKind.TRANSFER -> MyFinHubIcons.Transfer
    ActivityKind.CARD_PAYMENT -> MyFinHubIcons.Card
}

private fun ActivityKind.tone(): FinanceTone = when (this) {
    ActivityKind.EXPENSE -> FinanceTone.Expense
    ActivityKind.INCOME -> FinanceTone.Income
    ActivityKind.TRANSFER -> FinanceTone.Transfer
    ActivityKind.CARD_PAYMENT -> FinanceTone.Transfer
}

private fun formatSignedEuro(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(amount)
