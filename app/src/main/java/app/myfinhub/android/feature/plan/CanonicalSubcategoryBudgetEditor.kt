package app.myfinhub.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.MyFinHubSpacing

data class SubcategoryBudgetEditRequest(
    val budgetId: String?,
    val category: String,
    val subcategory: String,
    val monthlyLimitText: String,
    val alertThresholdText: String,
    val carryover: Boolean,
)

/** A subcategory editor never changes the parent category or a historical transaction. */
@Composable
fun CanonicalSubcategoryBudgetEditor(
    initial: CategoryBudget?,
    categories: Map<String, List<String>>,
    existing: List<CategoryBudget>,
    mutationBlocked: Boolean,
    onDismiss: () -> Unit,
    onSave: (SubcategoryBudgetEditRequest) -> Unit,
) {
    var category by remember(initial?.id) { mutableStateOf(initial?.category.orEmpty()) }
    var subcategory by remember(initial?.id) { mutableStateOf(initial?.subcategory.orEmpty()) }
    var amount by remember(initial?.id) { mutableStateOf(initial?.monthlyLimitText.orEmpty()) }
    var threshold by remember(initial?.id) {
        mutableStateOf(initial?.alertThresholdText ?: "80")
    }
    var carryover by remember(initial?.id) { mutableStateOf(initial?.carryover ?: false) }
    var categoriesOpen by remember { mutableStateOf(false) }
    var subcategoriesOpen by remember { mutableStateOf(false) }
    val validAmount = mobileBudgetLimitOrNull(amount) != null
    val availableSubcategories = categories[category].orEmpty()
    val selectedKnown = if (initial != null) {
        initial.category == category && initial.subcategory == subcategory
    } else {
        category in categories && subcategory in availableSubcategories
    }
    val valid = validAmount && selectedKnown && category.isNotBlank() &&
        subcategory.isNotBlank() &&
        (threshold.toIntOrNull()?.let { it in 50..99 } == true) &&
        existing.none {
            it.id != initial?.id &&
                it.category?.trim() == category.trim() &&
                it.subcategory?.trim() == subcategory.trim()
        }

    AlertDialog(
        onDismissRequest = { if (!mutationBlocked) onDismiss() },
        title = {
            Text(if (initial == null) "Νέο όριο υποκατηγορίας"
                else "Επεξεργασία ορίου υποκατηγορίας")
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            ) {
                Text(
                    "Ο προϋπολογισμός καταγράφει όριο, όχι νέα δαπάνη. " +
                        "Οι δαπάνες γονικής κατηγορίας δεν προστίθενται δεύτερη φορά.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(
                    onClick = { categoriesOpen = true },
                    enabled = !mutationBlocked && initial == null,
                    modifier = Modifier.fillMaxWidth()
                        .testTag("s7_subcategory_editor_category"),
                ) { Text(if (category.isBlank()) "Επίλεξε κατηγορία" else category) }
                DropdownMenu(
                    expanded = categoriesOpen,
                    onDismissRequest = { categoriesOpen = false },
                ) {
                    categories.filterValues { it.isNotEmpty() }.keys.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                category = name
                                subcategory = ""
                                categoriesOpen = false
                            },
                        )
                    }
                }
                TextButton(
                    onClick = { subcategoriesOpen = true },
                    enabled = !mutationBlocked && initial == null && category.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                        .testTag("s7_subcategory_editor_subcategory"),
                ) { Text(if (subcategory.isBlank()) "Επίλεξε υποκατηγορία" else subcategory) }
                DropdownMenu(
                    expanded = subcategoriesOpen,
                    onDismissRequest = { subcategoriesOpen = false },
                ) {
                    availableSubcategories.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                subcategory = name
                                subcategoriesOpen = false
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    enabled = !mutationBlocked,
                    label = { Text("Μηνιαίο όριο (€)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                        .testTag("s7_subcategory_editor_amount"),
                )
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it.filter(Char::isDigit).take(3) },
                    enabled = !mutationBlocked,
                    label = { Text("Προειδοποίηση (50–99%)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                        .testTag("s7_subcategory_editor_threshold"),
                )
                TextButton(
                    onClick = { carryover = !carryover },
                    enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth()
                        .testTag("s7_subcategory_editor_carryover"),
                ) {
                    Text(if (carryover) "Μεταφορά υπολοίπου: ενεργή"
                        else "Μεταφορά υπολοίπου: ανενεργή")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !mutationBlocked && valid,
                modifier = Modifier.testTag("s7_subcategory_editor_save"),
                onClick = {
                    onSave(SubcategoryBudgetEditRequest(
                        budgetId = initial?.id,
                        category = category,
                        subcategory = subcategory,
                        monthlyLimitText = amount,
                        alertThresholdText = threshold,
                        carryover = carryover,
                    ))
                },
            ) { Text("Αποθήκευση") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !mutationBlocked) { Text("Ακύρωση") }
        },
    )
}
