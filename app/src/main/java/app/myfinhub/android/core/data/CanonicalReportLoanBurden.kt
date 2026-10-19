package app.myfinhub.android.core.data

import java.text.Collator
import java.util.Locale
import kotlin.math.min
import kotlinx.serialization.json.JsonObject

/** Web Reports reportLoanBurden uses only long-term active payment obligations. */
data class CanonicalReportLoanRow(
    val id: String,
    val name: String,
    val amount: Double,
    val outstanding: Double,
    val remainingInstallments: Int,
)
data class CanonicalReportLoanBurden(
    val total: Double,
    val count: Int,
    val rows: List<CanonicalReportLoanRow>,
)

/**
 * Exact Web activeLongTermLoanObligations / reportLoanBurden selection: explicit
 * longTermRecurring takes precedence over installments >= 12, self-loans are
 * excluded from the automatic long-term fallback, and next amount is capped at
 * remaining debt. Identity-preserving: loan overrides are applied per seed row.
 */
fun CanonicalFinanceDocument.canonicalReportLoanBurden(asOf: String? = null): CanonicalReportLoanBurden {
    val overrides=state.obj("loanOverrides")
    val definitions=seed.array("loans").mapNotNull { it as? JsonObject }
        .map { raw -> (overrides[raw.string("id").orEmpty()] as? JsonObject) ?: raw } +
        state.array("customLoans").mapNotNull { it as? JsonObject }
    val balances=canonicalLoanBalanceRows(asOf)
    val collator=Collator.getInstance(Locale.forLanguageTag("el-GR"))
    data class Staged(val result: CanonicalReportLoanRow, val day: Int?, val index: Int)
    val staged=definitions.mapIndexedNotNull { index, raw ->
        val balance=balances.getOrNull(index) ?: return@mapIndexedNotNull null
        val enabled=raw.bool("longTermRecurring")
            ?: ((raw.number("installments") ?: 0.0)>=12 && !raw.isOwnLoan())
        if (!enabled) return@mapIndexedNotNull null
        val amount=min(balance.remaining,balance.installment.coerceAtLeast(0.0))
        if (!amount.isFinite() || amount<=0.005 || !balance.remaining.isFinite() ||
            balance.remaining<=0.005 || balance.remainingInstallments<=0) return@mapIndexedNotNull null
        Staged(
            CanonicalReportLoanRow(
                balance.id,balance.name,amount,balance.remaining,balance.remainingInstallments,
            ),balance.typicalPaymentDay,index,
        )
    }.sortedWith(Comparator { a,b ->
        val day=(a.day ?: 99).compareTo(b.day ?: 99)
        if(day!=0) day else {
            val name=collator.compare(a.result.name,b.result.name)
            if(name!=0) name else a.index.compareTo(b.index)
        }
    })
    val rows=staged.map(Staged::result)
    return CanonicalReportLoanBurden(rows.sumOf { it.amount },rows.size,rows)
}
