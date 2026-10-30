package app.myfinhub.android.feature.money

import java.time.LocalDate

/** Read-only source integrity: no inferred interest rate from corrupt or ambiguous imports. */
internal sealed interface CreditRateSelection {
    data object Missing : CreditRateSelection
    data object Invalid : CreditRateSelection
    data class Selected(val record: CardAnnualRateVersion) : CreditRateSelection
}

/**
 * Date-only canonical active rate. Missing or future rows never create a
 * historic effective rate. An invalid latest value or duplicate latest day
 * must not silently fall back to an earlier lower rate.
 */
internal fun selectedCreditRate(
    versions: List<CardAnnualRateVersion>,
    today: LocalDate,
): CreditRateSelection {
    val eligible = versions.filter { row ->
        val effective = runCatching { LocalDate.parse(row.effectiveFrom) }.getOrNull()
        effective != null && !effective.isAfter(today)
    }
    if (eligible.isEmpty()) return if (versions.any {
        runCatching { LocalDate.parse(it.effectiveFrom) }.getOrNull() == null
    }) CreditRateSelection.Invalid else CreditRateSelection.Missing

    val latestDay = eligible.maxOf { it.effectiveFrom }
    val latest = eligible.filter { it.effectiveFrom == latestDay }
    if (latest.size != 1 || !isValidCreditAnnualPercent(latest.single().annualPercent)) {
        return CreditRateSelection.Invalid
    }
    return CreditRateSelection.Selected(latest.single())
}

internal fun isValidCreditAnnualPercent(value: Double): Boolean =
    value.isFinite() && value in 0.0..100.0

/** All historical versions remain visible, but no NaN/Infinity percent is formatted. */
internal fun displayCreditRateHistory(version: CardAnnualRateVersion): String =
    if (isValidCreditAnnualPercent(version.annualPercent) &&
        runCatching { LocalDate.parse(version.effectiveFrom) }.getOrNull() != null
    ) "${version.effectiveFrom}: ${version.annualPercent}%" else "Μη έγκυρη ιστορική τιμή επιτοκίου"
