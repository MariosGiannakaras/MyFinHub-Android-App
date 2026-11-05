package app.myfinhub.android.feature.money

/**
 * A canonical/imported "last4" field is untrusted presentation metadata.
 * Never print a full PAN or derive its suffix from a malformed or longer value.
 * Only exactly four ASCII digits may appear in non-vault finance UI.
 */
internal fun canonicalCardDisplayLastFour(raw: String?): String? =
    raw?.takeIf { it.length == 4 && it.all { digit -> digit in '0'..'9' } }

internal fun canonicalCardMaskedLastFour(raw: String?): String? =
    canonicalCardDisplayLastFour(raw)?.let { "•••• $it" }
