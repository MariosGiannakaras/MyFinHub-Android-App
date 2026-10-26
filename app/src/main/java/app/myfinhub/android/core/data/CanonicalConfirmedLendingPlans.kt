package app.myfinhub.android.core.data

import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.math.abs
import kotlinx.serialization.json.JsonObject

/**
 * Web state.lendingPlans are owner-confirmed *schedules*, not receipts.
 * Never alter balance, create repayments or infer that any planned installment cleared.
 */
data class CanonicalConfirmedLendingPlanView(
    val person: String,
    val id: String? = null,
    val total: Double? = null,
    val firstDueDate: String? = null,
    val installments: Int? = null,
    val everyMonths: Int? = null,
    val confirmedAt: String? = null,
    val validationError: String? = null,
)

private fun planDate(raw: String?): LocalDate? {
    if (!raw.orEmpty().matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return null
    return runCatching { LocalDate.parse(raw) }.getOrNull()
}

/**
 * Read only the actual persisted plans. Malformed and duplicate same-person
 * imported rows surface explicit uncertainty rather than appearing as a
 * confirmed promise of future income. Do not collapse distinct physical rows
 * to a random first ID.
 */
fun CanonicalFinanceDocument.canonicalConfirmedLendingPlanRows():
    List<CanonicalConfirmedLendingPlanView> {
    val rows = state.array("lendingPlans").mapNotNull { it as? JsonObject }
        .filter { !it.string("person").isNullOrBlank() }
    val counts = rows.groupingBy { it.string("person").orEmpty().trim() }.eachCount()
    return rows.map { raw ->
        val person = raw.string("person").orEmpty().trim()
        val id = raw.string("id").orEmpty().trim()
        val asOf = planDate(raw.string("asOf"))
        val due = planDate(raw.string("firstDueDate"))
        val amount = raw.number("total")
        val cents = amount?.let { runCatching { moneyToCents(it) }.getOrNull() }
        val count = raw.number("installments")
        val interval = raw.number("everyMonths")
        val confirmedAt = raw.string("confirmedAt").orEmpty()
        val valid = counts[person] == 1 &&
            id.isNotEmpty() && id.length <= 160 &&
            asOf != null && due != null && !due.isBefore(asOf) &&
            amount != null && amount.isFinite() && cents != null &&
            cents > 0L && cents <= 9_007_199_254_740_991L &&
            abs(amount * 100 - cents.toDouble()) <= 1e-6 &&
            count != null && count.isFinite() && count % 1.0 == 0.0 &&
            count in 1.0..60.0 &&
            interval != null && interval.isFinite() && interval % 1.0 == 0.0 &&
            interval in 1.0..24.0 &&
            runCatching { OffsetDateTime.parse(confirmedAt) }.isSuccess
        if (!valid) CanonicalConfirmedLendingPlanView(
            person = person,
            validationError = if (counts[person] != 1)
                "Πολλαπλά επιβεβαιωμένα προγράμματα για το ίδιο πρόσωπο. Απαιτείται έλεγχος."
            else "Το εισαγόμενο πρόγραμμα επιστροφής χρειάζεται έλεγχο στοιχείων.",
        ) else CanonicalConfirmedLendingPlanView(
            person = person, id = id, total = centsToMoney(cents!!),
            firstDueDate = due!!.toString(), installments = count!!.toInt(),
            everyMonths = interval!!.toInt(), confirmedAt = confirmedAt,
        )
    }
}
