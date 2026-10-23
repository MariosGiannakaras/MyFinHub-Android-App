package app.myfinhub.android.core.data

import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Explicit current Web category tree; historical labels are never rewritten. */
data class CanonicalCategoryTaxonomyRow(
    val name: String,
    val subcategories: List<String>,
)

fun CanonicalFinanceDocument.canonicalCategoryTaxonomy(kind: String): List<CanonicalCategoryTaxonomyRow> {
    val key = when (kind) {
        "expense" -> "expenseCategoryTree"
        "income" -> "incomeCategoryTree"
        else -> return emptyList()
    }
    return settingsObject().array(key).filterIsInstance<JsonObject>()
        .mapNotNull { row ->
            val name = row.string("name")?.trim()?.takeIf(String::isNotEmpty)
                ?: return@mapNotNull null
            CanonicalCategoryTaxonomyRow(
                name, row.array("subcategories").mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull?.trim()
                }.filter(String::isNotEmpty),
            )
        }
}

private fun taxonomyLabelKey(value: String): String =
    Normalizer.normalize(value.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.forLanguageTag("el-GR"))

/**
 * Web taxonomyManagement reorder operations. Only display order changes; identities,
 * aliases, all category labels, budgets, rules, future obligations and ledger stay intact.
 * Server source snapshot conflicts must be reviewed rather than silently overwritten.
 */
data class ReorderCanonicalCategoryTaxonomy(
    val kind: String,
    val category: String,
    val subcategory: String?,
    val direction: Int,
    val expectedTree: JsonArray,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description = "Αλλαγή σειράς κατηγοριών"

    private fun sourceKey() = when (kind) {
        "expense" -> "expenseCategoryTree"
        "income" -> "incomeCategoryTree"
        else -> throw IllegalArgumentException("Μη έγκυρος τύπος κατηγορίας.")
    }

    private fun expectedReordered(): JsonArray {
        require(direction == -1 || direction == 1) { "Επιτρέπεται μόνο μία θέση ανά αλλαγή." }
        require(expectedTree.size in 1..2000) { "Το δέντρο κατηγοριών δεν είναι διαθέσιμο." }
        val rows = expectedTree.filterIsInstance<JsonObject>()
        require(rows.size == expectedTree.size) { "Υπάρχει μη έγκυρη εγγραφή κατηγορίας." }
        val labels = rows.map { it.string("name")?.trim().orEmpty() }
        require(labels.none(String::isEmpty) &&
            labels.map(::taxonomyLabelKey).distinct().size == labels.size) {
            "Μη μοναδικές κατηγορίες: απαιτείται έλεγχος."
        }
        val index = labels.indexOfFirst { taxonomyLabelKey(it) == taxonomyLabelKey(category) }
        require(index >= 0) { "Η κατηγορία δεν βρέθηκε." }
        val next = rows.toMutableList()
        if (subcategory == null) {
            val target = index + direction
            require(target in rows.indices) { "Η κατηγορία είναι ήδη στην άκρη." }
            val item = next.removeAt(index)
            next.add(target, item)
        } else {
            val original = rows[index]
            val children = original.array("subcategories").map {
                (it as? JsonPrimitive)?.contentOrNull
                    ?: throw IllegalArgumentException("Μη έγκυρη υποκατηγορία.")
            }
            require(children.isNotEmpty() && children.map(::taxonomyLabelKey)
                .distinct().size == children.size) {
                "Το δέντρο περιέχει μη μοναδικές υποκατηγορίες."
            }
            val subIndex = children.indexOfFirst {
                taxonomyLabelKey(it) == taxonomyLabelKey(subcategory)
            }
            val target = subIndex + direction
            require(subIndex >= 0 && target in children.indices) {
                "Η υποκατηγορία είναι ήδη στην άκρη ή δεν υπάρχει."
            }
            val updatedChildren = children.toMutableList()
            val moved = updatedChildren.removeAt(subIndex)
            updatedChildren.add(target, moved)
            next[index] = JsonObject(original + (
                "subcategories" to JsonArray(updatedChildren.map(::JsonPrimitive))
            ))
        }
        return JsonArray(next)
    }

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean =
        runCatching {
            document.settingsObject().array(sourceKey()) == expectedReordered()
        }.getOrDefault(false)

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val key = sourceKey()
        val desired = expectedReordered()
        val settings = document.settingsObject()
        val actual = settings.array(key)
        if (actual == desired) return document
        require(actual == expectedTree) {
            "Η σειρά ή οι ονομασίες κατηγοριών άλλαξαν σε άλλη συσκευή."
        }
        return document.withMutableState(
            document.state.updated("settings", JsonObject(settings + (key to desired))),
            nowIso,
        )
    }
}
