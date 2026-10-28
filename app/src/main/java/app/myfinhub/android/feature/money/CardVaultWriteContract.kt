package app.myfinhub.android.feature.money

import app.myfinhub.android.core.network.CardSecretUpdate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Mirrors Web v1.7 server/cardVaultCrypto.ts NEW-WRITE validation only.
 * This MUST NOT be applied to encrypted legacy reads: older PAN/CVV and expired
 * expiries can still be displayed without destroying or rewriting them.
 */
internal object CardVaultWriteContract {
    private val expirationPattern = Regex("""^([0-9]{2})\s*[/-]\s*([0-9]{2}|[0-9]{4})$""")

    fun normalizePan(raw: String): String? {
        val digits = raw.filter { it in '0'..'9' }
        return digits.takeIf { it.length == 16 }
    }

    fun normalizeCvv(raw: String): String? =
        raw.trim().takeIf { it.length == 3 && it.all { digit -> digit in '0'..'9' } }

    /**
     * Actual server behavior: accepts future MM/YY and MM/YYYY (also hyphen),
     * normalizes transport to MM/YY; uses UTC current month, not local timezone.
     */
    fun normalizeExpiry(raw: String, currentMonth: YearMonth = YearMonth.now(ZoneOffset.UTC)): String? {
        val parts = expirationPattern.matchEntire(raw.trim()) ?: return null
        val month = parts.groupValues[1].toInt()
        val enteredYear = parts.groupValues[2]
        val year = if (enteredYear.length == 2) 2000 + enteredYear.toInt() else enteredYear.toInt()
        if (month !in 1..12 || year !in 2000..2099) return null
        if (YearMonth.of(year, month).isBefore(currentMonth)) return null
        return parts.groupValues[1] + "/" + (year % 100).toString().padStart(2, '0')
    }

    /** Fail closed before ANY write; a legacy partial migration is never exempt. */
    fun isValidNewWrite(update: CardSecretUpdate, currentMonth: YearMonth = YearMonth.now(ZoneOffset.UTC)): Boolean {
        val present = update.pan != null || update.expiry != null || update.cvv != null
        return present &&
            (update.pan == null || normalizePan(update.pan) != null) &&
            (update.expiry == null || normalizeExpiry(update.expiry, currentMonth) != null) &&
            (update.cvv == null || normalizeCvv(update.cvv) != null)
    }
}
