package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Web CategoryIconPackId: no remote or user-controlled SVG/URL loading on Android. */
val CANONICAL_CATEGORY_ICON_PACKS: List<String> =
    listOf("lucide", "tabler", "phosphor", "heroicons", "bootstrap")

/**
 * Curated semantic keys for which Android can display an unambiguous local vector.
 * Web has a much larger offline SVG catalog; do not advertise those unmapped assets
 * as visually available in the native picker.
 */
val NATIVE_CATEGORY_ICON_KEYS: List<String> = listOf(
    "coffee", "dining", "groceries", "shopping", "fuel", "car", "home",
    "electricity", "health", "travel", "entertainment", "subscription",
    "cash", "saving", "salary", "card", "transfer", "loan", "other",
)

private val LIMITED_PACK_KEYS = mapOf(
    "tabler" to setOf("coffee", "shopping", "car", "home", "health", "other"),
    "phosphor" to setOf("coffee", "shopping", "car", "home", "health"),
    "heroicons" to setOf("shopping", "home", "health"),
    "bootstrap" to setOf("shopping", "home", "health"),
)

fun canonicalNativeIconsForPack(pack: String): List<String> =
    if (pack == "lucide") NATIVE_CATEGORY_ICON_KEYS else
        NATIVE_CATEGORY_ICON_KEYS.filter { it in LIMITED_PACK_KEYS[pack].orEmpty() }

/**
 * Mirrors Web categoryIconPreferences pack and semantic override storage.
 * Whole-settings source CAS avoids overwriting a concurrent icon or taxonomy edit.
 */
data class SetCanonicalCategoryIconPreference(
    val pack: String,
    val kind: String?,
    val category: String?,
    val subcategory: String?,
    val semanticKey: String?,
    val expectedSettings: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description = "Επιλογή εικονιδίου κατηγορίας"

    private fun target(): JsonObject {
        require(pack in CANONICAL_CATEGORY_ICON_PACKS) {
            "Άγνωστη οικογένεια εικονιδίων."
        }
        require(nowIso.isNotBlank()) { "Λείπει χρόνος αλλαγής." }
        if (kind == null) {
            require(category == null && subcategory == null && semanticKey == null) {
                "Η αλλαγή βιβλιοθήκης δεν απαιτεί κατηγορία."
            }
            return JsonObject(expectedSettings + mapOf("categoryIconPack" to JsonPrimitive(pack)))
        }
        require(kind in setOf("expense", "income") && !category.isNullOrBlank() &&
            category == category.trim()) { "Μη έγκυρη κατηγορία εικονιδίου." }
        if (subcategory != null) {
            require(subcategory.isNotBlank() && subcategory == subcategory.trim()) {
                "Μη έγκυρη υποκατηγορία εικονιδίου."
            }
        }
        val treeField = if (kind == "expense") "expenseCategoryTree" else "incomeCategoryTree"
        val matches = expectedSettings.array(treeField)
            .filterIsInstance<JsonObject>()
            .filter { it.string("name") == category }
        require(matches.size == 1) { "Η κατηγορία δεν είναι μοναδική." }
        if (subcategory != null) {
            require(matches.single().array("subcategories").count {
                (it as? JsonPrimitive)?.content == subcategory
            } == 1) { "Η υποκατηγορία δεν είναι μοναδική." }
        }
        if (semanticKey != null) {
            require(semanticKey in canonicalNativeIconsForPack(pack)) {
                "Το εικονίδιο δεν υποστηρίζεται σε αυτή τη βιβλιοθήκη."
            }
        }
        val preference = kind + ":" + category +
            if (subcategory == null) "" else ":" + subcategory
        val legacyField = if (subcategory == null) "categoryIcons" else "subcategoryIcons"
        val savedField = if (subcategory == null) "categoryIconPackSelections"
            else "subcategoryIconPackSelections"
        val legacy = expectedSettings.obj(legacyField).toMutableMap()
        val oldValue = legacy[preference]?.let { (it as? JsonPrimitive)?.content }
        val oldPack = oldValue?.let {
            CANONICAL_CATEGORY_ICON_PACKS.firstOrNull { prefix -> it.startsWith("$prefix:") }
                ?: "lucide"
        }
        val remembered = expectedSettings.obj(savedField).toMutableMap()
        val perPack = (remembered[preference] as? JsonObject)?.toMutableMap()
            ?: mutableMapOf()
        if (semanticKey != null) {
            perPack[pack] = JsonPrimitive(semanticKey)
            legacy[preference] = JsonPrimitive(
                if (pack == "lucide") semanticKey else "$pack:$semanticKey"
            )
        } else {
            perPack.remove(pack)
            if (oldPack == pack) legacy.remove(preference)
        }
        if (perPack.isEmpty()) remembered.remove(preference)
        else remembered[preference] = JsonObject(perPack)
        return JsonObject(expectedSettings + mapOf(
            "categoryIconPack" to JsonPrimitive(
                expectedSettings.string("categoryIconPack") ?: pack
            ),
            legacyField to JsonObject(legacy),
            savedField to JsonObject(remembered),
        ))
    }

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean =
        runCatching { document.settingsObject() == target() }.getOrDefault(false)

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val desired = target()
        if (document.settingsObject() == desired) return document
        require(document.settingsObject() == expectedSettings) {
            "Οι ρυθμίσεις εικονιδίων άλλαξαν σε άλλη συσκευή. Έλεγξε τη νέα έκδοση."
        }
        return document.withMutableState(document.state.updated("settings", desired), nowIso)
    }
}

/** Read Web's pack-specific preference first; fall back to legacy while pack is unset. */
fun canonicalExplicitNativeCategoryIcon(
    pack: String,
    kind: String,
    category: String?,
    subcategory: String?,
    saved: Map<String, String>,
    remembered: Map<String, Map<String, String>>,
    legacyPackNotSelected: Boolean,
): String? {
    if (kind !in setOf("expense", "income") || category.isNullOrBlank()) return null
    val key = kind + ":" + category +
        if (subcategory.isNullOrBlank()) "" else ":" + subcategory
    val selected = remembered[key]?.get(pack)
    if (selected != null && selected in canonicalNativeIconsForPack(pack)) return selected
    val previous = saved[key] ?: return null
    val decodedPack = CANONICAL_CATEGORY_ICON_PACKS.firstOrNull {
        previous.startsWith("$it:")
    } ?: "lucide"
    val semantic = if (decodedPack == "lucide" && !previous.startsWith("lucide:"))
        previous else previous.substringAfter(':', "")
    return semantic.takeIf {
        (legacyPackNotSelected || decodedPack == pack) &&
            it in canonicalNativeIconsForPack(if (legacyPackNotSelected) decodedPack else pack)
    }
}
