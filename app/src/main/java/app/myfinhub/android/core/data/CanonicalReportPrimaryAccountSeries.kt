package app.myfinhub.android.core.data

import java.time.YearMonth

/** Web reports.ts primaryAccountSeries with explicit IDs and an as-of-aware month end. */
data class CanonicalReportPrimaryAccountPoint(
    val monthIso: String,
    val cutoffDateIso: String,
    val balances: Map<String,Double>,
)

/**
 * Web's primaryAccountSeries does not infer IDs from a fixed institution.
 * The caller supplies canonical payroll and savings account IDs from the
 * shared account selector; other accounts/cash/reserve stay excluded.
 * Archived months end on their actual final calendar day. Only the month
 * matching asOf is cut off at that date. Missing balances count as zero.
 */
fun CanonicalFinanceDocument.canonicalReportPrimaryAccountSeries(
    month: String,
    accountIds: List<String>,
    count: Int = 6,
    asOf: String? = null,
): List<CanonicalReportPrimaryAccountPoint> {
    val ending=YearMonth.parse(month)
    val ids=accountIds.distinct().filter(String::isNotBlank)
    return (0 until count.coerceIn(0,60)).map { index ->
        val current=ending.minusMonths((count-1-index).toLong())
        val key=current.toString()
        val cutoff=if (asOf != null && key == asOf.take(7)) asOf
            else current.atEndOfMonth().toString()
        val balances=accountBalances(cutoff)
        CanonicalReportPrimaryAccountPoint(
            monthIso=key,
            cutoffDateIso=cutoff,
            balances=ids.associateWith { balances[it] ?: 0.0 },
        )
    }
}
