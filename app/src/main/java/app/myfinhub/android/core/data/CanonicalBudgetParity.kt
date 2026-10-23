package app.myfinhub.android.core.data

import java.text.Collator
import java.text.Normalizer
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlinx.serialization.json.JsonObject


/** Matches Web categories.ts categoryKey: Greek accent folding and whitespace normalization. */
fun webBudgetCategoryKey(value: String): String =
    Normalizer.normalize(
        value.trim().replace(Regex("\\s+"), " "),
        Normalizer.Form.NFD,
    ).replace(Regex("\\p{M}"), "")
        .lowercase(Locale.forLanguageTag("el-GR"))

/** JS encodeURIComponent over UTF-8, including its ! ~ * ' ( ) safe set. */
private fun encodeWebBudgetPart(value: String): String {
    val hex = "0123456789ABCDEF"
    return buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val n = byte.toInt() and 255
            if (n in 65..90 || n in 97..122 || n in 48..57 ||
                n in listOf(45, 95, 46, 33, 126, 42, 39, 40, 41)
            ) append(n.toChar()) else {
                append('%')
                append(hex[n shr 4])
                append(hex[n and 15])
            }
        }
    }
}

fun webOverallBudgetStableId(month: String): String {
    require(runCatching { java.time.YearMonth.parse(month).toString() == month }.getOrDefault(false))
    return "budget:" + month + ":overall"
}

fun webCategoryBudgetStableId(month: String, category: String): String {
    require(runCatching { java.time.YearMonth.parse(month).toString() == month }.getOrDefault(false))
    val key = webBudgetCategoryKey(category)
    require(key.isNotEmpty())
    return "budget:" + month + ":" + encodeWebBudgetPart(key)
}

fun webSubcategoryBudgetStableId(
    month: String,
    category: String,
    subcategory: String,
    categoryIdentityId: String? = null,
    subcategoryIdentityId: String? = null,
): String {
    require(runCatching { java.time.YearMonth.parse(month).toString() == month }.getOrDefault(false))
    val cat = categoryIdentityId?.takeIf(String::isNotBlank) ?: webBudgetCategoryKey(category)
    val sub = subcategoryIdentityId?.takeIf(String::isNotBlank) ?: webBudgetCategoryKey(subcategory)
    require(cat.isNotEmpty() && sub.isNotEmpty())
    return "budget:" + month + ":sub:" + encodeWebBudgetPart(cat) + ":" + encodeWebBudgetPart(sub)
}

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
    val subcategory: String? = null,
    val carryover: Boolean = false,
    val carried: Double = 0.0,
    val baseLimit: Double = limit,
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
        val decision = state.obj("reviewDecisions")[tx.id] as? JsonObject
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
    val normalizedSpending = spending.entries.groupBy { webBudgetCategoryKey(it.key) }
        .mapValues { (_, values) -> values.sumOf { it.value } }
    val subSpending = canonicalSubcategoryBudgetSpending(month, asOf)
    val total = spending.values.sum()
    val locale = Collator.getInstance(Locale.forLanguageTag("el-GR"))
    val all = state.array("budgets").filterIsInstance<JsonObject>()
    val budgets = all.filter { it.string("month") == month && (it.number("amount") ?: 0.0) > 0.0 }

    fun usage(row: JsonObject, period: String, cutoff: String?): Double {
        val cats = if (period == month && cutoff == asOf) normalizedSpending
            else categoryBudgetSpending(period, cutoff).entries
                .groupBy { webBudgetCategoryKey(it.key) }
                .mapValues { (_, values) -> values.sumOf { it.value } }
        return when (row.string("scope")) {
            "overall" -> cats.values.sum()
            "category" -> cats[webBudgetCategoryKey(row.string("category").orEmpty())] ?: 0.0
            "subcategory" -> {
                val subs = if (period == month && cutoff == asOf) subSpending
                    else canonicalSubcategoryBudgetSpending(period, cutoff)
                val cat = webBudgetCategoryKey(row.string("category").orEmpty())
                val sub = webBudgetCategoryKey(row.string("subcategory").orEmpty())
                subs[cat + "\u001f" + sub] ?: 0.0
            }
            else -> 0.0
        }
    }

    fun sameBudget(current: JsonObject, older: JsonObject): Boolean {
        val scope = current.string("scope")
        if (scope != older.string("scope")) return false
        if (scope == "overall") return true
        val newId = current.string("categoryIdentityId")?.takeIf(String::isNotBlank)
        val oldId = older.string("categoryIdentityId")?.takeIf(String::isNotBlank)
        if (newId != null && oldId != null) {
            return newId == oldId && (scope != "subcategory" ||
                current.string("subcategoryIdentityId") == older.string("subcategoryIdentityId"))
        }
        if (webBudgetCategoryKey(current.string("category").orEmpty()) !=
            webBudgetCategoryKey(older.string("category").orEmpty())) return false
        return scope != "subcategory" ||
            webBudgetCategoryKey(current.string("subcategory").orEmpty()) ==
                webBudgetCategoryKey(older.string("subcategory").orEmpty())
    }

    fun carry(previous: JsonObject, depth: Int): Long {
        if (previous.bool("carryover") != true || depth > 24) return 0L
        val prevMonth = runCatching {
            YearMonth.parse(previous.string("month").orEmpty()).minusMonths(1).toString()
        }.getOrNull() ?: return 0L
        val candidates = all.filter { it.string("month") == prevMonth &&
            (it.number("amount") ?: 0.0) > 0.0 }
        val parent = candidates.firstOrNull { sameBudget(previous, it) } ?: return 0L
        val amount = parent.number("amount")?.takeIf(Double::isFinite)?.let {
            runCatching { moneyToCents(it) }.getOrNull()
        } ?: return 0L
        val used = runCatching { moneyToCents(max(0.0, usage(parent, prevMonth, null))) }
            .getOrDefault(0L)
        val brought = carry(parent, depth + 1)
        return (amount + brought - used).coerceAtLeast(0L)
    }

    return budgets.sortedWith(Comparator { a, b ->
        (a.string("scope").orEmpty().compareTo(b.string("scope").orEmpty())).takeIf { it != 0 }
            ?: locale.compare(a.string("category").orEmpty(), b.string("category").orEmpty()).takeIf { it != 0 }
            ?: locale.compare(a.string("subcategory").orEmpty(), b.string("subcategory").orEmpty()).takeIf { it != 0 }
            ?: a.string("id").orEmpty().compareTo(b.string("id").orEmpty())
    }).map { row ->
        val scope = row.string("scope").orEmpty()
        val base = max(0.0, row.number("amount") ?: 0.0)
        val carried = centsToMoney(carry(row, 0))
        val limit = base + carried
        val rawUsed = when (scope) {
            "overall" -> total
            else -> usage(row, month, asOf)
        }
        val used = max(0.0, rawUsed)
        val ratio = if (limit > 0.0) used / limit else 0.0
        val rawThreshold = row.number("alertThreshold")
        val threshold = when {
            rawThreshold == null || !rawThreshold.isFinite() -> 0.8
            rawThreshold > 1.0 -> rawThreshold / 100.0
            else -> rawThreshold
        }.coerceIn(0.5, 0.99)
        CanonicalBudgetProgress(
            id = row.string("id").orEmpty(),
            month = month,
            scope = scope,
            category = row.string("category"),
            subcategory = row.string("subcategory"),
            carryover = row.bool("carryover") == true,
            carried = carried,
            baseLimit = base,
            limit = limit,
            used = used,
            rawUsed = rawUsed,
            remaining = limit - used,
            ratio = ratio,
            alertThreshold = threshold,
            status = when {
                ratio > 1.0 -> "exceeded"
                ratio >= threshold -> "near"
                else -> "ok"
            },
        )
    }
}
