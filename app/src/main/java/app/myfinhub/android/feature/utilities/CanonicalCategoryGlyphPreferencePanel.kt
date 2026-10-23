package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.data.CanonicalCategoryTaxonomyRow
import app.myfinhub.android.core.data.CANONICAL_CATEGORY_ICON_PACKS
import app.myfinhub.android.core.data.canonicalNativeIconsForPack
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.myFinHubSemanticCategoryIcon
import app.myfinhub.android.designsystem.myFinHubCategoryIcon

private val nativeIconNames = mapOf(
    "coffee" to "Καφές", "dining" to "Φαγητό", "groceries" to "Τρόφιμα",
    "shopping" to "Αγορές", "fuel" to "Καύσιμα", "car" to "Μετακίνηση",
    "home" to "Σπίτι", "electricity" to "Λογαριασμοί", "health" to "Υγεία",
    "travel" to "Ταξίδι", "entertainment" to "Ψυχαγωγία",
    "subscription" to "Συνδρομή", "cash" to "Μετρητά", "saving" to "Αποταμίευση",
    "salary" to "Μισθός", "card" to "Κάρτα", "transfer" to "Μεταφορά",
    "loan" to "Δάνειο", "other" to "Γενικό",
)
private fun packLabel(pack: String) = when (pack) {
    "lucide" -> "Lucide"
    "tabler" -> "Tabler"
    "phosphor" -> "Phosphor"
    "heroicons" -> "Heroicons"
    "bootstrap" -> "Bootstrap"
    else -> pack
}

/**
 * Settings editor for a safe native-renderable subset of Web semantic icon keys.
 * All five icon-pack identities and their independent selections are retained by the
 * Web state contract. Android currently renders curated equivalents rather than
 * advertising thousands of non-bundled desktop SVGs as locally available.
 */
@Composable
fun CanonicalCategoryGlyphPreferencePanel(
    expense: List<CanonicalCategoryTaxonomyRow>,
    income: List<CanonicalCategoryTaxonomyRow>,
    activePack: String,
    overrides: Map<String, String>,
    mutationBlocked: Boolean,
    onSetPack: (String) -> Unit,
    onSetGlyph: (String, String, String?, String?) -> Unit,
) {
    var packDialog by remember { mutableStateOf(false) }
    var editKind by remember { mutableStateOf<String?>(null) }
    var editCategory by remember { mutableStateOf("") }
    var editSubcategory by remember { mutableStateOf<String?>(null) }
    var visibleExpense by rememberSaveable { mutableIntStateOf(8) }
    var visibleIncome by rememberSaveable { mutableIntStateOf(8) }

    fun select(kind: String, category: String, subcategory: String?) {
        editKind = kind
        editCategory = category
        editSubcategory = subcategory
    }
    MyFinHubSectionCard(
        modifier = Modifier.fillMaxWidth().testTag("s9_category_glyph_preferences"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Εικονίδια κατηγοριών", style = MaterialTheme.typography.titleLarge)
            Text(
                "Οι προτιμήσεις αποθηκεύονται ανά βιβλιοθήκη, όπως στο Web. " +
                    "Στο Android εμφανίζεται μόνο το διαθέσιμο υποσύνολο εγγενών εικονιδίων. " +
                    "Οι μη διαθέσιμες επιλογές του Web δεν διαγράφονται.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { packDialog = true },
                enabled = !mutationBlocked,
                modifier = Modifier.fillMaxWidth().testTag("s9_category_icon_pack"),
            ) { Text("Οικογένεια: " + packLabel(activePack)) }
            listOf(
                Triple("expense", expense, visibleExpense),
                Triple("income", income, visibleIncome),
            ).forEach { (kind, categories, count) ->
                Text(
                    if (kind == "expense") "Εικονίδια εξόδων" else "Εικονίδια εσόδων",
                    style = MaterialTheme.typography.titleMedium,
                )
                categories.take(count).forEachIndexed { index, row ->
                    val key = "$kind:${row.name}"
                    TextButton(
                        onClick = { select(kind, row.name, null) },
                        enabled = !mutationBlocked,
                        modifier = Modifier.fillMaxWidth()
                            .testTag("s9_category_glyph_${kind}_$index"),
                    ) {
                        Icon(
                            imageVector = myFinHubCategoryIcon(
                                row.name, MyFinHubIcons.All, overrides[key],
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(MyFinHubSpacing.xs))
                        Text(row.name + " · " +
                            (overrides[key]?.let { nativeIconNames[it] } ?: "Αυτόματο"))
                    }
                    row.subcategories.take(16).forEachIndexed { childIndex, child ->
                        val childKey = "$key:$child"
                        TextButton(
                            onClick = { select(kind, row.name, child) },
                            enabled = !mutationBlocked,
                            modifier = Modifier.fillMaxWidth()
                                .testTag("s9_subcategory_glyph_${kind}_${index}_$childIndex"),
                        ) {
                            Icon(
                                imageVector = myFinHubCategoryIcon(
                                    row.name, MyFinHubIcons.All,
                                    overrides[childKey] ?: overrides[key],
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(MyFinHubSpacing.xs))
                            Text("↳ $child · " +
                                (overrides[childKey]?.let { nativeIconNames[it] } ?: "Αυτόματο"))
                        }
                    }
                }
                if (categories.size > count) {
                    TextButton(
                        onClick = {
                            if (kind == "expense") visibleExpense = (count + 8)
                                .coerceAtMost(categories.size)
                            else visibleIncome = (count + 8).coerceAtMost(categories.size)
                        },
                    ) { Text("Περισσότερες κατηγορίες") }
                }
            }
        }
    }
    if (packDialog) {
        AlertDialog(
            onDismissRequest = { if (!mutationBlocked) packDialog = false },
            title = { Text("Οικογένεια εικονιδίων Web") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    CANONICAL_CATEGORY_ICON_PACKS.forEach { pack ->
                        TextButton(
                            onClick = {
                                onSetPack(pack)
                                packDialog = false
                            },
                            enabled = !mutationBlocked,
                            modifier = Modifier.fillMaxWidth()
                                .testTag("s9_select_icon_pack_$pack"),
                        ) { Text(packLabel(pack) + if (pack == activePack) " · Ενεργή" else "") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { packDialog = false }) { Text("Κλείσιμο") }
            },
        )
    }
    editKind?.let { kind ->
        val key = "$kind:$editCategory" +
            if (editSubcategory == null) "" else ":$editSubcategory"
        val keys = canonicalNativeIconsForPack(activePack)
        AlertDialog(
            onDismissRequest = { if (!mutationBlocked) editKind = null },
            title = { Text("Εικονίδιο: " + (editSubcategory ?: editCategory)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                ) {
                    Text(
                        "Ενεργή βιβλιοθήκη: " + packLabel(activePack) +
                            " · Επιλογές συμβατές με τα εγγενή εικονίδια Android.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = {
                            onSetGlyph(kind, editCategory, editSubcategory, null)
                            editKind = null
                        },
                        enabled = !mutationBlocked,
                        modifier = Modifier.fillMaxWidth()
                            .testTag("s9_category_glyph_auto"),
                    ) { Text("Αυτόματη αντιστοίχιση") }
                    keys.forEach { semantic ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        ) {
                            myFinHubSemanticCategoryIcon(semantic)?.let { vector ->
                                Icon(vector, contentDescription = null)
                            }
                            TextButton(
                                onClick = {
                                    onSetGlyph(kind, editCategory, editSubcategory, semantic)
                                    editKind = null
                                },
                                enabled = !mutationBlocked,
                                modifier = Modifier.testTag("s9_category_glyph_option_$semantic"),
                            ) {
                                Text((nativeIconNames[semantic] ?: semantic) +
                                    if (overrides[key] == semantic) " · Επιλεγμένο" else "")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editKind = null }) { Text("Κλείσιμο") }
            },
        )
    }
}
