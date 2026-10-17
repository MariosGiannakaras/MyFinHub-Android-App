package app.myfinhub.android.core.data

/**
 * Web ContextualQuickAdd recurring payment: create a regular, linked expense;
 * does not mutate a separate recurring balance or invent a recurring EventKind.
 * A user may pay an overridden or custom item via any available noncredit account.
 */
fun createCanonicalRecurringPaymentMutation(
    document: CanonicalFinanceDocument,
    recurringId: String,
    date: String,
    accountId: String,
    eventId: String,
    nowIso: String,
    amount: Double? = null,
    note: String? = null,
): CanonicalFinanceMutation {
    require(recurringId.isNotBlank()) { "Διάλεξε υπαρκτό πάγιο." }
    val item = document.allCanonicalRecurringItems().firstOrNull { it.id == recurringId }
        ?: throw IllegalArgumentException("Το πάγιο δεν είναι πλέον διαθέσιμο.")
    val draft = CanonicalTransactionEntryDraft(
        kind = "expense", date = date,
        amount = amount ?: item.amount,
        note = note?.trim()?.takeIf(String::isNotBlank) ?: "Πληρωμή: ${item.name}",
        category = item.category,
        accountId = accountId,
        recurringId = recurringId,
    )
    return createCanonicalTransactionEntryMutation(document, draft, eventId, nowIso)
}
