package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonObject

/** Web savings.ts Pay & Save / manual transfer / cash-offset reporting. */
data class CanonicalSavingsHistoryRow(
    val id: String, val date: String, val amount: Double,
    val source: String, val note: String, val origin: String,
)
data class CanonicalSavingsBreakdown(
    val rows: List<CanonicalSavingsHistoryRow>,
    val bySource: Map<String, Double>,
    val total: Double,
)

private val payAndSave = Regex("\\bpay\\s*&?\\s*save\\b", RegexOption.IGNORE_CASE)
private val transferMarker = Regex("μεταφορ", RegexOption.IGNORE_CASE)

fun CanonicalFinanceDocument.canonicalSavingsBreakdown(month: String, asOf: String? = null): CanonicalSavingsBreakdown {
    fun included(date: String) = date.startsWith("$month-") &&
        (asOf == null || month != asOf.take(7) || date <= asOf)
    val legacy = effectiveLegacyTransactions().filter {
        included(it.date) && payAndSave.containsMatchIn(it.note + " " + it.category.orEmpty())
    }.map { tx ->
        CanonicalSavingsHistoryRow(tx.id,tx.date,tx.amount,"pay_and_save",
            tx.note.ifBlank { "Pay & Save" },"legacy")
    }
    val events = state.array("events").mapNotNull { it as? JsonObject }.filter {
        it.string("kind")=="saving_cash_offset" && included(it.string("date").orEmpty())
    }.map { e ->
        val note=e.string("note").orEmpty()
        val rawSource=e.string("savingSource")
        val source=if (rawSource in setOf("pay_and_save","manual_transfer","cash_offset")) rawSource!!
            else if (payAndSave.containsMatchIn(note)) "pay_and_save"
            else if (transferMarker.containsMatchIn(note)) "manual_transfer"
            else "cash_offset"
        CanonicalSavingsHistoryRow(
            id=e.string("id").orEmpty(),date=e.string("date").orEmpty(),
            amount=e.number("savingAmount") ?: (e.number("amount") ?: 0.0),
            source=source,note=note,origin="event",
        )
    }
    val rows=(legacy+events).sortedWith(compareByDescending<CanonicalSavingsHistoryRow> { it.date }.thenByDescending { it.id })
    val sources=linkedMapOf("pay_and_save" to 0.0,"manual_transfer" to 0.0,"cash_offset" to 0.0)
    rows.forEach { sources[it.source]=(sources[it.source] ?: 0.0)+it.amount }
    return CanonicalSavingsBreakdown(rows,sources,sources.values.sum())
}

/**
 * Web savings.ts operationalMonthlyFlow, used by Dashboard selectors and reports.
 * Legacy Pay & Save is a savings mechanism, not an operating expense/income.
 * Retain the base monthlyFlow API for raw event/ledger accounting and do not
 * mutate classification or the user's stored finance document.
 */
fun CanonicalFinanceDocument.canonicalOperationalMonthlyFlow(
    month: String,
    asOf: String? = null,
): CanonicalMonthlyFlow {
    val base = monthlyFlow(month, asOf)
    var income = base.income
    var expense = base.expense
    var saving = base.saving
    effectiveLegacyTransactions().forEach { tx ->
        if (!tx.date.startsWith("$month-") ||
            (asOf != null && month == asOf.take(7) && tx.date > asOf) ||
            !payAndSave.containsMatchIn(tx.note + " " + tx.category.orEmpty())
        ) return@forEach
        val decision = state.obj("reviewDecisions")[tx.id] as? JsonObject
        if (decision?.string("status") == "confirmed" &&
            decision.string("semanticKind") == "saving_cash_offset"
        ) return@forEach
        val original = legacyFlowImpact(tx)
        income -= original.income
        expense -= original.expense
        saving += tx.amount
    }
    return CanonicalMonthlyFlow(
        income = income.coerceAtLeast(0.0),
        expense = expense.coerceAtLeast(0.0),
        saving = saving,
        refunds = base.refunds,
        net = income - expense,
    )
}
