package app.myfinhub.android.core.data

import java.text.Collator
import java.util.Locale
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
    val historicalMatchCount: Int = 0,
    val matchDescription: String = "",
    val matchMerchant: String = "",
    val matchAccountId: String = "",
    val matchMode: String = "contains",
    val actionCategory: String = "",
    val actionSubcategory: String = "",
    val actionNote: String = "",
    val scopes: List<String> = listOf("manual"),
    val createdAt: String = "",
)

fun CanonicalFinanceDocument.canonicalTransactionRuleOverview(): List<CanonicalRuleOverview> {
    val settings = settingsObject()
    val accountNames = canonicalAccounts().filter { it.kind != "credit" }
        .associate { it.id to it.name }
    val categoryTree = settings.array("expenseCategoryTree")
        .mapNotNull { it as? JsonObject }
    // Web categoryTree uses the configured taxonomy when present, otherwise
    // the flat category set. Never silently merge both historical taxonomies.
    val names = if (categoryTree.isNotEmpty())
        categoryTree.mapNotNull { it.string("name") }.toSet()
    else settings.array("expenseCategories")
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
    val scopeNames = mapOf(
        "manual" to "χειροκίνητη καταχώριση",
        "imported" to "εισαγωγή",
        "review" to "επιβεβαίωση από έλεγχο",
    )
    val idCollator = Collator.getInstance(Locale.forLanguageTag("el-GR"))
    return state.array("transactionRules").mapNotNull { it as? JsonObject }
        .sortedWith(Comparator { a,b ->
            val priority = (a.number("priority") ?: Double.POSITIVE_INFINITY)
                .compareTo(b.number("priority") ?: Double.POSITIVE_INFINITY)
            if (priority != 0) priority
            else idCollator.compare(a.string("id").orEmpty(),b.string("id").orEmpty())
        })
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
                category != null && category !in names ->
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
                historicalMatchCount = canonicalHistoricalRuleMatchCount(rule),
                matchDescription = desc.orEmpty(),
                matchMerchant = merchant.orEmpty(),
                matchAccountId = account.orEmpty(),
                matchMode = match.string("mode") ?: "contains",
                actionCategory = category.orEmpty(),
                actionSubcategory = subcategory.orEmpty(),
                actionNote = note.orEmpty(),
                scopes = scopes,
                createdAt = rule.string("createdAt").orEmpty(),
            )
        }
}
