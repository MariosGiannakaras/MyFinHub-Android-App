package app.myfinhub.android.core.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * Read-only native equivalent of Web creditStatements.ts date and card configuration helpers.
 *
 * A closing or due day in 29..31 is clamped independently for every calendar month;
 * the next-cycle rule treats a purchase on closing day as belonging to the following cycle.
 * No statement is created and no canonical money event is mutated by these helpers.
 */
data class CanonicalStatementConfiguration(
    val closingDay: Int,
    val dueDay: Int,
    val boundaryRule: String,
)

fun CanonicalCard.statementConfiguration(): CanonicalStatementConfiguration? {
    val closing = statementClosingDay ?: return null
    val due = statementDueDay ?: return null
    val rule = statementBoundaryRule ?: return null
    if (kind != "credit" || closing !in 1..31 || due !in 1..31 ||
        rule !in setOf("include-closing-day", "next-cycle")) return null
    return CanonicalStatementConfiguration(closing, due, rule)
}

private fun statementDay(month: YearMonth, day: Int): LocalDate {
    require(day in 1..31) { "Billing day must be between 1 and 31." }
    return month.atDay(minOf(day, month.lengthOfMonth()))
}

fun statementCloseDateForPurchase(
    purchaseDate: LocalDate,
    closingDay: Int,
    boundaryRule: String,
): LocalDate {
    require(boundaryRule in setOf("include-closing-day", "next-cycle")) {
        "Unsupported statement boundary rule."
    }
    val month = YearMonth.from(purchaseDate)
    val currentClose = statementDay(month, closingDay)
    val inCurrent = if (boundaryRule == "include-closing-day")
        !purchaseDate.isAfter(currentClose) else purchaseDate.isBefore(currentClose)
    return if (inCurrent) currentClose else statementDay(month.plusMonths(1), closingDay)
}

fun statementOpenDateForClose(
    closeDate: LocalDate,
    closingDay: Int,
    boundaryRule: String = "include-closing-day",
): LocalDate {
    require(boundaryRule in setOf("include-closing-day", "next-cycle")) {
        "Unsupported statement boundary rule."
    }
    val previousClose = statementDay(YearMonth.from(closeDate).minusMonths(1), closingDay)
    return if (boundaryRule == "next-cycle") previousClose else previousClose.plusDays(1)
}

fun statementDueDateForClose(closeDate: LocalDate, dueDay: Int): LocalDate {
    val month = YearMonth.from(closeDate)
    val thisMonthDue = statementDay(month, dueDay)
    return if (thisMonthDue.isAfter(closeDate)) thisMonthDue
    else statementDay(month.plusMonths(1), dueDay)
}

fun canonicalCreditStatementId(cardId: String, closeDate: LocalDate): String {
    require(cardId.isNotBlank()) { "Statement requires a stable card id." }
    return "$cardId:$closeDate"
}
