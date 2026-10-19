package app.myfinhub.android.core.data

import java.text.Collator
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Pure Web transactionRules.ts precedence and matching; never rewrites existing events. */
private val transactionRuleLocale = Locale.forLanguageTag("el-GR")
private val ruleEventKinds = setOf("expense", "refund", "income", "card_purchase")

private fun normalizeRuleText(value: String?): String =
    value.orEmpty().trim().lowercase(transactionRuleLocale)

/** Web transactionRuleMatchingEvents forces only this rule enabled for history previews. */
internal fun canonicalRuleMatchesEvent(
    rule: JsonObject,
    event: JsonObject,
    ignoreEnabled: Boolean = false,
): Boolean {
    val kind = event.string("kind").orEmpty()
    if (kind !in ruleEventKinds || (!ignoreEnabled && rule.bool("enabled") != true))
        return false
    val scope = when (event.string("source")) {
        "migration" -> "imported"
        "review" -> "review"
        else -> "manual"
    }
    if (rule.array("scopes").none { (it as? JsonPrimitive)?.contentOrNull == scope })
        return false
    val action = rule.obj("action")
    if (kind == "income" &&
        (!action.string("category").isNullOrEmpty() || !action.string("subcategory").isNullOrEmpty())
    ) return false
    val match = rule.obj("match")
    val note = normalizeRuleText(event.string("note"))
    val description = normalizeRuleText(match.string("description"))
    val merchant = normalizeRuleText(match.string("merchant"))
    val account = match.string("accountId").orEmpty().trim()
    val accountId = event.string("accountId")?.takeIf(String::isNotEmpty)
        ?: event.string("fromAccountId").orEmpty()
    if (description.isNotEmpty()) {
        val matched = if (match.string("mode") == "equals") note == description
            else note.contains(description)
        if (!matched) return false
    }
    if (merchant.isNotEmpty() && !note.contains(merchant)) return false
    if (account.isNotEmpty() && accountId != account) return false
    return description.isNotEmpty() || merchant.isNotEmpty() || account.isNotEmpty()
}

/**
 * Web transactionRuleMatchingEvents counts only state.events, irrespective
 * of rule.enabled, by previewing this one rule as enabled. Never applies
 * any rule to historic activity or includes seeded legacy transactions.
 */
fun CanonicalFinanceDocument.canonicalHistoricalRuleMatchCount(rule: JsonObject): Int =
    state.array("events").filterIsInstance<JsonObject>().count { event ->
        canonicalRuleMatchesEvent(rule, event, ignoreEnabled = true)
    }

private fun CanonicalFinanceDocument.matchingCanonicalRules(event: JsonObject): List<JsonObject> {
    val idCollator = Collator.getInstance(transactionRuleLocale)
    return state.array("transactionRules")
        .filterIsInstance<JsonObject>()
        .filter { rule -> canonicalRuleMatchesEvent(rule, event) }
        // Web uses String.localeCompare for equal priorities, not UTF-16
        // ordinal sorting (which misorders accented Greek and mixed-case IDs).
        .sortedWith(Comparator { a, b ->
            val priority = (a.number("priority") ?: Double.POSITIVE_INFINITY)
                .compareTo(b.number("priority") ?: Double.POSITIVE_INFINITY)
            if (priority != 0) priority
            else idCollator.compare(a.string("id").orEmpty(), b.string("id").orEmpty())
        })
}

/** Web previewTransactionRules: matching rule IDs sorted by priority then stable ID. */
fun CanonicalFinanceDocument.canonicalMatchingTransactionRuleIds(event: JsonObject): List<String> =
    matchingCanonicalRules(event).mapNotNull { it.string("id") }

/** Pure Web applyTransactionRules; caller must invoke only at a freshly submitted event boundary. */
fun CanonicalFinanceDocument.applyCanonicalTransactionRules(event: JsonObject): JsonObject {
    val winner = matchingCanonicalRules(event).firstOrNull() ?: return event
    val action = winner.obj("action")
    val kind = event.string("kind").orEmpty()
    val settings = state.obj("settings")
    val defaults = settings.array(if (kind == "income") "incomeCategories" else "expenseCategories")
    // Web expenseCategories[0] || 'Άλλο': an empty historical first
    // category is falsey, not an explicit default that blocks matching rules.
    val fallback = (defaults.firstOrNull() as? JsonPrimitive)?.contentOrNull
        ?.takeIf(String::isNotEmpty) ?: "Άλλο"
    val categoryIsDefault = event.string("category").isNullOrEmpty() ||
        event.string("category") == fallback
    val additions = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
    if (kind != "income" && categoryIsDefault) {
        action.string("category")?.takeIf(String::isNotEmpty)?.let {
            additions["category"] = JsonPrimitive(it)
        }
        if (event.string("subcategory").isNullOrEmpty()) {
            action.string("subcategory")?.takeIf(String::isNotEmpty)?.let {
                additions["subcategory"] = JsonPrimitive(it)
            }
        }
    }
    if (event.string("note").orEmpty().trim().isEmpty()) {
        action.string("note")?.takeIf(String::isNotEmpty)?.let {
            additions["note"] = JsonPrimitive(it)
        }
    }
    return if (additions.isEmpty()) event else JsonObject(event + additions)
}
