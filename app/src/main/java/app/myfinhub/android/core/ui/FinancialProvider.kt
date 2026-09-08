package app.myfinhub.android.core.ui

import java.util.Locale

enum class FinancialProvider(
    val key: String,
    val institutionLabel: String,
    val cardLabel: String,
) {
    PIRAEUS("piraeus", "Τράπεζα Πειραιώς", "Piraeus"),
    REVOLUT("revolut", "Revolut", "Revolut"),
    ALPHA("alpha", "Alpha Bank", "ALPHA BANK"),
    PAYZY("payzy", "payzy by COSMOTE", "payzy"),
    VIVA("viva", "Viva.com", "Viva.com"),
}

fun financialProvider(id: String?, name: String?): FinancialProvider? {
    val text = "${id.orEmpty()} ${name.orEmpty()}".lowercase(Locale.ROOT)
    return when {
        "piraeus" in text || "πειραι" in text -> FinancialProvider.PIRAEUS
        "revolut" in text -> FinancialProvider.REVOLUT
        "alpha" in text -> FinancialProvider.ALPHA
        "payzy" in text -> FinancialProvider.PAYZY
        "viva" in text -> FinancialProvider.VIVA
        else -> null
    }
}

fun financialAccountDisplayName(accountId: String, canonicalName: String, kind: String): String {
    if (kind == "cash" || accountId == "cash") return canonicalName.ifBlank { "Μετρητά" }
    val provider = financialProvider(accountId, canonicalName)
    val trimmed = canonicalName.trim()
    val withoutInstitution = when (provider) {
        FinancialProvider.PIRAEUS -> trimmed
            .replace(Regex("^(Πειραιώς|Piraeus)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
        FinancialProvider.REVOLUT -> trimmed.takeUnless { it.equals("Revolut", ignoreCase = true) }.orEmpty()
        FinancialProvider.ALPHA -> trimmed.takeUnless { it.equals("Alpha Bank", ignoreCase = true) }.orEmpty()
        FinancialProvider.PAYZY -> trimmed.takeUnless { it.contains("payzy", ignoreCase = true) }.orEmpty()
        FinancialProvider.VIVA -> trimmed.takeUnless { it.contains("viva", ignoreCase = true) }.orEmpty()
        null -> trimmed
    }
    if (withoutInstitution.isNotBlank()) return withoutInstitution
    return when (kind) {
        "savings" -> "Αποταμιευτικός"
        "bank" -> "Λογαριασμός"
        else -> trimmed.ifBlank { "Λογαριασμός" }
    }
}
