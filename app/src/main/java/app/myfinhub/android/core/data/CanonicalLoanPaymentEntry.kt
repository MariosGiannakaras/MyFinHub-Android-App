package app.myfinhub.android.core.data

import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.json.JsonObject

/** Loan identity in Web ContextualQuickAdd: seed/override first, custom after it. */
internal fun CanonicalFinanceDocument.canonicalLoanDefinition(id: String): JsonObject? {
    val overrides = state.obj("loanOverrides")
    val seeded = seed.array("loans").mapNotNull { it as? JsonObject }
        .firstOrNull { it.string("id") == id }
    if (seeded != null) return (overrides[id] as? JsonObject) ?: seeded
    return state.array("customLoans").mapNotNull { it as? JsonObject }
        .firstOrNull { it.string("id") == id }
}

data class CanonicalLoanInstallmentPaymentPlan(
    val loanId: String,
    val count: Int,
    val firstInstallment: Int,
    val lastInstallment: Int,
    val amount: Double,
)

/** Web loans.ts loanInstallmentPaymentPlan; available before event creation. */
fun CanonicalFinanceDocument.canonicalLoanInstallmentPaymentPlan(
    loanId: String, requestedCount: Int, asOf: String,
): CanonicalLoanInstallmentPaymentPlan? {
    val loan = canonicalLoanDefinition(loanId) ?: return null
    if (loan.isOwnLoan()) return null
    val row = canonicalLoanBalanceRows(asOf).firstOrNull { it.id == loanId } ?: return null
    if (row.remainingInstallments <= 0 || row.remaining <= 0.0) return null
    val count = min(row.remainingInstallments, max(1, requestedCount))
    val paidCount = (loan.number("installments") ?: 0.0).toInt() - row.remainingInstallments
    val nominal = max(0.0, row.installment) * count
    val amount = min(row.remaining, nominal)
    if (moneyToCents(amount) <= 0) return null
    return CanonicalLoanInstallmentPaymentPlan(
        loanId, count, paidCount + 1, paidCount + count, amount,
    )
}

/**
 * Web ContextualQuickAdd loan payment semantics routed through the SAME strict
 * createCanonicalTransactionEntryMutation / AppendCanonicalEvent pipeline.
 * Does not create an invented "loan_payment" EventKind or an extra finance store.
 */
fun createCanonicalLoanPaymentMutation(
    document: CanonicalFinanceDocument,
    loanId: String,
    date: String,
    accountId: String,
    eventId: String,
    nowIso: String,
    requestedInstallments: Int = 1,
    selfLoanAmount: Double? = null,
    savingsAccountId: String? = null,
): CanonicalFinanceMutation {
    require(loanId.isNotBlank()) { "Επίλεξε υπαρκτό δάνειο." }
    val loan = document.canonicalLoanDefinition(loanId)
        ?: throw IllegalArgumentException("Το δάνειο δεν είναι πλέον διαθέσιμο.")
    val name = loan.string("name").orEmpty().ifBlank { loanId }
    val draft = if (loan.isOwnLoan()) {
        val target = savingsAccountId ?: document.canonicalAccounts()
            .firstOrNull(CanonicalAccount::isSavingsAccount)?.id
        CanonicalTransactionEntryDraft(
            kind = "transfer", date = date, amount = selfLoanAmount,
            note = "ΕΠΙΣΤΡΟΦΗ: $name", fromAccountId = accountId,
            toAccountId = target, loanId = loanId,
        )
    } else {
        val plan = document.canonicalLoanInstallmentPaymentPlan(
            loanId, requestedInstallments, date,
        ) ?: throw IllegalArgumentException("Δεν υπάρχουν διαθέσιμες δόσεις.")
        val liability = loan.string("accountingMode") == "liability-repayment"
        CanonicalTransactionEntryDraft(
            kind = if (liability) "card_payment" else "expense",
            date = date, amount = plan.amount,
            note = "Δόση: $name",
            category = if (liability) null else "Δόσεις / δάνεια",
            accountId = if (liability) null else accountId,
            fromAccountId = if (liability) accountId else null,
            loanId = loanId, installmentCount = plan.count,
        )
    }
    return createCanonicalTransactionEntryMutation(document, draft, eventId, nowIso)
}
