package app.myfinhub.android.core.data

import java.text.Collator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlinx.serialization.json.JsonObject

/** Web budgets.ts canonical categorized spending, independent of clamped insights totals. */
data class CanonicalBudgetProgress(
    val id: String,
    val month: String,
    val scope: String,
    val category: String?,
    val limit: Double,
    val used: Double,
    val rawUsed: Double,
    val remaining: Double,
    val ratio: Double,
    val alertThreshold: Double,
    val status: String,
)

fun CanonicalFinanceDocument.categoryBudgetSpending(month: String, asOf: String? = null): Map<String, Double> {
    val totals = linkedMapOf<String, Double>()
    fun include(date: String): Boolean =
        date.startsWith("$month-") && (asOf == null || month != asOf.take(7) || date <= asOf)
    fun add(category: String?, amount: Double) {
        val key = category?.trim().orEmpty()
        if (key.isEmpty() || !amount.isFinite() || abs(amount) <= 0.000001) return
        totals[key] = (totals[key] ?: 0.0) + amount
    }
    fun addParts(parts: Iterable<JsonObject>) {
        parts.forEach { part ->
            val amount = abs(part.number("amount") ?: 0.0)
            when (part.string("kind") ?: "expense") {
                "expense" -> add(part.string("category"), amount)
                "refund" -> add(part.string("category"), -amount)
            }
        }
    }
    effectiveLegacyTransactions().filter { include(it.date) }.forEach { tx ->
        val decision = reviewDecision(tx.id)
        if (decision?.string("status") == "confirmed" && decision.string("semanticKind") == "split") {
            addParts(decision.array("parts").mapNotNull { it as? JsonObject })
        } else {
            val expense = when {
                decision?.string("status") == "confirmed" &&
                    decision.string("semanticKind") == "refund" -> -tx.amount
                decision?.string("status") == "confirmed" &&
                    decision.string("semanticKind") in setOf(
                        "saving_cash_offset", "withdrawal", "transfer", "card_payment", "reconciliation"
                    ) -> 0.0
                tx.type == "expense" -> tx.amount
                else -> 0.0
            }
            add(tx.category, expense)
        }
    }
    state.array("events").mapNotNull { it as? JsonObject }
        .filter { include(it.string("date").orEmpty()) }.forEach { event ->
            val parts = event.array("parts").mapNotNull { it as? JsonObject }
            if (parts.isNotEmpty()) {
                addParts(parts)
            } else {
                val amount = event.number("amount") ?: 0.0
                val expense = when (event.string("kind")) {
                    "expense", "card_purchase" -> amount
                    "refund" -> -amount
                    else -> 0.0
                }
                add(event.string("category"), expense)
            }
        }
    return totals
}

fun CanonicalFinanceDocument.webBudgetProgress(month: String, asOf: String? = null): List<CanonicalBudgetProgress> {
    val spending = categoryBudgetSpending(month, asOf)
    val total = spending.values.sum()
    val locale = Collator.getInstance(Locale.forLanguageTag("el-GR"))
    return state.array("budgets").mapNotNull { it as? JsonObject }
        .filter { it.string("month") == month && (it.number("amount") ?: 0.0) > 0.0 }
        .sortedWith(Comparator { a,b ->
            (a.string("scope").orEmpty().compareTo(b.string("scope").orEmpty())).takeIf { it != 0 }
                ?: locale.compare(a.string("category").orEmpty(),b.string("category").orEmpty()).takeIf { it != 0 }
                ?: a.string("id").orEmpty().compareTo(b.string("id").orEmpty())
        })
        .map { raw ->
            val scope = raw.string("scope").orEmpty()
            val category = raw.string("category")
            val rawUsed = if (scope == "overall") total else spending[category.orEmpty()] ?: 0.0
            val used = max(0.0, rawUsed)
            val limit = max(0.0, raw.number("amount") ?: 0.0)
            val ratio = if (limit > 0.0) used / limit else 0.0
            val rawThreshold = raw.number("alertThreshold")
            // Web uses 0.5–0.99 fractional thresholds. Read older Android whole-percent values safely.
            val normalized = when {
                rawThreshold == null || !rawThreshold.isFinite() -> 0.8
                rawThreshold > 1.0 -> rawThreshold / 100.0
                else -> rawThreshold
            }.coerceIn(0.5,0.99)
            CanonicalBudgetProgress(
                id = raw.string("id").orEmpty(), month = month, scope = scope,
                category = category, limit = limit, used = used, rawUsed = rawUsed,
                remaining = limit - used, ratio = ratio, alertThreshold = normalized,
                status = when { ratio > 1.0 -> "exceeded"; ratio >= normalized -> "near"; else -> "ok" },
            )
        }
}
