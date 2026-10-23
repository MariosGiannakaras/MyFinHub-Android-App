package app.myfinhub.android.core.data

import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Follows Web categories.ts categoryKey and categoryIdentity.ts FNV-1a UTF-16 stable IDs. */
private fun nativeCategoryKey(value: String): String =
    Normalizer.normalize(value.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.forLanguageTag("el-GR"))

private fun nativeCategoryId(kind: String, name: String, parentId: String?): String {
    val token = kind + "|" + (if (parentId == null) "category" else "sub:" + parentId) +
        "|" + nativeCategoryKey(name)
    var hash = 2166136261u.toInt()
    token.forEach { hash = (hash xor it.code) * 16777619 }
    return (if (parentId == null) "cat" else "sub") + "-" + kind + "-" +
        Integer.toUnsignedString(hash, 36)
}

/**
 * Explicit Web-compatible category/subcategory addition. Current settings are CAS protected.
 * Historical events, budgets, rules, icon choices and old labels remain unchanged.
 */
data class AddCanonicalCategoryTaxonomy(
    val kind: String,
    val label: String,
    val parentCategory: String?,
    val expectedSettings: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description = "Προσθήκη κατηγορίας ή υποκατηγορίας"

    private fun treeKey(): String = when (kind) {
        "expense" -> "expenseCategoryTree"
        "income" -> "incomeCategoryTree"
        else -> throw IllegalArgumentException("Μη έγκυρος τύπος κατηγορίας.")
    }

    private fun flatKey() = if (kind == "expense") "expenseCategories" else "incomeCategories"

    private fun desiredSettings(): JsonObject {
        val name = label.trim().replace(Regex("\\s+"), " ")
        require(name.length in 1..120 && name.any(Char::isLetterOrDigit)) {
            "Συμπλήρωσε έγκυρο όνομα κατηγορίας (έως 120 χαρακτήρες)."
        }
        val existingTree = expectedSettings.array(treeKey())
        require(existingTree.isNotEmpty() || expectedSettings.array(flatKey()).isEmpty()) {
            "Πρέπει πρώτα να συγχρονιστεί το δέντρο κατηγοριών."
        }
        val rows = existingTree.map {
            it as? JsonObject ?: throw IllegalArgumentException("Μη έγκυρη εγγραφή κατηγορίας.")
        }
        require(rows.size < 2000 && rows.map { nativeCategoryKey(it.string("name").orEmpty()) }
            .distinct().size == rows.size) { "Διπλές κατηγορίες ή υπερβολικό πλήθος." }

        val identities = expectedSettings.obj("categoryIdentities").toMutableMap()
        fun identityMatches(row: JsonObject, parentId: String?, desired: String): Boolean {
            if (row.string("kind") != kind || row.string("parentId") != parentId) return false
            val names = listOfNotNull(row.string("label")) +
                row.array("aliases").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            return names.any { nativeCategoryKey(it) == nativeCategoryKey(desired) }
        }
        fun ensureIdentity(desired: String, parentId: String?): String {
            val matching = identities.entries.mapNotNull { (id, raw) ->
                val row = raw as? JsonObject ?: return@mapNotNull null
                if (identityMatches(row, parentId, desired)) id else null
            }
            require(matching.size <= 1) { "Μη μοναδική ταυτότητα κατηγορίας." }
            if (matching.size == 1) return matching.single()
            val base = nativeCategoryId(kind, desired, parentId)
            var id = base
            var index = 2
            while (id in identities) {
                id = base + "-" + index
                index++
            }
            identities[id] = JsonObject(buildMap {
                put("id", JsonPrimitive(id))
                put("kind", JsonPrimitive(kind))
                put("label", JsonPrimitive(desired))
                put("aliases", JsonArray(emptyList()))
                if (parentId != null) put("parentId", JsonPrimitive(parentId))
            })
            return id
        }
        val targetKey = nativeCategoryKey(name)
        val updatedTree: List<JsonObject>
        if (parentCategory == null) {
            val reserved = rows.mapNotNull { it.string("name") } +
                identities.values.filterIsInstance<JsonObject>()
                    .filter { it.string("kind") == kind && it.string("parentId") == null }
                    .flatMap { listOfNotNull(it.string("label")) +
                        it.array("aliases").mapNotNull { v ->
                            (v as? JsonPrimitive)?.contentOrNull
                        }
                    }
            require(reserved.none { nativeCategoryKey(it) == targetKey }) {
                "Υπάρχει ήδη αυτή η κατηγορία ή παλιά ονομασία."
            }
            ensureIdentity(name, null)
            updatedTree = rows + JsonObject(mapOf(
                "name" to JsonPrimitive(name), "subcategories" to JsonArray(emptyList())
            ))
        } else {
            val matches = rows.indices.filter {
                nativeCategoryKey(rows[it].string("name").orEmpty()) ==
                    nativeCategoryKey(parentCategory)
            }
            require(matches.size == 1) { "Η γονική κατηγορία δεν είναι μοναδική." }
            val idx = matches.single()
            val parent = rows[idx]
            val parentId = ensureIdentity(parent.string("name").orEmpty(), null)
            val children = parent.array("subcategories").map {
                (it as? JsonPrimitive)?.contentOrNull
                    ?: throw IllegalArgumentException("Μη έγκυρη υποκατηγορία.")
            }
            val reserved = children + identities.values.filterIsInstance<JsonObject>()
                .filter { row ->
                    row.string("kind") == kind &&
                        (row.string("parentId") == parentId ||
                            row.array("parentAliases").any {
                                (it as? JsonPrimitive)?.contentOrNull == parentId
                            })
                }
                .flatMap { listOfNotNull(it.string("label")) +
                    it.array("aliases").mapNotNull { v ->
                        (v as? JsonPrimitive)?.contentOrNull
                    }
                }
            require(children.size < 2000 && reserved.none {
                nativeCategoryKey(it) == targetKey
            }) { "Η υποκατηγορία ή παλιά ονομασία υπάρχει ήδη." }
            ensureIdentity(name, parentId)
            updatedTree = rows.toMutableList().apply {
                this[idx] = JsonObject(parent + (
                    "subcategories" to JsonArray((children + name).map(::JsonPrimitive))
                ))
            }
        }
        return JsonObject(expectedSettings + mapOf(
            treeKey() to JsonArray(updatedTree),
            flatKey() to JsonArray(updatedTree.map { JsonPrimitive(it.string("name").orEmpty()) }),
            "categoryIdentities" to JsonObject(identities),
        ))
    }

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean =
        runCatching { document.settingsObject() == desiredSettings() }.getOrDefault(false)

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(nowIso.isNotBlank()) { "Λείπει χρόνος εγγραφής." }
        val desired = desiredSettings()
        val current = document.settingsObject()
        if (current == desired) return document
        require(current == expectedSettings) {
            "Οι κατηγορίες ή οι ρυθμίσεις άλλαξαν σε άλλη συσκευή."
        }
        return document.withMutableState(
            document.state.updated("settings", desired), nowIso,
        )
    }
}
