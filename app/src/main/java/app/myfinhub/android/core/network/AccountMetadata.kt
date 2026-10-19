package app.myfinhub.android.core.network

import java.util.Locale

/** The API data is owner-private; avoid including IBAN in logs, exceptions or diagnostics. */
data class AccountMetadataRecord(
    val accountId: String,
    val iban: String?,
    val revision: Long,
    val updatedAt: String,
) {
    override fun toString(): String =
        "AccountMetadataRecord(accountId=$accountId, iban=<redacted>, revision=$revision)"
}

/** Equivalent to Web normalizeIban and isValidIban; null means no IBAN configured. */
internal fun normalizeIban(raw: String?): String? =
    raw?.filterNot { ch ->
        // Web /\\s+/g includes NBSP/narrow NBSP and Unicode BOM; JVM \\s
        // without UNICODE_CHARACTER_CLASS does not, corrupting pasted IBANs.
        ch.isWhitespace() || Character.isSpaceChar(ch) || ch == '\uFEFF'
    }?.uppercase(Locale.ROOT)?.ifBlank { null }

internal fun isValidIban(raw: String?): Boolean {
    val value = normalizeIban(raw) ?: return true
    if (value.length !in 15..34 || !Regex("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$").matches(value)) return false
    val rearranged = value.drop(4) + value.take(4)
    var remainder = 0
    for (char in rearranged) {
        val token = when (char) {
            in '0'..'9' -> char.toString()
            in 'A'..'Z' -> (char.code - 55).toString()
            else -> return false
        }
        token.forEach { digit -> remainder = (remainder * 10 + (digit - '0')) % 97 }
    }
    return remainder == 1
}

/** Web dashboard masking: show country/check digits and last 4 only. */
internal fun compactIban(raw: String?): String? {
    val value = normalizeIban(raw) ?: return null
    if (!isValidIban(value)) return null
    return if (value.length <= 8) value else "${value.take(4)} •••• •••• •••• ${value.takeLast(4)}"
}
