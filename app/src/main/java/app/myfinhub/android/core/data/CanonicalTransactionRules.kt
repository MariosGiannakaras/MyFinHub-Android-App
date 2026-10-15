package app.myfinhub.android.core.data

import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Pure Web transactionRules.ts precedence and matching; never rewrites existing events. */
private val transactionRuleLocale = Locale.forLanguageTag("el-GR")
private val ruleEventKinds = setOf("expense", "refund", "income", "card_purchase")

private fun normalizeRuleText(value: String?): String =
    value.orEmpty().trim().lowercase(transactionRuleLocale)

private fun CanonicalFinanceDocument.matchingCanonicalRules(event: JsonObject): List<JsonObject> {
    val kind = event.string("kind").orEmpty()
    if (kind !in ruleEventKinds) return emptyList()
    val scope = when (event.string("source")) {
        "migration" -> "imported"
        "review" -> "review"
        else -> "manual"
    }
    val note = normalizeRuleText(event.string("note"))
    val accountId = event.string("accountId")?.takeIf(String::isNotEmpty)
        ?: event.string("fromAccountId").orEmpty()
    return state.array("transactionRules")
        .filterIsInstance<JsonObject>()
        .filter { rule ->
            if (rule.bool("enabled") != true ||
                rule.array("scopes").none { (it as? JsonPrimitive)?.contentOrNull == scope }
            ) return@filter false
            val action = rule.obj("action")
            if (kind == "income" &&
                (!action.string("category").isNullOrEmpty() || !action.string("subcategory").isNullOrEmpty())
            ) return@filter false
            val match = rule.obj("match")
            val description = normalizeRuleText(match.string("description"))
            val merchant = normalizeRuleText(match.string("merchant"))
            val account = match.string("accountId").orEmpty().trim()
            if (description.isNotEmpty()) {
                val matched = if (match.string("mode") == "equals") note == description
                    else note.contains(description)
                if (!matched) return@filter false
            }
            if (merchant.isNotEmpty() && !note.contains(merchant)) return@filter false
            if (account.isNotEmpty() && accountId != account) return@filter false
            description.isNotEmpty() || merchant.isNotEmpty() || account.isNotEmpty()
        }
        .sortedWith(compareBy<JsonObject> { it.number("priority") ?: Double.POSITIVE_INFINITY }
            .thenBy { it.string("id").orEmpty() })
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
    val fallback = (defaults.firstOrNull() as? JsonPrimitive)?.contentOrNull ?: "Άλλο"
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
