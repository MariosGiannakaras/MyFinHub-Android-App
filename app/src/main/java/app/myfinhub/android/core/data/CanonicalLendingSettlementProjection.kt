package app.myfinhub.android.core.data

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.min

/** Web lendingPlans.ts proposals are informational only; no repayment events are posted. */
data class CanonicalLendingPlannedInstallment(
    val dueDate: String,
    val amount: Double,
    val status: String = "planned",
)

data class CanonicalLendingSettlementProposal(
    val person: String,
    val asOf: String,
    val total: Double,
    val outstanding: Double,
    val schedule: List<CanonicalLendingPlannedInstallment>,
    val note: String =
        "Μόνο προγραμματισμένη επιστροφή — καμία οικονομική κίνηση ή είσπραξη δεν καταχωρίστηκε.",
)

private const val MAX_SAFE_LENDING_CENTS = 9_007_199_254_740_991L

private fun exactLendingCents(value: Double): Long {
    val cents = runCatching { moneyToCents(value) }.getOrNull()
    require(cents != null && cents in 1L..MAX_SAFE_LENDING_CENTS &&
        value.isFinite() && abs(value * 100.0 - cents.toDouble()) <= 1e-6) {
        "Χρειάζεται θετικό ποσό σε ακριβή λεπτά ευρώ."
    }
    return cents
}

private fun validLendingIsoDate(value: String): LocalDate? =
    if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value))
        runCatching { LocalDate.parse(value) }.getOrNull()
    else null

/**
 * Same Web lendingPlans.ts calendar/cent allocator for proposed AND confirmed
 * installments. A saved plan is historical intent, not a newly booked receipt,
 * and must not be clamped to today's potentially smaller actual receivable.
 */
fun canonicalLendingInstallmentSchedule(
    firstDueDate: String,
    amount: Double,
    installments: Int,
    everyMonths: Int = 1,
): List<CanonicalLendingPlannedInstallment> {
    val due = validLendingIsoDate(firstDueDate)
    require(due != null) { "Η ημερομηνία προγραμματισμένης δόσης δεν είναι έγκυρη." }
    require(installments in 1..60 && everyMonths in 1..24) {
        "Ο αριθμός και η συχνότητα των δόσεων δεν είναι έγκυρα."
    }
    val cents = exactLendingCents(amount)
    require(cents >= installments.toLong()) {
        "Κάθε προγραμματισμένη δόση πρέπει να είναι τουλάχιστον ένα λεπτό του ευρώ."
    }
    val each = cents / installments
    val remainder = cents % installments
    val firstMonth = YearMonth.from(due)
    return (0 until installments).map { index ->
        val month = firstMonth.plusMonths(index.toLong() * everyMonths)
        val date = month.atDay(min(due.dayOfMonth, month.lengthOfMonth()))
        val centsForMonth = each + if (index == installments - 1) remainder else 0L
        CanonicalLendingPlannedInstallment(date.toString(), centsToMoney(centsForMonth))
    }
}

/**
 * Pure native rendering companion for Web lendingSettlementProjection().
 * The actual outstanding must come from canonical per-person Lending rows;
 * it cannot be inferred from scenario assumptions or a payment plan.
 */
fun canonicalLendingSettlementProposal(
    person: String,
    outstanding: Double,
    asOf: String,
    firstDueDate: String,
    amount: Double,
    installments: Int,
    everyMonths: Int = 1,
): CanonicalLendingSettlementProposal {
    val name = person.trim()
    require(name.isNotEmpty()) { "Διάλεξε πρόσωπο με καταγεγραμμένη οφειλή." }
    val today = validLendingIsoDate(asOf)
    val due = validLendingIsoDate(firstDueDate)
    require(today != null && due != null && !due.isBefore(today)) {
        "Η πρώτη ημερομηνία επιστροφής πρέπει να είναι έγκυρη και όχι παλαιότερη του ελέγχου."
    }
    require(installments in 1..60 && everyMonths in 1..24) {
        "Ο αριθμός και η συχνότητα των δόσεων δεν είναι έγκυρα."
    }
    val cents = exactLendingCents(amount)
    val available = runCatching { moneyToCents(outstanding) }.getOrNull()
    require(available != null && available > 0L &&
        available <= MAX_SAFE_LENDING_CENTS) {
        "Δεν υπάρχει επαληθευμένη απαίτηση για το πρόσωπο."
    }
    require(cents <= available) {
        "Το προτεινόμενο ποσό δεν μπορεί να ξεπερνά την καταγεγραμμένη οφειλή."
    }
    require(cents >= installments.toLong()) {
        "Κάθε προτεινόμενη δόση πρέπει να είναι τουλάχιστον ένα λεπτό του ευρώ."
    }
    val schedule = canonicalLendingInstallmentSchedule(
        firstDueDate = firstDueDate, amount = amount,
        installments = installments, everyMonths = everyMonths,
    )
    return CanonicalLendingSettlementProposal(
        name, today.toString(), centsToMoney(cents),
        centsToMoney(available), schedule,
    )
}

/** Web lendingOutstandingFor() must be derived from source rows at the exact as-of. */
fun CanonicalFinanceDocument.webLendingSettlementProjection(
    person: String,
    asOf: String,
    firstDueDate: String,
    amount: Double,
    installments: Int,
    everyMonths: Int = 1,
): CanonicalLendingSettlementProposal {
    val name = person.trim()
    val available = canonicalLendingPersonRows(asOf).firstOrNull { it.person == name }
        ?.outstanding ?: 0.0
    return canonicalLendingSettlementProposal(
        person, available, asOf, firstDueDate, amount, installments, everyMonths,
    )
}
