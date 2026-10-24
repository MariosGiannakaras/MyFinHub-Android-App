package app.myfinhub.android.core.data

import java.text.Collator
import java.text.Normalizer
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull


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

/** Web budgets.ts identities(): the stable category/subcategory ID retains
 * historical labels, so renamed postings still count against the same budget.
 * Lookup is read-only; no financial history or taxonomy identity is rewritten.
 */
private fun CanonicalFinanceDocument.budgetIdentityKeys(
    row: JsonObject,
    field: String,
): Set<String> {
    val identityField = when (field) {
        "category" -> "categoryIdentityId"
        "subcategory" -> "subcategoryIdentityId"
        else -> error("Unsupported budget identity field")
    }
    val identityId = row.string(identityField)
    val identity = identityId?.let {
        settingsObject().obj("categoryIdentities")[it] as? JsonObject
    }
    val labels = buildList {
        row.string(field)?.let(::add)
        identity?.string("label")?.let(::add)
        identity?.array("aliases")?.forEach { alias ->
            (alias as? JsonPrimitive)?.contentOrNull?.let(::add)
        }
    }
    return labels.map(::webBudgetCategoryKey).filter(String::isNotEmpty).toSet()
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
    val projectedDue: Double = 0.0,
    val projectedUsed: Double = used,
    val projectedRemaining: Double = remaining,
    val projectedStatus: String = status,
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
            // Web budgets.ts uses the reconciled category when present,
            // even for a legacy posting whose original category remains immutable.
            add(decision?.string("category")?.takeIf(String::isNotBlank) ?: tx.category, expense)
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
    val commitments = asOf?.let { canonicalBudgetProjectedCommitments(month, it) }.orEmpty()
    val total = spending.values.sum()
    val locale = Collator.getInstance(Locale.forLanguageTag("el-GR"))
    val all = state.array("budgets").filterIsInstance<JsonObject>()
    val budgets = all.filter { it.string("month") == month && (it.number("amount") ?: 0.0) > 0.0 }

    fun usage(row: JsonObject, period: String, cutoff: String?): Double {
        val cats = if (period == month && cutoff == asOf) normalizedSpending
            else categoryBudgetSpending(period, cutoff).entries
                .groupBy { webBudgetCategoryKey(it.key) }
                .mapValues { (_, values) -> values.sumOf { it.value } }
        val categoryKeys = budgetIdentityKeys(row, "category")
        return when (row.string("scope")) {
            "overall" -> cats.values.sum()
            "category" -> cats.entries.sumOf { (key, amount) ->
                if (key in categoryKeys) amount else 0.0
            }
            "subcategory" -> {
                val subs = if (period == month && cutoff == asOf) subSpending
                    else canonicalSubcategoryBudgetSpending(period, cutoff)
                val subKeys = budgetIdentityKeys(row, "subcategory")
                subs.entries.sumOf { (pair, amount) ->
                    val names = pair.split('\u001f', limit = 2)
                    if (names.size == 2 && names[0] in categoryKeys && names[1] in subKeys)
                        amount else 0.0
                }
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
        if (budgetIdentityKeys(current, "category").intersect(
                budgetIdentityKeys(older, "category")
            ).isEmpty()) return false
        return scope != "subcategory" ||
            budgetIdentityKeys(current, "subcategory").intersect(
                budgetIdentityKeys(older, "subcategory")
            ).isNotEmpty()
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
            // Web monthlyBudgets orders by scope, category, then ID.
            // A subcategory label must not reorder same-category rows before ID.
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
        val budgetCategories = budgetIdentityKeys(row, "category")
        val budgetSubcategories = budgetIdentityKeys(row, "subcategory")
        val matchingCommitments = commitments.filter { item ->
            when (scope) {
                "overall" -> true
                "category" -> webBudgetCategoryKey(item.category) in budgetCategories
                "subcategory" -> webBudgetCategoryKey(item.category) in budgetCategories &&
                    webBudgetCategoryKey(item.subcategory.orEmpty()) in budgetSubcategories
                else -> false
            }
        }
        val projectedDue = centsToMoney(matchingCommitments.fold(0L) { cents, item ->
            Math.addExact(cents, moneyToCents(item.amount))
        })
        val projectedUsed = max(0.0, rawUsed + projectedDue)
        val projectedRatio = if (limit > 0.0) projectedUsed / limit else 0.0
        // Web budgets.ts budgetProgress reads the persisted value directly:
        // Math.min(.99, Math.max(.5, Number(alertThreshold ?? .8))).
        // The write/editor compatibility layer may accept legacy percent input;
        // never reinterpret an imported budget's raw threshold at read time.
        val rawThreshold = row.number("alertThreshold")
        val threshold = (rawThreshold ?: 0.8).coerceIn(0.5, 0.99)
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
            projectedDue = projectedDue,
            projectedUsed = projectedUsed,
            projectedRemaining = limit - projectedUsed,
            projectedStatus = when {
                projectedRatio > 1.0 -> "exceeded"
                projectedRatio >= threshold -> "near"
                else -> "ok"
            },
        )
    }
}
