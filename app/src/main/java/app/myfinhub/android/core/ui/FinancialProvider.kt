package app.myfinhub.android.core.ui

import java.text.Normalizer
import java.util.Locale

/** Web financialProviders.ts known registry; unknown server providers remain unknown. */
enum class FinancialProvider(
    val key: String,
    val institutionLabel: String,
    val cardLabel: String,
) {
    PIRAEUS("piraeus", "Τράπεζα Πειραιώς", "Piraeus"),
    ALPHA("alpha", "Alpha Bank", "ALPHA BANK"),
    NATIONAL("national", "Εθνική Τράπεζα", "NBG"),
    EUROBANK("eurobank", "Eurobank", "Eurobank"),
    REVOLUT("revolut", "Revolut", "Revolut"),
    VIVA("viva", "Viva.com", "Viva.com"),
    PAYZY("payzy", "Magenta Pay", "payzy"),
    PAYPAL("paypal", "PayPal", "PayPal"),
}

private fun normalizeProviderName(raw: String): String = Normalizer
    .normalize(raw.lowercase(Locale.forLanguageTag("el-GR")), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")

/** Web financialProviderId: accent-insensitive aliases for all eight canonical providers. */
fun financialProviderId(vararg values: String?): String {
    val text = normalizeProviderName(values.filterNotNull().filter(String::isNotBlank).joinToString(" "))
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

/** Web providerId is authoritative; do not relabel unknown IDs by a guessed account name. */
fun accountFinancialProviderId(
    providerId: String?,
    provider: String?,
    accountId: String,
    accountName: String,
): String = providerId?.trim()?.takeIf(String::isNotBlank)
    ?: financialProviderId(provider, accountId, accountName)

/**
 * Exact providerId takes precedence over fallback bank/name matching.
 * Null is safe: no fake third-party logo or bank identity for unknown providers.
 */
fun financialProvider(id: String?, name: String?, explicitProviderId: String? = null): FinancialProvider? {
    val key = if (!explicitProviderId.isNullOrBlank()) explicitProviderId.trim()
        else financialProviderId(id, name)
    return FinancialProvider.entries.firstOrNull { it.key == key }
}

/**
 * A present canonical providerId is authoritative. Do not show a known-bank
 * mark when a custom/unknown provider happens to contain another bank's
 * name in its descriptive account label.
 */
fun financialProviderForAccount(
    accountId: String,
    displayOrInstitution: String?,
    providerId: String?,
): FinancialProvider? = financialProvider(accountId,displayOrInstitution,providerId)

/** Card bankId is an explicit canonical ownership key when nonblank. */
fun financialProviderForCard(bankId: String, nickname: String?): FinancialProvider? =
    financialProvider(bankId,nickname,bankId.takeIf(String::isNotBlank))

fun financialAccountDisplayName(
    accountId: String,
    canonicalName: String,
    kind: String,
    providerId: String? = null,
): String {
    if (kind == "cash" || accountId == "cash") return canonicalName.ifBlank { "Μετρητά" }
    val provider = financialProvider(accountId, canonicalName, providerId)
    val trimmed = canonicalName.trim()
    val withoutInstitution = when (provider) {
        FinancialProvider.PIRAEUS -> trimmed
            .replace(Regex("^(Πειραιώς|Piraeus|Τράπεζα Πειραιώς)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .takeUnless { trimmed.equals("Τράπεζα Πειραιώς", ignoreCase = true) }.orEmpty()
        FinancialProvider.REVOLUT -> trimmed.takeUnless { it.equals("Revolut", ignoreCase = true) }.orEmpty()
        FinancialProvider.ALPHA -> trimmed.takeUnless { it.equals("Alpha Bank", ignoreCase = true) }.orEmpty()
        FinancialProvider.NATIONAL -> trimmed.takeUnless {
            it.equals("Εθνική Τράπεζα", ignoreCase = true) || it.equals("NBG", ignoreCase = true)
        }.orEmpty()
        FinancialProvider.EUROBANK -> trimmed.takeUnless { it.equals("Eurobank", ignoreCase = true) }.orEmpty()
        FinancialProvider.PAYZY -> trimmed.takeUnless {
            it.contains("payzy", ignoreCase = true) || it.equals("Magenta Pay", ignoreCase = true)
        }.orEmpty()
        FinancialProvider.VIVA -> trimmed.takeUnless { it.contains("viva", ignoreCase = true) }.orEmpty()
        FinancialProvider.PAYPAL -> trimmed.takeUnless { it.equals("PayPal", ignoreCase = true) }.orEmpty()
        null -> trimmed
    }
    if (withoutInstitution.isNotBlank()) return withoutInstitution
    return when (kind) {
        "savings" -> "Αποταμιευτικός"
        "bank" -> "Λογαριασμός"
        else -> trimmed.ifBlank { "Λογαριασμός" }
    }
}
