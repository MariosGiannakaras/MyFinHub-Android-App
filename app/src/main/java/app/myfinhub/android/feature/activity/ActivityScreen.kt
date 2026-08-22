package app.myfinhub.android.feature.activity

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onAction: (ActivityAction) -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenQuickEntry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Κινήσεις", modifier = Modifier.semantics { heading() }) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenQuickEntry,
                text = { Text("Νέα κίνηση") },
                icon = { Text("+") },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (maxWidth >= 840.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
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
                            onUpdateNote = { onAction(ActivityAction.UpdateNote(selected.id, it)) },
                            onUpdateCategory = { onAction(ActivityAction.UpdateCategory(selected.id, it)) },
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
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onAction(ActivityAction.QueryChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Αναζήτηση κινήσεων") },
                singleLine = true,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActivityFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { onAction(ActivityAction.FilterChanged(filter)) },
                        label = { Text(filter.label) },
                    )
                }
            }
        }
        if (state.visibleItems.isEmpty()) {
            item {
                Text(
                    text = "Δεν βρέθηκαν κινήσεις με αυτά τα φίλτρα.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }
        } else {
            items(state.visibleItems, key = ActivityItem::id) { item ->
                ActivityRow(item = item, onClick = { onSelect(item.id) })
            }
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${item.dateLabel} · ${item.accountLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatSignedEuro(item.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (item.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    item: ActivityItem?,
    onBack: () -> Unit,
    onUpdateNote: (String) -> Unit,
    onUpdateCategory: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Λεπτομέρειες κίνησης") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Πίσω") } },
            )
        },
    ) { padding ->
        if (item == null) {
            Text("Η κίνηση δεν είναι πλέον διαθέσιμη.", modifier = Modifier.padding(padding).padding(20.dp))
        } else {
            ActivityDetailContent(
                item = item,
                onUpdateNote = onUpdateNote,
                onUpdateCategory = onUpdateCategory,
                modifier = Modifier.padding(padding).padding(20.dp),
            )
        }
    }
}

@Composable
private fun ActivityDetailContent(
    item: ActivityItem,
    onUpdateNote: (String) -> Unit,
    onUpdateCategory: (String) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(formatSignedEuro(item.amount), style = MaterialTheme.typography.headlineMedium)
        Text("${item.kind.label} · ${item.dateLabel}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        OutlinedTextField(
            value = item.subtitle,
            onValueChange = onUpdateNote,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Σημείωση") },
        )
        OutlinedTextField(
            value = item.category.orEmpty(),
            onValueChange = onUpdateCategory,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Κατηγορία") },
            singleLine = true,
        )
        Text(
            text = if (item.kind == ActivityKind.TRANSFER) {
                "Η εσωτερική μεταφορά δεν μετρά ως έσοδο ή έξοδο."
            } else {
                "Οι αλλαγές αυτού του prototype είναι τοπικές και synthetic μέχρι να ενεργοποιηθεί το canonical API."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatSignedEuro(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR"))
    return formatter.format(amount)
}
