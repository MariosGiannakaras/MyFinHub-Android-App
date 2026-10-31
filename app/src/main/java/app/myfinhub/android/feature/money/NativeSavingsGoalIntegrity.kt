package app.myfinhub.android.feature.money

/** Source duplicate/blank IDs must never resolve to a first-match mutating goal. */
internal fun canonicalSavingsGoalIdUnique(id: String, rows: List<SavingsGoalItem>): Boolean =
    id.isNotBlank() && rows.count { it.id == id } == 1

/** Invalid imported progress is never a plausible 0%; valid overfunded goals stay >100%. */
internal fun safeSavingsGoalProgressPercent(value: Double): Int? =
    value.takeIf { it.isFinite() && it >= 0.0 && it <= Int.MAX_VALUE.toDouble() }?.toInt()

/** Real monthly target accepts only configured [0, 100] percent. */
internal fun safeSavingsMonthlyTargetPercent(rate: Double): Int? =
    rate.takeIf { it.isFinite() && it >= 0.0 && it <= 1.0 }
        ?.let { kotlin.math.round(it * 100.0).toInt() }
