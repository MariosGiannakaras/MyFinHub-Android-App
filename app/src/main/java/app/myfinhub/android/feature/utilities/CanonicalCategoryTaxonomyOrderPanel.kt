package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
) {
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
                if (rows.isNotEmpty()) {
                    Text(
                        if (kind == "expense") "Έξοδα" else "Έσοδα",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    var visible by rememberSaveable(kind) { mutableIntStateOf(12) }
                    rows.take(visible).forEachIndexed { index, row ->
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                            Text(row.name, style = MaterialTheme.typography.bodyLarge)
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
}
