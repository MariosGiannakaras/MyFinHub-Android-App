package app.myfinhub.android.core.data

import java.text.Collator
import java.util.Locale
import kotlinx.serialization.json.JsonObject

/** Web savings.ts Pay & Save / manual transfer / cash-offset reporting. */
data class CanonicalSavingsHistoryRow(
    val id: String, val date: String, val amount: Double,
    val source: String, val note: String, val origin: String,
    val fromAccountId: String? = null, val toAccountId: String? = null,
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
            tx.note.ifEmpty { "Pay & Save" },"legacy",tx.fromAccountId,tx.toAccountId)
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
            fromAccountId=e.string("fromAccountId"),toAccountId=e.string("toAccountId"),
        )
    }
    // Web savings.ts savingsHistory sorts descending by date.localeCompare and
    // ID.localeCompare. Unicode String.compareTo puts mixed-case imported IDs
    // in the wrong position within a day; use the Web default locale for IDs.
    val webIdOrder = Collator.getInstance(Locale.US)
    val rows = (legacy + events).sortedWith(Comparator { a, b ->
        val dateOrder = b.date.compareTo(a.date) // ISO date-only, locale-independent
        if (dateOrder != 0) dateOrder else webIdOrder.compare(b.id, a.id)
    })
    val sources=linkedMapOf("pay_and_save" to 0.0,"manual_transfer" to 0.0,"cash_offset" to 0.0)
    rows.forEach { sources[it.source]=(sources[it.source] ?: 0.0)+it.amount }
    return CanonicalSavingsBreakdown(rows,sources,sources.values.sum())
}

/**
 * Web savings.ts operationalMonthlyFlow and reports selectors.
 * Ledger monthlyFlow / flowBetween stay unmodified for canonical accounting.
 * Web first adjusts RAW signed expense then clamps only the public displayed
 * expense; using monthlyFlow.expense (already clamped) corrupts refund-heavy net.
 */
private fun CanonicalFinanceDocument.operationalizeFlow(
    base: CanonicalMonthlyFlow,
    start: String,
    end: String,
): CanonicalMonthlyFlow {
    var income = base.income
    var expense = base.income - base.net // recover signed raw expense before display clamping
    var saving = base.saving
    effectiveLegacyTransactions()
        .filter { it.date in start..end && payAndSave.containsMatchIn(it.note + " " + it.category.orEmpty()) }
        .forEach { tx ->
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

/** DashboardPage and ReportsPage read Web operational monthly financial totals. */
fun CanonicalFinanceDocument.canonicalOperationalMonthlyFlow(
    month: String,
    asOf: String? = null,
): CanonicalMonthlyFlow {
    val end = if (asOf != null && asOf.startsWith("$month-")) asOf else "$month-31"
    return operationalizeFlow(monthlyFlow(month, asOf), "$month-01", end)
}

/** Native 30/90-day and equal-window comparisons use the identical savings semantics. */
fun CanonicalFinanceDocument.canonicalOperationalFlowBetween(
    startInclusive: String,
    endInclusive: String,
): CanonicalMonthlyFlow {
    val base = flowBetween(startInclusive, endInclusive)
    return if (endInclusive < startInclusive) base
        else operationalizeFlow(base, startInclusive, endInclusive)
}

/** Web savings.ts savingsHistoryPresentation: genuine notes lead; generic mechanism
 * labels are shown once rather than being mistaken for user-written comments. */
data class CanonicalSavingsHistoryPresentation(
    val primary: String,
    val sourceLabel: String,
    val hasUserNote: Boolean,
)

fun canonicalSavingsHistoryPresentation(row: CanonicalSavingsHistoryRow): CanonicalSavingsHistoryPresentation {
    val sourceLabel = when (row.source) {
        "pay_and_save" -> "Pay & Save"
        "manual_transfer" -> "Μεταφορά στην αποταμίευση"
        else -> "Σύνθετη αποταμίευση"
    }
    val knownNotes = when (row.source) {
        "pay_and_save" -> listOf("Pay & Save")
        "manual_transfer" -> listOf("Μεταφορά στην άκρη", "Μεταφορά στην αποταμίευση")
        else -> listOf("Σύνθετη αποταμίευση", "Σύνθετη αποταμίευση με μετρητά")
    }
    val note = row.note.trim()
    val collator = Collator.getInstance(Locale.forLanguageTag("el-GR")).apply {
        strength = Collator.PRIMARY
    }
    val hasUserNote = note.isNotEmpty() && knownNotes.none { collator.compare(it, note) == 0 }
    return CanonicalSavingsHistoryPresentation(
        primary = if (hasUserNote) note else sourceLabel,
        sourceLabel = sourceLabel,
        hasUserNote = hasUserNote,
    )
}
