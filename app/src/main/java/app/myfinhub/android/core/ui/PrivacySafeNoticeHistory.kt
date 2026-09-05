package app.myfinhub.android.core.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val NOTICE_DEDUPE_WINDOW_MILLIS = 12_000L
private const val HISTORY_DEDUPE_WINDOW_MILLIS = 60_000L
private const val MAX_HISTORY_ENTRIES = 40
private val SAFE_DIAGNOSTIC_CODE = Regex("[^A-Z0-9_-]+")

data class PrivacySafeNoticeRecord(
    val diagnosticCode: String,
    val occurredAtEpochMillis: Long,
) {
    val title: String
        get() = when {
            diagnosticCode.startsWith("MFH-AUTH") -> "Έλεγχος σύνδεσης"
            diagnosticCode.startsWith("MFH-NET") || diagnosticCode.startsWith("MFH-OFFLINE") -> "Κατάσταση δικτύου"
            diagnosticCode.startsWith("MFH-API") -> "Συγχρονισμός MyFinHub"
            diagnosticCode.startsWith("MFH-APP") -> "Λειτουργία εφαρμογής"
            else -> "Ενημέρωση εφαρμογής"
        }
}

internal fun privacySafeNoticeRecord(
    notice: UserNotice,
    occurredAtEpochMillis: Long,
): PrivacySafeNoticeRecord = PrivacySafeNoticeRecord(
    diagnosticCode = sanitizeDiagnosticCode(notice.diagnosticCode),
    occurredAtEpochMillis = occurredAtEpochMillis.coerceAtLeast(0L),
)

internal fun shouldPresentNotice(
    notice: UserNotice,
    recentByCode: MutableMap<String, Long>,
    nowElapsedMillis: Long,
): Boolean {
    val now = nowElapsedMillis.coerceAtLeast(0L)
    val key = sanitizeDiagnosticCode(notice.diagnosticCode)
    recentByCode.entries.removeAll { (_, shownAt) -> now - shownAt >= NOTICE_DEDUPE_WINDOW_MILLIS * 4 }
    val previous = recentByCode[key]
    if (previous != null && now >= previous && now - previous < NOTICE_DEDUPE_WINDOW_MILLIS) return false
    recentByCode[key] = now
    return true
}

internal fun encodePrivacySafeNoticeHistory(records: List<PrivacySafeNoticeRecord>): String = records
    .take(MAX_HISTORY_ENTRIES)
    .joinToString("\n") { record ->
        "${record.occurredAtEpochMillis}|${sanitizeDiagnosticCode(record.diagnosticCode)}"
    }

internal fun decodePrivacySafeNoticeHistory(raw: String?): List<PrivacySafeNoticeRecord> = raw
    .orEmpty()
    .lineSequence()
    .mapNotNull { line ->
        val separator = line.indexOf('|')
        if (separator <= 0 || separator == line.lastIndex) return@mapNotNull null
        val timestamp = line.substring(0, separator).toLongOrNull() ?: return@mapNotNull null
        val code = sanitizeDiagnosticCode(line.substring(separator + 1))
        if (timestamp < 0L || code.isBlank()) return@mapNotNull null
        PrivacySafeNoticeRecord(code, timestamp)
    }
    .take(MAX_HISTORY_ENTRIES)
    .toList()

class PrivacySafeNoticeHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun load(): List<PrivacySafeNoticeRecord> = withContext(Dispatchers.IO) {
        decodePrivacySafeNoticeHistory(preferences.getString(KEY_ENTRIES, null))
    }

    suspend fun append(
        notice: UserNotice,
        occurredAtEpochMillis: Long,
    ): List<PrivacySafeNoticeRecord> = withContext(Dispatchers.IO) {
        val record = privacySafeNoticeRecord(notice, occurredAtEpochMillis)
        val current = decodePrivacySafeNoticeHistory(preferences.getString(KEY_ENTRIES, null))
        val first = current.firstOrNull()
        val duplicate = first != null &&
            first.diagnosticCode == record.diagnosticCode &&
            record.occurredAtEpochMillis >= first.occurredAtEpochMillis &&
            record.occurredAtEpochMillis - first.occurredAtEpochMillis < HISTORY_DEDUPE_WINDOW_MILLIS
        val updated = if (duplicate) current else (listOf(record) + current).take(MAX_HISTORY_ENTRIES)
        preferences.edit().putString(KEY_ENTRIES, encodePrivacySafeNoticeHistory(updated)).commit()
        updated
    }

    private companion object {
        const val PREFERENCES_NAME = "myfinhub_privacy_safe_notice_history"
        const val KEY_ENTRIES = "entries_v1"
    }
}

private fun sanitizeDiagnosticCode(raw: String): String = raw
    .uppercase()
    .replace(SAFE_DIAGNOSTIC_CODE, "_")
    .trim('_')
    .take(96)
    .ifBlank { "MFH-UNKNOWN" }
