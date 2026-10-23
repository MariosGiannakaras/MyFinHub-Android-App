package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.core.data.CanonicalCategoryTaxonomyRow
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing

/** Read-only taxonomy identities; reorder only touches the canonical display sequence. */
@Composable
fun CanonicalCategoryTaxonomyOrderPanel(
    expense: List<CanonicalCategoryTaxonomyRow>,
    income: List<CanonicalCategoryTaxonomyRow>,
    mutationBlocked: Boolean,
    onReorder: (kind: String, category: String, subcategory: String?, direction: Int) -> Unit,
    onAdd: ((kind: String, label: String, parentCategory: String?) -> Unit)? = null,
    onRename: ((kind: String, category: String, subcategory: String?, newLabel: String) -> Unit)? = null,
    onMove: ((kind: String, source: String, child: String, target: String) -> Unit)? = null,
    onRetire: ((kind: String, category: String, subcategory: String?) -> Unit)? = null,
    onSetColor: ((kind: String, category: String, subcategory: String?, color: String?) -> Unit)? = null,
    categoryColors: Map<String, String> = emptyMap(),
    subcategoryColors: Map<String, String> = emptyMap(),
) {
    var newKind by remember { mutableStateOf<String?>(null) }
    var newParent by remember { mutableStateOf<String?>(null) }
    var newLabel by remember { mutableStateOf("") }
    var renameKind by remember { mutableStateOf<String?>(null) }
    var renameCategory by remember { mutableStateOf("") }
    var renameSubcategory by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var moveKind by remember { mutableStateOf<String?>(null) }
    var moveSource by remember { mutableStateOf("") }
    var moveChild by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<String?>(null) }
    var retireKind by remember { mutableStateOf<String?>(null) }
    var retireCategory by remember { mutableStateOf("") }
    var retireSubcategory by remember { mutableStateOf<String?>(null) }
    var colorKind by remember { mutableStateOf<String?>(null) }
    var colorCategory by remember { mutableStateOf("") }
    var colorSubcategory by remember { mutableStateOf<String?>(null) }
    var colorDraft by remember { mutableStateOf("") }
    fun openColor(kind: String, category: String, child: String?) {
        colorKind = kind
        colorCategory = category
        colorSubcategory = child
        val key = kind + ":" + category + if (child == null) "" else ":" + child
        colorDraft = (if (child == null) categoryColors else subcategoryColors)[key].orEmpty()
    }
    fun openRetirement(kind: String, category: String, child: String?) {
        retireKind = kind
        retireCategory = category
        retireSubcategory = child
    }
    fun openRename(kind: String, category: String, child: String?) {
        renameKind = kind
        renameCategory = category
        renameSubcategory = child
        renameDraft = child ?: category
    }

    fun openAdd(kind: String, parent: String?) {
        newKind = kind
        newParent = parent
        newLabel = ""
    }

    MyFinHubSectionCard(
        modifier = Modifier.fillMaxWidth().testTag("s9_category_order"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Σειρά κατηγοριών", style = MaterialTheme.typography.titleLarge)
            Text(
                "Αλλάζει μόνο η σειρά εμφάνισης. Οι ταυτότητες, οι κινήσεις, " +
                    "οι κανόνες ταξινόμησης και τα ιστορικά ποσά διατηρούνται.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expense.isEmpty() && income.isEmpty()) {
                Text(
                    "Δεν υπάρχει ακόμη ρητά αποθηκευμένο δέντρο κατηγοριών. " +
                        "Η διαχείριση είναι διαθέσιμη όταν φορτωθεί το αντίστοιχο δέντρο.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            listOf("expense" to expense, "income" to income).forEach { (kind, rows) ->
                if (rows.isNotEmpty() || onAdd != null) {
                    Text(
                        if (kind == "expense") "Έξοδα" else "Έσοδα",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (onAdd != null) {
                        TextButton(
                            enabled = !mutationBlocked,
                            onClick = { openAdd(kind, null) },
                            modifier = Modifier.fillMaxWidth()
                                .testTag("s9_category_add_" + kind),
                        ) { Text("Προσθήκη κατηγορίας") }
                    }
                    var visible by rememberSaveable(kind) { mutableIntStateOf(12) }
                    rows.take(visible).forEachIndexed { index, row ->
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                            Text(row.name, style = MaterialTheme.typography.bodyLarge)
                            if (onSetColor != null) {
                                val color = categoryColors[kind + ":" + row.name]
                                TextButton(
                                    enabled = !mutationBlocked,
                                    onClick = { openColor(kind, row.name, null) },
                                    modifier = Modifier.testTag("s9_taxonomy_color_" + kind + "_" + index),
                                ) {
                                    Text("Χρώμα εικονιδίου" +
                                        if (color.isNullOrBlank()) "" else " · " + color)
                                }
                            }
                            if (onRetire != null && row.subcategories.isEmpty()) {
                                TextButton(
                                    enabled = !mutationBlocked,
                                    onClick = { openRetirement(kind, row.name, null) },
                                    modifier = Modifier.testTag("s9_taxonomy_retire_" + kind + "_" + index),
                                ) { Text("Απόσυρση κατηγορίας") }
                            }
                            if (onRename != null) {
                                TextButton(
                                    enabled = !mutationBlocked,
                                    onClick = { openRename(kind, row.name, null) },
                                    modifier = Modifier.testTag("s9_taxonomy_rename_" + kind + "_" + index),
                                ) { Text("Μετονομασία κατηγορίας") }
                            }
                            if (onAdd != null) {
                                TextButton(
                                    onClick = { openAdd(kind, row.name) },
                                    enabled = !mutationBlocked,
                                    modifier = Modifier.testTag("s9_subcategory_add_" + kind + "_" + index),
                                ) { Text("Νέα υποκατηγορία") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                                TextButton(
                                    enabled = !mutationBlocked && index > 0,
                                    onClick = { onReorder(kind, row.name, null, -1) },
                                    modifier = Modifier.testTag("s9_category_up_${kind}_$index"),
                                ) { Text("Πάνω") }
                                TextButton(
                                    enabled = !mutationBlocked && index < rows.lastIndex,
                                    onClick = { onReorder(kind, row.name, null, 1) },
                                    modifier = Modifier.testTag("s9_category_down_${kind}_$index"),
                                ) { Text("Κάτω") }
                            }
                            row.subcategories.forEachIndexed { subIndex, child ->
                                Column {
                                    Text("↳ $child", style = MaterialTheme.typography.bodySmall)
                                    if (onSetColor != null) {
                                        val color = subcategoryColors[kind + ":" + row.name + ":" + child]
                                        TextButton(
                                            enabled = !mutationBlocked,
                                            onClick = { openColor(kind, row.name, child) },
                                            modifier = Modifier.testTag(
                                                "s9_taxonomy_color_sub_" + kind + "_" + index + "_" + subIndex,
                                            ),
                                        ) {
                                            Text("Χρώμα εικονιδίου" +
                                                if (color.isNullOrBlank()) "" else " · " + color)
                                        }
                                    }
                                    if (onRetire != null) {
                                        TextButton(
                                            enabled = !mutationBlocked,
                                            onClick = { openRetirement(kind, row.name, child) },
                                            modifier = Modifier.testTag(
                                                "s9_taxonomy_retire_sub_" + kind + "_" + index + "_" + subIndex,
                                            ),
                                        ) { Text("Απόσυρση υποκατηγορίας") }
                                    }
                                    if (onMove != null && rows.size > 1) {
                                        TextButton(
                                            enabled = !mutationBlocked,
                                            onClick = {
                                                moveKind = kind
                                                moveSource = row.name
                                                moveChild = child
                                                moveTarget = null
                                            },
                                            modifier = Modifier.testTag(
                                                "s9_taxonomy_move_sub_" + kind + "_" + index + "_" + subIndex,
                                            ),
                                        ) { Text("Μεταφορά σε άλλη κατηγορία") }
                                    }
                                    if (onRename != null) {
                                        TextButton(
                                            enabled = !mutationBlocked,
                                            onClick = { openRename(kind, row.name, child) },
                                            modifier = Modifier.testTag(
                                                "s9_taxonomy_rename_sub_" + kind + "_" + index + "_" + subIndex,
                                            ),
                                        ) { Text("Μετονομασία υποκατηγορίας") }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(
                                            MyFinHubSpacing.xs,
                                        ),
                                    ) {
                                        TextButton(
                                            enabled = !mutationBlocked && subIndex > 0,
                                            onClick = {
                                                onReorder(kind, row.name, child, -1)
                                            },
                                            modifier = Modifier.testTag(
                                                "s9_subcategory_up_${kind}_${index}_$subIndex",
                                            ),
                                        ) { Text("Πάνω") }
                                        TextButton(
                                            enabled = !mutationBlocked &&
                                                subIndex < row.subcategories.lastIndex,
                                            onClick = { onReorder(kind, row.name, child, 1) },
                                            modifier = Modifier.testTag(
                                                "s9_subcategory_down_${kind}_${index}_$subIndex",
                                            ),
                                        ) { Text("Κάτω") }
                                    }
                                }
                            }
                        }
                    }
                    if (rows.size > visible) {
                        TextButton(
                            onClick = { visible = (visible + 12).coerceAtMost(rows.size) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Περισσότερες κατηγορίες (${rows.size - visible})") }
                    }
                }
            }
        }
    }
    if (colorKind != null && onSetColor != null) {
        val kind = colorKind!!
        val category = colorCategory
        val subcategory = colorSubcategory
        val valid = colorDraft.isBlank() ||
            Regex("^#[0-9a-fA-F]{6}$").matches(colorDraft.trim())
        AlertDialog(
            onDismissRequest = { if (!mutationBlocked) colorKind = null },
            title = { Text("Χρώμα εικονιδίου") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text(
                        "Ορίζει μόνο το χρώμα προτίμησης του Web. " +
                            "Δεν τροποποιεί το λογιστικό ιστορικό ή την ταυτότητα.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = colorDraft,
                        onValueChange = { colorDraft = it.take(7) },
                        label = { Text("Χρώμα #RRGGBB") },
                        singleLine = true,
                        enabled = !mutationBlocked,
                        modifier = Modifier.fillMaxWidth().testTag("s9_taxonomy_color_hex"),
                    )
                    if (!valid) {
                        Text(
                            "Χρησιμοποίησε έξι δεκαεξαδικά ψηφία μετά το #.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !mutationBlocked && valid,
                    onClick = {
                        onSetColor(kind, category, subcategory, colorDraft.trim().takeIf { it.isNotBlank() })
                        colorKind = null
                    },
                    modifier = Modifier.testTag("s9_taxonomy_color_save"),
                ) { Text("Αποθήκευση") }
            },
            dismissButton = {
                TextButton(
                    enabled = !mutationBlocked,
                    onClick = { colorKind = null },
                ) { Text("Ακύρωση") }
            },
        )
    }
    if (retireKind != null && onRetire != null) {
        val kind = retireKind!!
        val category = retireCategory
        val child = retireSubcategory
        AlertDialog(
            onDismissRequest = { if (!mutationBlocked) retireKind = null },
            title = { Text(if (child == null) "Απόσυρση κατηγορίας;" else "Απόσυρση υποκατηγορίας;") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text(
                        "Η «" + (child ?: category) + "» αφαιρείται μόνο από τις ενεργές επιλογές. " +
                            "Οι ιστορικές συναλλαγές, τα IDs και τα aliases δεν διαγράφονται.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Η ενέργεια απορρίπτεται αυτόματα αν υπάρχουν ενεργοί κανόνες, " +
                            "πάγια, προγραμματισμένες κινήσεις ή budgets που εξαρτώνται από αυτή.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !mutationBlocked,
                    onClick = {
                        onRetire(kind, category, child)
                        retireKind = null
                    },
                    modifier = Modifier.testTag("s9_taxonomy_retire_confirm"),
                ) { Text("Απόσυρση") }
            },
            dismissButton = {
                TextButton(
                    enabled = !mutationBlocked,
                    onClick = { retireKind = null },
                ) { Text("Ακύρωση") }
            },
        )
    }
    if (moveKind != null && onMove != null) {
        val kind = moveKind!!
        val candidates = (if (kind == "expense") expense else income)
            .filter { it.name != moveSource }
        AlertDialog(
            onDismissRequest = { if (!mutationBlocked) moveKind = null },
            title = { Text("Μεταφορά υποκατηγορίας") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text(
                        "Η «" + moveChild + "» θα διατηρήσει το ID και την αρχική " +
                            "κατηγορία ως ιστορικό alias. Οι προηγούμενες κινήσεις δεν αλλάζουν.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Επίλεξε κατηγορία προορισμού",
                        style = MaterialTheme.typography.titleSmall)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    ) {
                        items(candidates, key = { it.name }) { candidate ->
                            TextButton(
                                enabled = !mutationBlocked,
                                onClick = { moveTarget = candidate.name },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    (if (moveTarget == candidate.name) "✓ " else "") +
                                        candidate.name
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !mutationBlocked && moveTarget != null,
                    onClick = {
                        val target = moveTarget
                        if (target != null) onMove(kind, moveSource, moveChild, target)
                        moveKind = null
                    },
                    modifier = Modifier.testTag("s9_taxonomy_move_confirm"),
                ) { Text("Μεταφορά") }
            },
            dismissButton = {
                TextButton(
                    enabled = !mutationBlocked,
                    onClick = { moveKind = null },
                ) { Text("Ακύρωση") }
            },
        )
    }
    if (renameKind != null && onRename != null) {
        val kind = renameKind!!
        val category = renameCategory
        val subcategory = renameSubcategory
        val valid = renameDraft.trim().length in 1..120 &&
            renameDraft.trim().any(Char::isLetterOrDigit) &&
            renameDraft.trim() != (subcategory ?: category)
        AlertDialog(
            onDismissRequest = { if (!mutationBlocked) renameKind = null },
            title = { Text(if (subcategory == null)
                "Μετονομασία κατηγορίας" else "Μετονομασία υποκατηγορίας") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text(
                        "Η παλιά ονομασία θα διατηρηθεί ως alias στην ίδια ταυτότητα. " +
                            "Το ιστορικό, τα budgets και οι κανόνες δεν επανεγγράφονται.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it.take(120) },
                        singleLine = true,
                        label = { Text("Νέα ονομασία") },
                        enabled = !mutationBlocked,
                        modifier = Modifier.fillMaxWidth().testTag("s9_taxonomy_rename_name"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !mutationBlocked && valid,
                    onClick = {
                        onRename(kind, category, subcategory, renameDraft)
                        renameKind = null
                    },
                    modifier = Modifier.testTag("s9_taxonomy_rename_save"),
                ) { Text("Αποθήκευση") }
            },
            dismissButton = {
                TextButton(
                    onClick = { renameKind = null },
                    enabled = !mutationBlocked,
                ) { Text("Ακύρωση") }
            },
        )
    }
    if (newKind != null && onAdd != null) {
        val selectedKind = newKind!!
        val selectedParent = newParent
        val valid = newLabel.trim().isNotEmpty() && newLabel.trim().length <= 120
        AlertDialog(
            onDismissRequest = { if (!mutationBlocked) newKind = null },
            title = {
                Text(if (selectedParent == null) "Νέα κατηγορία" else "Νέα υποκατηγορία")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text(
                        (if (selectedKind == "expense") "Έξοδα" else "Έσοδα") +
                            (if (selectedParent == null) "" else " · " + selectedParent),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Δημιουργείται νέα ταυτότητα. Οι παλιές κινήσεις, " +
                            "κανόνες και προϋπολογισμοί δεν αλλάζουν.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it.take(120) },
                        label = { Text("Ονομασία") },
                        singleLine = true,
                        enabled = !mutationBlocked,
                        modifier = Modifier.fillMaxWidth()
                            .testTag("s9_taxonomy_add_name"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !mutationBlocked && valid,
                    onClick = {
                        onAdd(selectedKind, newLabel, selectedParent)
                        newKind = null
                    },
                    modifier = Modifier.testTag("s9_taxonomy_add_save"),
                ) { Text("Προσθήκη") }
            },
            dismissButton = {
                TextButton(
                    enabled = !mutationBlocked,
                    onClick = { newKind = null },
                ) { Text("Ακύρωση") }
            },
        )
    }
}
