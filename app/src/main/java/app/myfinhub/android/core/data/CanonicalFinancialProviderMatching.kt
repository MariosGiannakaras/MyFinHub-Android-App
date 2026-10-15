package app.myfinhub.android.core.data

import java.text.Normalizer
import java.util.Locale

/**
 * Matches Web financialProviders.ts: authoritative explicit providerId, falling back to
 * normalized legacy provider/id/name heuristics in the same precedence order.
 * The account ID prefix alone is never an authorization decision.
 */
internal fun CanonicalAccount.matchesFinancialProvider(targetProviderId: String): Boolean {
    val target = targetProviderId.trim()
    if (target.isEmpty()) return false
    val accountProvider = providerId?.trim().orEmpty().ifEmpty {
        financialProviderIdFromLegacyMetadata(provider, id, name)
    }
    return accountProvider == target
}

private fun financialProviderIdFromLegacyMetadata(vararg values: String?): String {
    val joined = values.filterNotNull().filter(String::isNotBlank).joinToString(" ")
    val text = Normalizer.normalize(joined, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.forLanguageTag("el-GR"))
    return when {
        "piraeus" in text || "πειραι" in text -> "piraeus"
        "alpha" in text -> "alpha"
        "national" in text || "nbg" in text || "εθνικ" in text -> "national"
        "eurobank" in text -> "eurobank"
        "revolut" in text -> "revolut"
        "viva" in text -> "viva"
        "payzy" in text || "magenta pay" in text -> "payzy"
        "paypal" in text -> "paypal"
        else -> ""
    }
}
