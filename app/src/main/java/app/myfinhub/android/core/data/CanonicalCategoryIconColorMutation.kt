package app.myfinhub.android.core.data

import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Web categoryIconPreferences withCategoryIconColor / withSubcategoryIconColor.
 * No icon-pack or semantic icon changes; preserve all existing finance/category identity rows.
 */
data class SetCanonicalCategoryIconColor(
    val kind: String,
    val category: String,
    val subcategory: String?,
    val color: String?,
    val expectedSettings: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description = "Χρώμα εικονιδίου κατηγορίας"

    private fun desired(): JsonObject {
        require(kind in setOf("expense", "income")) { "Μη έγκυρος τύπος κατηγορίας." }
        require(category.isNotBlank() && category == category.trim()) {
            "Η κατηγορία δεν έχει έγκυρη ονομασία."
        }
        if (subcategory != null) require(subcategory.isNotBlank() &&
            subcategory == subcategory.trim()) { "Μη έγκυρη υποκατηγορία." }
        val treeKey = if (kind == "expense") "expenseCategoryTree" else "incomeCategoryTree"
        val rows = expectedSettings.array(treeKey).filterIsInstance<JsonObject>()
        val categoryRows = rows.filter { it.string("name") == category }
        require(categoryRows.size == 1) { "Η κατηγορία δεν είναι διαθέσιμη ή μοναδική." }
        if (subcategory != null) {
            val children = categoryRows.single().array("subcategories").mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }
            require(children.count { it == subcategory } == 1) {
                "Η υποκατηγορία δεν είναι διαθέσιμη ή μοναδική."
            }
        }
        val normalized = color?.trim()?.takeIf(String::isNotEmpty)?.also {
            require(Regex("^#[0-9a-fA-F]{6}$").matches(it)) {
                "Χρησιμοποίησε χρώμα #RRGGBB (έξι ψηφία)."
            }
        }?.uppercase(Locale.ROOT)
        val key = kind + ":" + category +
            if (subcategory == null) "" else ":" + subcategory
        val field = if (subcategory == null) "categoryIconColors" else "subcategoryIconColors"
        val overrides = expectedSettings.obj(field).toMutableMap()
        if (normalized == null) overrides.remove(key)
        else overrides[key] = JsonPrimitive(normalized)
        return JsonObject(expectedSettings + (field to JsonObject(overrides)))
    }

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean =
        runCatching { document.settingsObject() == desired() }.getOrDefault(false)

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(nowIso.isNotBlank()) { "Δεν υπάρχει χρόνος ενημέρωσης." }
        val target = desired()
        if (document.settingsObject() == target) return document
        require(document.settingsObject() == expectedSettings) {
            "Οι ρυθμίσεις άλλαξαν στον server. Ανανέωσε πρώτα τα δεδομένα."
        }
        return document.withMutableState(
            document.state.updated("settings", target), nowIso,
        )
    }
}
