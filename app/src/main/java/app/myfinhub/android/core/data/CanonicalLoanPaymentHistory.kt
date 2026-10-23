package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonObject

/** Web loans.ts loanPaymentEvents: dated source events only, no synthetic installment rows. */
data class CanonicalLoanPaymentHistoryRow(
    val loanId: String,
    val eventId: String,
    val date: String,
    val note: String,
    val amount: Double,
    val accountId: String?,
    val installmentCount: Int,
    val eventKind: String,
)

private val returnNotePattern = Regex(
    "^(?:ΕΠΙΣΤΡΟΦΗ|RETURN)(?:\\s|:|$)", RegexOption.IGNORE_CASE,
)

/**
 * Attach a payment history only where one effective loan row uniquely owns its ID.
 * Multiple legacy rows with the same ID are preserved in the Loans list, but their
 * linked events must not be attributed to an arbitrary first match.
 */
fun CanonicalFinanceDocument.canonicalLoanPaymentHistory(
    asOf: String? = null,
): Map<String, List<CanonicalLoanPaymentHistoryRow>> {
    val overrides = state.obj("loanOverrides")
    val effectiveSeed = seed.array("loans").mapNotNull { it as? JsonObject }.map { original ->
        (overrides[original.string("id").orEmpty()] as? JsonObject) ?: original
    }
    val custom = state.array("customLoans").mapNotNull { it as? JsonObject }
    val uniqueLoans = (effectiveSeed + custom)
        .filter { !it.string("id").isNullOrBlank() }
        .groupBy { it.string("id").orEmpty() }
        .filterValues { it.size == 1 }
        .mapValues { (_, rows) -> rows.single().isOwnLoan() }
    val events = state.array("events").mapNotNull { it as? JsonObject }
    return events.mapNotNull { raw ->
        val id = raw.string("loanId") ?: return@mapNotNull null
        val self = uniqueLoans[id] ?: return@mapNotNull null
        val date = raw.string("date").orEmpty()
        if (date.isBlank() || (asOf != null && date > asOf)) return@mapNotNull null
        val kind = raw.string("kind").orEmpty()
        val note = raw.string("note").orEmpty()
        if (self && (kind != "transfer" ||
                !returnNotePattern.containsMatchIn(note.trim()))) return@mapNotNull null
        val amount = raw.number("amount")?.takeIf(Double::isFinite) ?: 0.0
        val installments = raw.number("installmentCount")?.let { value ->
            if (value.isFinite() && value > 0 && value % 1.0 == 0.0)
                value.toInt().coerceAtLeast(1)
            else 1
        } ?: 1
        val account = raw.string("accountId")
            ?.takeIf(String::isNotBlank)
            ?: raw.string("fromAccountId")?.takeIf(String::isNotBlank)
            ?: raw.string("toAccountId")?.takeIf(String::isNotBlank)
        Triple(id, raw.string("createdAt").orEmpty(),
            CanonicalLoanPaymentHistoryRow(
                loanId = id, eventId = raw.string("id").orEmpty(),
                date = date, note = note, amount = amount,
                accountId = account, installmentCount = installments,
                eventKind = kind,
            ))
    }.groupBy { it.first }
        .mapValues { (_, rows) ->
            rows.sortedWith(
                compareByDescending<Triple<String, String, CanonicalLoanPaymentHistoryRow>> {
                    it.third.date
                }.thenByDescending { it.second },
            ).map { it.third }
        }
}
