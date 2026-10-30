package app.myfinhub.android.feature.insights

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/** Display-only guards: imported source events and finance totals are never changed. */
internal const val INVALID_INSIGHT_MONEY = "Μη έγκυρο ποσό"
internal const val INVALID_INSIGHT_RATIO = "Μη έγκυρο ποσοστό"

internal fun insightsCurrency(value: Double): String =
    if (value.isFinite()) {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
    } else INVALID_INSIGHT_MONEY

/** A nonfinite/unrepresentable ratio must not become 0%, Int.MAX_VALUE% or 2147483647%. */
internal fun insightsRatioLabel(
    ratio: Double?,
    signed: Boolean = false,
    missing: String = "Χωρίς προηγούμενη βάση",
): String {
    if (ratio == null) return missing
    if (!ratio.isFinite()) return INVALID_INSIGHT_RATIO
    val percent = ratio * 100.0
    if (!percent.isFinite() || percent < Int.MIN_VALUE.toDouble() ||
        percent > Int.MAX_VALUE.toDouble()) return INVALID_INSIGHT_RATIO
    val rounded = percent.roundToInt()
    return (if (signed && rounded > 0) "+" else "") + rounded + "%"
}

internal fun insightsCategoryShareLabel(share: Float): String =
    if (!share.isFinite() || share < 0f || share > 1f) INVALID_INSIGHT_RATIO
    else insightsRatioLabel(share.toDouble())

internal fun insightsTrendCanDraw(points: List<TrendPoint>): Boolean =
    points.all { it.income.isFinite() && it.expense.isFinite() &&
        it.income >= 0.0 && it.expense >= 0.0 }
