package app.myfinhub.android.core.data

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private fun retirementKey(value: String): String =
    Normalizer.normalize(value.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.forLanguageTag("el-GR"))

private fun retirementNames(row: JsonObject): List<String> =
    listOfNotNull(row.string("label")) +
        row.array("aliases").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

private fun JsonObject.retirementIdentityFor(kind: String, name: String, parent: String?): String? {
    val found = entries.mapNotNull { (id, raw) ->
        val row = raw as? JsonObject ?: return@mapNotNull null
        if (row.string("id") == id && row.string("kind") == kind &&
            (row.string("parentId") == parent ||
                (parent != null && row.array("parentAliases").any {
                    (it as? JsonPrimitive)?.contentOrNull == parent
                })) &&
            retirementNames(row).any { retirementKey(it) == retirementKey(name) }
        ) id else null
    }
    return found.singleOrNull()
}

private fun JsonObject.retirementIdentityMatches(id: String?, category: String, kind: String,
    parentId: String?, child: String? = null,
): Boolean {
    if (id == null) return false
    val identities = this
    if (child == null) return identities.retirementIdentityFor(kind, category, null) == id
    val parent = parentId ?: identities.retirementIdentityFor(kind, category, null)
    return identities.retirementIdentityFor(kind, child, parent) == id
}

private fun CanonicalFinanceDocument.retirementReferences(
    kind: String, category: String, subcategory: String?, asOf: String,
    identityId: String,
): List<String> {
    val settings = settingsObject()
    val identities = settings.obj("categoryIdentities")
    val parentId = identities.retirementIdentityFor(kind, category, null)
    val currentMonth = asOf.take(7)
    val blockers = mutableListOf<String>()
    fun links(cat: String?, sub: String?): Boolean {
        if (cat.isNullOrBlank()) return false
        return if (subcategory == null) {
            identities.retirementIdentityFor(kind, cat, null) == identityId
        } else {
            !sub.isNullOrBlank() &&
                identities.retirementIdentityFor(
                    kind, sub, identities.retirementIdentityFor(kind, cat, null),
                ) == identityId
        }
    }
    val recurringOverrides = state.obj("recurringOverrides")
    val seedRecurring = seed.array("recurring").filterIsInstance<JsonObject>().map { row ->
        (recurringOverrides[row.string("id").orEmpty()] as? JsonObject) ?: row
    }
    val recurring = seedRecurring + state.array("recurringCustom").filterIsInstance<JsonObject>()
    recurring.forEach { row ->
        if (row.bool("active") != false && row.string("status") != "stopped" &&
            links(row.string("category"), row.string("subcategory"))
        ) blockers += "Πάγιο: " + row.string("name").orEmpty().ifBlank { row.string("id").orEmpty() }
    }
    state.array("scheduled").filterIsInstance<JsonObject>().forEach { row ->
        if (row.string("status") == "pending" &&
            links(row.string("category"), row.string("subcategory"))
        ) blockers += "Προγραμματισμένη κίνηση: " +
            row.string("note").orEmpty().ifBlank { row.string("id").orEmpty() }
    }
    state.array("transactionRules").filterIsInstance<JsonObject>().forEach { row ->
        val action = row.obj("action")
        if (row.bool("enabled") == true &&
            links(action.string("category"), action.string("subcategory"))
        ) blockers += "Ενεργός κανόνας: " +
            row.string("name").orEmpty().ifBlank { row.string("id").orEmpty() }
    }
    if (kind == "expense") {
        state.array("budgets").filterIsInstance<JsonObject>().forEach { row ->
            val scope = row.string("scope")
            if (row.string("month").orEmpty() < currentMonth) return@forEach
            val relevant = if (subcategory == null) scope in setOf("category", "subcategory")
                else scope == "subcategory"
            if (!relevant) return@forEach
            val idMatches = if (subcategory == null) {
                row.string("categoryIdentityId") == identityId ||
                    identities.retirementIdentityFor(
                        kind, row.string("category").orEmpty(), null,
                    ) == identityId
            } else {
                row.string("subcategoryIdentityId") == identityId ||
                    (parentId != null && identities.retirementIdentityFor(
                        kind, row.string("subcategory").orEmpty(),
                        identities.retirementIdentityFor(
                            kind, row.string("category").orEmpty(), null,
                        ),
                    ) == identityId)
            }
            if (idMatches) blockers += "Budget " + row.string("month").orEmpty()
        }
    }
    return blockers.distinct()
}

/** Conservative Web taxonomyRetirementDependencies; requires all current references resolved. */
fun CanonicalFinanceDocument.categoryRetirementBlockers(
    kind: String, category: String, subcategory: String?, asOf: String,
): List<String> {
    require(kind in setOf("expense", "income"))
    require(runCatching { LocalDate.parse(asOf) }.isSuccess)
    val settings = settingsObject()
    val key = if (kind == "expense") "expenseCategoryTree" else "incomeCategoryTree"
    val parentRows = settings.array(key).filterIsInstance<JsonObject>()
        .filter { retirementKey(it.string("name").orEmpty()) == retirementKey(category) }
    require(parentRows.size == 1) { "Η κατηγορία δεν είναι μοναδική." }
    val sourceParent = parentRows.single()
    val children = sourceParent.array("subcategories").mapNotNull {
        (it as? JsonPrimitive)?.contentOrNull
    }
    val identities = settings.obj("categoryIdentities")
    val parentId = identities.retirementIdentityFor(kind, category, null)
        ?: throw IllegalArgumentException("Δεν υπάρχει μοναδική ταυτότητα κατηγορίας.")
    val targetId = if (subcategory == null) parentId else
        identities.retirementIdentityFor(kind, subcategory, parentId)
            ?: throw IllegalArgumentException("Δεν υπάρχει μοναδική υποκατηγορία.")
    val reasons = mutableListOf<String>()
    if (subcategory == null) {
        children.forEach { reasons += "Υποκατηγορία: " + it }
    } else require(children.count {
        retirementKey(it) == retirementKey(subcategory)
    } == 1) { "Η υποκατηγορία απουσιάζει ή έχει διπλή εγγραφή." }
    reasons += retirementReferences(kind, category, subcategory, asOf, targetId)
    return reasons.distinct()
}

/**
 * Remove only a dependency-free active taxonomy label. Stable identity and aliases
 * survive for all historical budgets/events. No accounting ledger entries are changed.
 */
data class RetireCanonicalCategoryTaxonomy(
    val kind: String,
    val category: String,
    val subcategory: String?,
    val asOf: String,
    val expectedSettings: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description = "Απόσυρση κατηγορίας"

    private fun desired(): JsonObject {
        require(kind in setOf("expense", "income"))
        val treeKey = if (kind == "expense") "expenseCategoryTree" else "incomeCategoryTree"
        val flatKey = if (kind == "expense") "expenseCategories" else "incomeCategories"
        val source = expectedSettings.array(treeKey)
        val rows = source.filterIsInstance<JsonObject>()
        require(rows.size == source.size && rows.isNotEmpty()) {
            "Το δέντρο κατηγοριών δεν είναι διαθέσιμο."
        }
        val matches = rows.indices.filter {
            retirementKey(rows[it].string("name").orEmpty()) == retirementKey(category)
        }
        require(matches.size == 1) { "Η κατηγορία δεν είναι μοναδική." }
        val index = matches.single()
        val parent = rows[index]
        val children = parent.array("subcategories").map {
            (it as? JsonPrimitive)?.contentOrNull
                ?: throw IllegalArgumentException("Μη έγκυρη υποκατηγορία.")
        }
        val revised = rows.toMutableList()
        val childKey: String?
        if (subcategory == null) {
            require(children.isEmpty()) { "Απόσυρε πρώτα τις υποκατηγορίες." }
            revised.removeAt(index)
            childKey = null
        } else {
            val subs = children.indices.filter {
                retirementKey(children[it]) == retirementKey(subcategory)
            }
            require(subs.size == 1) { "Η υποκατηγορία δεν είναι μοναδική." }
            val nextChildren = children.toMutableList()
            childKey = nextChildren.removeAt(subs.single())
            revised[index] = JsonObject(parent + (
                "subcategories" to JsonArray(nextChildren.map(::JsonPrimitive))
            ))
        }
        val result = expectedSettings.toMutableMap()
        result[treeKey] = JsonArray(revised)
        if (subcategory == null) {
            result[flatKey] = JsonArray(revised.map { JsonPrimitive(it.string("name").orEmpty()) })
        }
        val iconKey = kind + ":" + parent.string("name").orEmpty().trim()
        val subIconKey = iconKey + ":" + childKey.orEmpty().trim()
        for (field in listOf(
            "categoryIcons", "categoryIconPackSelections", "categoryIconColors",
            "subcategoryIcons", "subcategoryIconPackSelections", "subcategoryIconColors",
        )) {
            val isSub = field.startsWith("subcategory")
            val existing = expectedSettings.obj(field)
            result[field] = JsonObject(existing.filterKeys { key ->
                if (subcategory == null) {
                    if (isSub) !key.startsWith(iconKey + ":") else key != iconKey
                } else if (isSub) key != subIconKey else true
            })
        }
        return JsonObject(result)
    }

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean =
        runCatching { document.settingsObject() == desired() }.getOrDefault(false)

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(nowIso.isNotBlank())
        val target = desired()
        if (document.settingsObject() == target) return document
        require(document.settingsObject() == expectedSettings) {
            "Οι κατηγορίες άλλαξαν στον server. Ανανέωσε το δέντρο."
        }
        val blockers = document.categoryRetirementBlockers(kind, category, subcategory, asOf)
        require(blockers.isEmpty()) {
            "Η απόσυρση μπλοκάρεται από " + blockers.size +
                " ενεργές αναφορές: " + blockers.take(4).joinToString(", ")
        }
        return document.withMutableState(
            document.state.updated("settings", target), nowIso,
        )
    }
}
