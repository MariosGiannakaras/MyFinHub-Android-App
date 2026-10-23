package app.myfinhub.android.core.data

import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private fun movedSubcategoryKey(value: String): String =
    Normalizer.normalize(value.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.forLanguageTag("el-GR"))

/**
 * Web moveSubcategoryIdentity. Keeps source ID and original parent ID in parentAliases
 * so prior transactions/budget references remain resolvable without a ledger rewrite.
 */
data class MoveCanonicalSubcategoryTaxonomy(
    val kind: String,
    val sourceCategory: String,
    val subcategory: String,
    val targetCategory: String,
    val expectedSettings: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description = "Μεταφορά υποκατηγορίας"

    private fun desiredSettings(): JsonObject {
        require(kind in setOf("expense", "income") &&
            sourceCategory.isNotBlank() && targetCategory.isNotBlank() &&
            subcategory.isNotBlank()) { "Επίλεξε έγκυρες κατηγορίες." }
        val key = if (kind == "expense") "expenseCategoryTree" else "incomeCategoryTree"
        val sourceTree = expectedSettings.array(key)
        val rows = sourceTree.filterIsInstance<JsonObject>()
        require(sourceTree.isNotEmpty() && rows.size == sourceTree.size) {
            "Δεν υπάρχει πλήρες δέντρο κατηγοριών."
        }
        val first = rows.indices.filter {
            movedSubcategoryKey(rows[it].string("name").orEmpty()) ==
                movedSubcategoryKey(sourceCategory)
        }
        val second = rows.indices.filter {
            movedSubcategoryKey(rows[it].string("name").orEmpty()) ==
                movedSubcategoryKey(targetCategory)
        }
        require(first.size == 1 && second.size == 1 &&
            first.single() != second.single()) { "Επίλεξε διαφορετικές μοναδικές κατηγορίες." }
        val fromIndex = first.single()
        val toIndex = second.single()
        val from = rows[fromIndex]
        val to = rows[toIndex]
        val sourceName = from.string("name").orEmpty()
        val targetName = to.string("name").orEmpty()
        val sourceChildren = from.array("subcategories").map {
            (it as? JsonPrimitive)?.contentOrNull
                ?: throw IllegalArgumentException("Μη έγκυρη αρχική υποκατηγορία.")
        }
        val targetChildren = to.array("subcategories").map {
            (it as? JsonPrimitive)?.contentOrNull
                ?: throw IllegalArgumentException("Μη έγκυρη κατηγορία προορισμού.")
        }
        val matching = sourceChildren.indices.filter {
            movedSubcategoryKey(sourceChildren[it]) == movedSubcategoryKey(subcategory)
        }
        require(matching.size == 1 && targetChildren.size < 2000) {
            "Η υποκατηγορία δεν έχει μοναδική αρχική θέση."
        }
        val original = sourceChildren[matching.single()]
        require(targetChildren.none {
            movedSubcategoryKey(it) == movedSubcategoryKey(original)
        }) { "Η κατηγορία προορισμού έχει ήδη αυτή την υποκατηγορία." }
        val records = expectedSettings.obj("categoryIdentities").toMutableMap()
        fun parentId(label: String): String {
            val matchingParents = records.entries.filter { (id, raw) ->
                val row = raw as? JsonObject ?: return@filter false
                row.string("id") == id && row.string("kind") == kind &&
                    row.string("parentId") == null &&
                    (listOfNotNull(row.string("label")) + row.array("aliases").mapNotNull {
                        (it as? JsonPrimitive)?.contentOrNull
                    }).any { movedSubcategoryKey(it) == movedSubcategoryKey(label) }
            }
            require(matchingParents.size == 1) {
                "Δεν βρέθηκε μοναδική γονική ταυτότητα. Συγχρόνισε."
            }
            return matchingParents.single().key
        }
        val originalParentId = parentId(sourceName)
        val targetParentId = parentId(targetName)
        val matchingChildren = records.entries.filter { (id, raw) ->
            val row = raw as? JsonObject ?: return@filter false
            row.string("id") == id && row.string("kind") == kind &&
                row.string("parentId") == originalParentId &&
                (listOfNotNull(row.string("label")) + row.array("aliases").mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                }).any { movedSubcategoryKey(it) == movedSubcategoryKey(original) }
        }
        require(matchingChildren.size == 1) {
            "Η υποκατηγορία δεν έχει μοναδική σταθερή ταυτότητα."
        }
        val identityId = matchingChildren.single().key
        val identity = matchingChildren.single().value as JsonObject
        val reserved = (listOfNotNull(identity.string("label")) +
            identity.array("aliases").mapNotNull { (it as? JsonPrimitive)?.contentOrNull })
            .map(::movedSubcategoryKey).toSet()
        require(records.entries.none { (id, raw) ->
            if (id == identityId) return@none false
            val row = raw as? JsonObject ?: return@none false
            row.string("kind") == kind &&
                (row.string("parentId") == targetParentId ||
                    row.array("parentAliases").any {
                        (it as? JsonPrimitive)?.contentOrNull == targetParentId
                    }) &&
                (listOfNotNull(row.string("label")) +
                    row.array("aliases").mapNotNull {
                        (it as? JsonPrimitive)?.contentOrNull
                    }).any { movedSubcategoryKey(it) in reserved }
        }) { "Υπάρχει δεσμευμένη ονομασία στον προορισμό." }
        val parentAliases = (identity.array("parentAliases").mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        } + originalParentId).distinct().filter { it != targetParentId }
        records[identityId] = JsonObject(identity + mapOf(
            "parentId" to JsonPrimitive(targetParentId),
            "parentAliases" to JsonArray(parentAliases.map(::JsonPrimitive)),
        ))
        val revised = rows.toMutableList()
        revised[fromIndex] = JsonObject(from + (
            "subcategories" to JsonArray(sourceChildren.filterIndexed { i, _ ->
                i != matching.single()
            }.map(::JsonPrimitive))
        ))
        revised[toIndex] = JsonObject(to + (
            "subcategories" to JsonArray((targetChildren + original).map(::JsonPrimitive))
        ))
        val settings = expectedSettings.toMutableMap()
        settings[key] = JsonArray(revised)
        settings["categoryIdentities"] = JsonObject(records)
        val oldIconKey = kind + ":" + sourceName.trim() + ":" + original.trim()
        val newIconKey = kind + ":" + targetName.trim() + ":" + original.trim()
        for (field in listOf(
            "subcategoryIcons", "subcategoryIconPackSelections", "subcategoryIconColors",
        )) {
            val iconMap = expectedSettings.obj(field)
            require(newIconKey !in iconMap || oldIconKey !in iconMap) {
                "Υπάρχει ήδη ρύθμιση εικονιδίου στον προορισμό."
            }
            val mapped = mutableMapOf<String, JsonElement>()
            iconMap.forEach { (name, value) ->
                mapped[if (name == oldIconKey) newIconKey else name] = value
            }
            settings[field] = JsonObject(mapped)
        }
        return JsonObject(settings)
    }

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean =
        runCatching { document.settingsObject() == desiredSettings() }.getOrDefault(false)

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(nowIso.isNotBlank()) { "Λείπει ο χρόνος μεταφοράς." }
        val target = desiredSettings()
        if (document.settingsObject() == target) return document
        require(document.settingsObject() == expectedSettings) {
            "Το δέντρο άλλαξε στον server. Ανανέωσε τις κατηγορίες."
        }
        return document.withMutableState(document.state.updated("settings", target), nowIso)
    }
}
