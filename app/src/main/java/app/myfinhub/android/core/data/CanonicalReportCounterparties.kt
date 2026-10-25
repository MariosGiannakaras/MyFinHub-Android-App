package app.myfinhub.android.core.data

import java.text.Collator
import java.util.Locale

/** Web reports.ts reportExpenseCounterparties: positive net-flow expense groups. */
data class CanonicalExpenseCounterparty(
    val title: String,
    val category: String,
    val amount: Double,
    val share: Double?,
    val count: Int,
    val lastDate: String,
)

fun CanonicalFinanceDocument.canonicalReportExpenseCounterparties(
    month: String,
    asOf: String? = null,
    limit: Int = 5,
): List<CanonicalExpenseCounterparty> {
    data class Acc(
        val title: String,
        val category: String,
        var amount: Double,
        var count: Int,
        var lastDate: String,
    )
    val grouped = linkedMapOf<String,Acc>()
    val locale = Locale.forLanguageTag("el-GR")
    fun categoryPath(category: String?, subcategory: String?): String =
        if (category.isNullOrEmpty()) "Άλλο"
        else if (subcategory.isNullOrEmpty()) category else "$category › $subcategory"
    fun include(date: String) =
        date.startsWith("$month-") && (asOf == null || month != asOf.take(7) || date <= asOf)
    fun add(label: String, category: String, amount: Double, date: String) {
        if (!amount.isFinite() || amount <= 0.005) return
        // Web reports.ts uses JS fallback truthiness:
        // nonempty whitespace-only imported category strings count as present.
        val safeTitle = label.trim().ifEmpty { category.ifEmpty { "Συναλλαγή" } }
        val safeCategory = category.ifEmpty { "Χωρίς κατηγορία" }
        val key = safeTitle.lowercase(locale) + "|" + safeCategory.lowercase(locale)
        val prior = grouped[key]
        if (prior != null) {
            prior.amount += amount
            prior.count += 1
            if (date > prior.lastDate) prior.lastDate = date
        } else grouped[key] = Acc(safeTitle,safeCategory,amount,1,date)
    }
    effectiveLegacyTransactions().forEach { tx ->
        if (!include(tx.date)) return@forEach
        val amount = legacyFlowImpact(tx).expense
        val category = categoryPath(tx.category,tx.subcategory)
        add(tx.note.ifEmpty { category },category,amount,tx.date)
    }
    canonicalEvents().forEach { event ->
        if (!include(event.date)) return@forEach
        val amount = eventFlowImpact(event).expense
        val partCategories = event.parts
            .filter { (it.kind ?: "expense") == "expense" }
            .map { categoryPath(it.category,it.subcategory) }.distinct()
        val category = when {
            partCategories.size == 1 -> partCategories[0]
            partCategories.size > 1 -> "Διαχωρισμένη συναλλαγή"
            else -> categoryPath(event.category,event.subcategory)
        }
        add(event.note.ifEmpty { category },category,amount,event.date)
    }
    val collator=Collator.getInstance(locale)
    val rows=grouped.values.sortedWith(Comparator { a,b ->
        val amount=b.amount.compareTo(a.amount)
        if (amount != 0) amount else {
            val recency=b.lastDate.compareTo(a.lastDate)
            if (recency != 0) recency else collator.compare(a.title,b.title)
        }
    })
    val gross=rows.sumOf { it.amount }
    return rows.take(limit.coerceAtLeast(0)).map {
        CanonicalExpenseCounterparty(
            title=it.title,category=it.category,
            amount=it.amount,share=if(gross>0) it.amount/gross else null,
            count=it.count,lastDate=it.lastDate,
        )
    }
}
