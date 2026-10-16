package app.myfinhub.android.core.network

import java.util.Locale

/** Transient server account security intent. No password, email or mutable secret in logs. */
sealed interface WebAccountSecurityChange {
    class Email(val value: String) : WebAccountSecurityChange {
        override fun toString(): String = "WebAccountSecurityChange.Email(<redacted>)"
    }

    class Password(val current: String, val replacement: String) : WebAccountSecurityChange {
        override fun toString(): String = "WebAccountSecurityChange.Password(<redacted>)"
    }
}

/** Result comes only from the owner+AAL2 Web API, never optimistically from a local profile. */
class WebAccountSecurityReceipt(val emailPendingConfirmation: Boolean) {
    override fun toString(): String = "WebAccountSecurityReceipt(<redacted>)"
}

fun normalizedAccountSecurityEmail(input: String): String? {
    val value = input.trim().lowercase(Locale.ROOT)
    val pattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
    return value.takeIf { it.length in 1..254 && pattern.matches(it) }
}

/** Matches Web passwordPolicy.ts: 12–512, Unicode lower/upper/number/punctuation or symbol. */
fun validAccountSecurityPassword(current: String, replacement: String): Boolean =
    current.isNotEmpty() && current.length <= 512 &&
        replacement.length in 12..512 && replacement != current &&
        replacement.any(Char::isLowerCase) && replacement.any(Char::isUpperCase) &&
        replacement.any(Char::isDigit) &&
        Regex("""[\p{P}\p{S}]""").containsMatchIn(replacement)
