package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Canonical read-only Web TransactionRulesWorkspace identity, ordering and status. */
data class CanonicalRuleOverview(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val priority: Double,
    val condition: String,
    val action: String,
    val scopeLabel: String,
    val invalidReason: String?,
)

fun CanonicalFinanceDocument.canonicalTransactionRuleOverview(): List<CanonicalRuleOverview> {
    val settings = settingsObject()
    val accountNames = canonicalAccounts().filter { it.kind != "credit" }
        .associate { it.id to it.name }
    val categoryTree = settings.array("expenseCategoryTree")
        .mapNotNull { it as? JsonObject }
    val names = (settings.array("expenseCategories")
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull } +
        categoryTree.mapNotNull { it.string("name") }).toSet()
    val scopeNames = mapOf(
        "manual" to "χειροκίνητη καταχώριση",
        "imported" to "εισαγωγή",
        "review" to "επιβεβαίωση από έλεγχο",
    )
    return state.array("transactionRules").mapNotNull { it as? JsonObject }
        .sortedWith(compareBy<JsonObject> { it.number("priority") ?: Double.POSITIVE_INFINITY }
            .thenBy { it.string("id").orEmpty() })
        .map { rule ->
            val match = rule.obj("match")
            val action = rule.obj("action")
            val desc = match.string("description")?.takeIf(String::isNotBlank)
            val merchant = match.string("merchant")?.takeIf(String::isNotBlank)
            val account = match.string("accountId")?.trim()?.takeIf(String::isNotBlank)
            val conditions = buildList {
                desc?.let {
                    add(if (match.string("mode") == "equals")
                        "η περιγραφή είναι ακριβώς «" + it + "»"
                    else "η περιγραφή περιέχει «" + it + "»")
                }
                merchant?.let { add("περιέχει επίσης «" + it + "»") }
                account?.let { add("ο λογαριασμός είναι " + (accountNames[it] ?: it)) }
            }
            val category = action.string("category")?.takeIf(String::isNotBlank)
            val subcategory = action.string("subcategory")?.takeIf(String::isNotBlank)
            val note = action.string("note")?.takeIf(String::isNotBlank)
            val effects = buildList {
                category?.let { add("κατηγορία " + it) }
                subcategory?.let { add("υποκατηγορία " + it) }
                note?.let { add("σχόλιο «" + it + "» αν είναι κενό") }
            }
            val scopes = rule.array("scopes")
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val scopeLabel = if (scopes.toSet().containsAll(scopeNames.keys))
                "κάθε νέα υποστηριζόμενη κίνηση"
            else scopes.mapNotNull(scopeNames::get).joinToString(", ")
            val warning = when {
                account != null && account !in accountNames ->
                    "Ο λογαριασμός της συνθήκης δεν είναι πλέον διαθέσιμος."
                category != null && names.isNotEmpty() && category !in names ->
                    "Η κατηγορία της ενέργειας δεν είναι πλέον διαθέσιμη."
                subcategory != null && category == null ->
                    "Η υποκατηγορία δεν έχει πλέον συνδεδεμένη κατηγορία."
                category != null && subcategory != null &&
                    categoryTree.any { it.string("name") == category } &&
                    categoryTree.filter { it.string("name") == category }
                        .flatMap { it.array("subcategories") }
                        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        .none { it == subcategory } ->
                    "Η υποκατηγορία της ενέργειας δεν είναι πλέον διαθέσιμη σε αυτή την κατηγορία."
                else -> null
            }
            CanonicalRuleOverview(
                id = rule.string("id").orEmpty(),
                name = rule.string("name").orEmpty().ifBlank { rule.string("id").orEmpty() },
                enabled = rule.bool("enabled") == true,
                priority = rule.number("priority") ?: Double.POSITIVE_INFINITY,
                condition = conditions.joinToString(" και ").ifBlank { "λείπει συνθήκη" },
                action = effects.joinToString(" · ").ifBlank { "λείπει ενέργεια" },
                scopeLabel = scopeLabel,
                invalidReason = warning,
            )
        }
}
