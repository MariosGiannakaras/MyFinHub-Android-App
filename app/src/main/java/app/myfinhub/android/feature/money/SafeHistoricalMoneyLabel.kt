package app.myfinhub.android.feature.money

import java.text.NumberFormat
import java.util.Locale

/**
 * A corrupted imported number is never plausible financial information.
 * Keep the same Greek currency formatting for finite historical values.
 * No rounding/normalization of canonical FinanceData and no financial writes.
 */
internal fun safeHistoricalMoneyLabel(value: Double): String =
    if (value.isFinite()) {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
    } else {
        "Μη έγκυρο ποσό"
    }
