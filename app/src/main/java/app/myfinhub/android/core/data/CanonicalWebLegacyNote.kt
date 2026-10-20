package app.myfinhub.android.core.data

/**
 * Web format.ts cleanNote: historical imported discussions may contain
 * transport headers and reply markers. Classification must inspect the
 * actual user comment, not the surrounding imported thread metadata.
 *
 * Read-only normalization: the canonical saved transaction note is never
 * changed, and originals remain available in the Activity ledger.
 */
internal fun canonicalWebCleanLegacyNote(raw: String?): String {
    val note = raw.orEmpty()
    val marker = "Comment:\n"
    return if (marker in note) {
        note.substringAfter(marker).replace("\nReply:\n", "\n").trim()
    } else {
        note.replace(
            Regex("""\[Threaded comment\][\s\S]*?Comment:\n""", RegexOption.IGNORE_CASE),
            "",
        ).trim()
    }
}
