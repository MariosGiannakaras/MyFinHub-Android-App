package app.myfinhub.android.core.data

import kotlin.math.abs
import kotlinx.serialization.json.JsonObject

/**
 * Separate source of subcategory spending for Web subcategory budgets.
 * Never sum this with parent-category spending (the same expense is in both projections).
 */
fun CanonicalFinanceDocument.canonicalSubcategoryBudgetSpending(
    month: String,
    asOf: String? = null,
): Map<String, Double> {
    val cents = linkedMapOf<String, Long>()
    fun inRange(date: String): Boolean =
        date.startsWith(month + "-") && (asOf == null || asOf.take(7) != month || date <= asOf)
    fun add(category: String?, subcategory: String?, amount: Double) {
        val cat = webBudgetCategoryKey(category.orEmpty())
        val sub = webBudgetCategoryKey(subcategory.orEmpty())
        if (cat.isBlank() || sub.isBlank() || !amount.isFinite()) return
        val centDelta = runCatching { moneyToCents(amount) }.getOrNull() ?: return
        val key = cat + "\u001f" + sub
        cents[key] = Math.addExact(cents[key] ?: 0L, centDelta)
    }
    fun splitParts(rows: List<JsonObject>) {
        rows.forEach { part ->
            val amount = abs(part.number("amount") ?: 0.0)
            when (part.string("kind") ?: "expense") {
                "expense" -> add(part.string("category"), part.string("subcategory"), amount)
                "refund" -> add(part.string("category"), part.string("subcategory"), -amount)
            }
        }
    }
    effectiveLegacyTransactions().filter { inRange(it.date) }.forEach { tx ->
        val decision = state.obj("reviewDecisions")[tx.id] as? JsonObject
        if (decision?.string("status") == "confirmed" &&
            decision.string("semanticKind") == "split") {
            splitParts(decision.array("parts").filterIsInstance<JsonObject>())
        } else {
            val expense = when {
                decision?.string("status") == "confirmed" &&
                    decision.string("semanticKind") == "refund" -> -tx.amount
                decision?.string("status") == "confirmed" &&
                    decision.string("semanticKind") in setOf(
                        "saving_cash_offset", "withdrawal", "transfer",
                        "card_payment", "reconciliation"
                    ) -> 0.0
                tx.type == "expense" -> tx.amount
                else -> 0.0
            }
            add(tx.category, tx.subcategory, expense)
        }
    }
    state.array("events").filterIsInstance<JsonObject>()
        .filter { inRange(it.string("date").orEmpty()) }.forEach { event ->
            val parts = event.array("parts").filterIsInstance<JsonObject>()
            if (parts.isNotEmpty()) splitParts(parts)
            else {
                val amount = event.number("amount") ?: 0.0
                when (event.string("kind")) {
                    "expense", "card_purchase" ->
                        add(event.string("category"), event.string("subcategory"), amount)
                    "refund" ->
                        add(event.string("category"), event.string("subcategory"), -amount)
                }
            }
        }
    return cents.mapValues { (_, value) -> centsToMoney(value) }
}
