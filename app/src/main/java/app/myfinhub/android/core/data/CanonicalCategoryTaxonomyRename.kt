package app.myfinhub.android.core.data

import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private fun renamedTaxonomyKey(value: String): String =
    Normalizer.normalize(value.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.forLanguageTag("el-GR"))

private fun normalizedTaxonomyLabel(value: String): String =
    value.trim().replace(Regex("\\s+"), " ")

private fun moveTaxonomyIconSetting(
    settings: JsonObject, key: String, old: String, new: String, prefix: Boolean,
): JsonObject {
    val entries = settings.obj(key)
    val target = mutableMapOf<String, JsonElement>()
    entries.forEach { (id, value) ->
        val moved = if (prefix && id.startsWith(old)) new + id.removePrefix(old)
            else if (!prefix && id == old) new else id
        require(moved == id || moved !in entries || moved == old) {
            "Η νέα ονομασία συγκρούεται με αποθηκευμένο εικονίδιο."
        }
        target[moved] = value
    }
    return JsonObject(target)
}

/**
 * Web taxonomyManagement / categoryIdentity rename: retain immutable identity IDs,
 * append the previous label to aliases, preserve finance history, and migrate icon keys.
 * Entire original settings are CAS guarded for durable offline/revision-safe replay.
 */
data class RenameCanonicalCategoryTaxonomy(
    val kind: String,
    val category: String,
    val subcategory: String?,
    val newLabel: String,
    val expectedSettings: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description = "Μετονομασία κατηγορίας"

    private fun desired(): JsonObject {
        require(kind in setOf("expense", "income")) { "Μη έγκυρος τύπος κατηγορίας." }
        val name = normalizedTaxonomyLabel(newLabel)
        require(name.length in 1..120 && name.any(Char::isLetterOrDigit)) {
            "Συμπλήρωσε έγκυρο όνομα έως 120 χαρακτήρες."
        }
        val treeKey = if (kind == "expense") "expenseCategoryTree" else "incomeCategoryTree"
        val flatKey = if (kind == "expense") "expenseCategories" else "incomeCategories"
        val source = expectedSettings.array(treeKey)
        val tree = source.filterIsInstance<JsonObject>()
        require(tree.size == source.size && tree.isNotEmpty()) {
            "Δεν υπάρχει έγκυρο αποθηκευμένο δέντρο κατηγοριών."
        }
        val matchedParents = tree.indices.filter {
            renamedTaxonomyKey(tree[it].string("name").orEmpty()) ==
                renamedTaxonomyKey(category)
        }
        require(matchedParents.size == 1) { "Η κατηγορία δεν είναι μοναδική." }
        val index = matchedParents.single()
        val parent = tree[index]
        val currentParentLabel = parent.string("name").orEmpty()
        val identities = expectedSettings.obj("categoryIdentities").toMutableMap()
        fun findRecord(label: String, parentId: String?): List<String> =
            identities.entries.mapNotNull { (id, raw) ->
                val entry = raw as? JsonObject ?: return@mapNotNull null
                if (entry.string("id") != id || entry.string("kind") != kind ||
                    entry.string("parentId") != parentId) return@mapNotNull null
                val aliases = entry.array("aliases").mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                }
                if ((listOfNotNull(entry.string("label")) + aliases).any {
                    renamedTaxonomyKey(it) == renamedTaxonomyKey(label)
                }) id else null
            }
        val parents = findRecord(currentParentLabel, null)
        require(parents.size == 1) {
            "Δεν υπάρχει μοναδική σταθερή ταυτότητα. Συγχρόνισε τις κατηγορίες."
        }
        val parentId = parents.single()
        val children = parent.array("subcategories")
        val subLabels = children.map {
            (it as? JsonPrimitive)?.contentOrNull
                ?: throw IllegalArgumentException("Μη έγκυρη υποκατηγορία.")
        }
        val parentScope = if (subcategory == null) null else parentId
        val original: String
        val identityId: String
        val childIndex: Int?
        if (subcategory == null) {
            original = currentParentLabel
            identityId = parentId
            childIndex = null
            require(tree.none { it !== parent &&
                renamedTaxonomyKey(it.string("name").orEmpty()) == renamedTaxonomyKey(name)
            }) { "Υπάρχει ήδη κατηγορία με αυτό το όνομα." }
        } else {
            val matches = subLabels.indices.filter {
                renamedTaxonomyKey(subLabels[it]) == renamedTaxonomyKey(subcategory)
            }
            require(matches.size == 1) { "Η υποκατηγορία δεν είναι μοναδική." }
            childIndex = matches.single()
            original = subLabels[childIndex]
            val found = findRecord(original, parentId)
            require(found.size == 1) {
                "Δεν υπάρχει σταθερή ταυτότητα υποκατηγορίας. Συγχρόνισε."
            }
            identityId = found.single()
            require(subLabels.none {
                renamedTaxonomyKey(it) == renamedTaxonomyKey(name) &&
                    renamedTaxonomyKey(it) != renamedTaxonomyKey(original)
            }) { "Η υποκατηγορία υπάρχει ήδη." }
        }
        require(identities.entries.none { (id, raw) ->
            if (id == identityId) return@none false
            val entry = raw as? JsonObject ?: return@none false
            if (entry.string("kind") != kind || entry.string("parentId") != parentScope)
                return@none false
            (listOfNotNull(entry.string("label")) + entry.array("aliases").mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }).any { renamedTaxonomyKey(it) == renamedTaxonomyKey(name) }
        }) { "Το όνομα ανήκει σε άλλη ενεργή ή ιστορική ταυτότητα." }
        val identity = identities[identityId] as JsonObject
        val aliases = (identity.array("aliases").mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        } + original).map(::normalizedTaxonomyLabel).filter(String::isNotEmpty)
            .distinctBy(::renamedTaxonomyKey)
            .filter { renamedTaxonomyKey(it) != renamedTaxonomyKey(name) }
        identities[identityId] = JsonObject(identity + mapOf(
            "label" to JsonPrimitive(name),
            "aliases" to JsonArray(aliases.map(::JsonPrimitive)),
        ))
        val revisedParent = if (childIndex == null) {
            JsonObject(parent + ("name" to JsonPrimitive(name)))
        } else {
            val revisedChildren = children.toMutableList()
            revisedChildren[childIndex] = JsonPrimitive(name)
            JsonObject(parent + ("subcategories" to JsonArray(revisedChildren)))
        }
        val updatedTree = tree.toMutableList()
        updatedTree[index] = revisedParent
        val revised = expectedSettings.toMutableMap()
        revised[treeKey] = JsonArray(updatedTree)
        if (childIndex == null) {
            revised[flatKey] = JsonArray(updatedTree.map { JsonPrimitive(it.string("name").orEmpty()) })
        }
        revised["categoryIdentities"] = JsonObject(identities)
        val oldCategoryKey = kind + ":" + currentParentLabel.trim()
        val newCategoryKey = kind + ":" + (if (childIndex == null) name else currentParentLabel).trim()
        for (field in listOf("categoryIcons", "categoryIconPackSelections", "categoryIconColors")) {
            if (childIndex == null) revised[field] = moveTaxonomyIconSetting(
                expectedSettings, field, oldCategoryKey, newCategoryKey, false,
            )
        }
        for (field in listOf(
            "subcategoryIcons", "subcategoryIconPackSelections", "subcategoryIconColors",
        )) {
            revised[field] = if (childIndex == null) moveTaxonomyIconSetting(
                expectedSettings, field, oldCategoryKey + ":", newCategoryKey + ":", true,
            ) else moveTaxonomyIconSetting(
                expectedSettings, field, oldCategoryKey + ":" + original.trim(),
                newCategoryKey + ":" + name, false,
            )
        }
        return JsonObject(revised)
    }

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean =
        runCatching { document.settingsObject() == desired() }.getOrDefault(false)

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(nowIso.isNotBlank()) { "Δεν υπάρχει χρόνος αλλαγής." }
        val target = desired()
        val current = document.settingsObject()
        if (current == target) return document
        require(current == expectedSettings) {
            "Το δέντρο ή οι ρυθμίσεις άλλαξαν σε άλλη συσκευή. Ανανέωσε."
        }
        return document.withMutableState(
            document.state.updated("settings", target), nowIso,
        )
    }
}
