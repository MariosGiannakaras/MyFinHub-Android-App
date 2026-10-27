package app.myfinhub.android.core.data

import java.time.LocalDate

/**
 * A persisted Web state.lendingPlans record is a CONFIRMED SCHEDULE, not a
 * receipt. Calendar dates can pass without any corresponding payment event.
 */
data class CanonicalConfirmedLendingSchedule(
    val installments: List<CanonicalLendingPlannedInstallment>,
    val asOfDate: String?,
    val pastDueDates: Int,
)

/**
 * Pure, fail-closed confirmed schedule from a separately validated imported
 * record. Never infer paid status or reduce amounts using actual outstanding:
 * real repayments are recorded through the existing canonical event pipeline.
 */
fun CanonicalConfirmedLendingPlanView.canonicalConfirmedSchedule(
    asOf: String?,
): CanonicalConfirmedLendingSchedule? {
    if (validationError != null) return null
    val due = firstDueDate ?: return null
    val nominal = total ?: return null
    val count = installments ?: return null
    val cadence = everyMonths ?: return null
    val today = if (asOf.isNullOrBlank()) null else {
        if (!asOf.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return null
        runCatching { LocalDate.parse(asOf) }.getOrNull() ?: return null
    }
    val schedule = runCatching {
        canonicalLendingInstallmentSchedule(due, nominal, count, cadence)
    }.getOrNull() ?: return null
    val elapsed = if (today == null) 0 else
        schedule.count { LocalDate.parse(it.dueDate).isBefore(today) }
    return CanonicalConfirmedLendingSchedule(
        installments = schedule,
        asOfDate = today?.toString(),
        pastDueDates = elapsed,
    )
}
