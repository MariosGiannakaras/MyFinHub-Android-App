package app.myfinhub.android.core.data

import java.time.YearMonth

/** Web reports.ts subcategoryTotals and categoryMomentum, entirely read-only. */
data class CanonicalReportCategoryValue(val name: String, val value: Double)

data class CanonicalReportCategoryMomentum(
    val name: String,
    val value: Double,
    val previous: Double,
    val change: Double?,
)

/**
 * Web groups "category › subcategory" and accounts for both expense and
 * refund impact. Split parts are counted individually and never re-added
 * through the parent event. Internal transfers and savings have no expense.
 */
fun CanonicalFinanceDocument.canonicalReportSubcategoryTotals(
    month: String,
    asOf: String? = null,
): List<CanonicalReportCategoryValue> {
    val totals=linkedMapOf<String,Double>()
    fun categoryPath(category: String?, subcategory: String?): String =
        if (category.isNullOrEmpty()) "Άλλο"
        else if (subcategory.isNullOrEmpty()) category
        else category + " › " + subcategory
    fun add(category: String?, subcategory: String?, amount: Double) {
        val name=categoryPath(category,subcategory)
        totals[name]=(totals[name] ?: 0.0)+amount
    }
    fun inMonth(date: String) = date.startsWith(month + "-") &&
        (asOf == null || month != asOf.take(7) || date <= asOf)
    effectiveLegacyTransactions().forEach { tx ->
        if (!inMonth(tx.date)) return@forEach
        val impact=legacyFlowImpact(tx)
        // Web reports.ts uses refund *instead of* its already-negative expense
        // impact. Adding both counts a returned purchase twice.
        if (impact.refund != 0.0) {
            add(tx.category,tx.subcategory,-impact.refund)
        } else if (impact.expense != 0.0) {
            add(tx.category,tx.subcategory,impact.expense)
        }
    }
    canonicalEvents().forEach { event ->
        if (!inMonth(event.date)) return@forEach
        if (event.parts.isNotEmpty()) {
            event.parts.forEach { part ->
                when (part.kind ?: "expense") {
                    "expense" -> add(part.category,part.subcategory,part.amount)
                    "refund" -> add(part.category,part.subcategory,-part.amount)
                }
            }
        } else {
            val impact=eventFlowImpact(event)
            if (impact.refund != 0.0) {
                add(event.category,event.subcategory,-impact.refund)
            } else if (impact.expense != 0.0) {
                add(event.category,event.subcategory,impact.expense)
            }
        }
    }
    // Kotlin's stable sort keeps the Web's insertion order for equal totals.
    return totals.map { (key,amount) ->
        CanonicalReportCategoryValue(key,amount.coerceAtLeast(0.0))
    }.filter { it.value > 0.005 }.sortedByDescending { it.value }
}

fun CanonicalFinanceDocument.canonicalReportCategoryMomentum(
    month: String,
    limit: Int = 10,
    asOf: String? = null,
): List<CanonicalReportCategoryMomentum> {
    val previousMonth=YearMonth.parse(month).minusMonths(1).toString()
    val previous=canonicalReportSubcategoryTotals(previousMonth)
        .associate { it.name to it.value }
    return canonicalReportSubcategoryTotals(month,asOf)
        .take(limit.coerceAtLeast(0))
        .map { row ->
            val previousValue=previous[row.name] ?: 0.0
            val change=when {
                previousValue == 0.0 && row.value == 0.0 -> 0.0
                previousValue == 0.0 -> null
                else -> (row.value-previousValue)/kotlin.math.abs(previousValue)
            }
            CanonicalReportCategoryMomentum(row.name,row.value,previousValue,change)
        }
}
