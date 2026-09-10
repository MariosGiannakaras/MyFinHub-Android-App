package app.myfinhub.android.feature.utilities

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Safe support metadata only. Never place credentials, tokens, finance payloads or card secrets here. */
data class AppDiagnosticsSnapshot(
    val versionName: String,
    val buildType: String,
    val environment: String,
    val apiHost: String,
    val networkStatus: String,
    val apiStatus: String,
    val sessionStatus: String,
    val lastSuccessfulSync: String?,
    val lastDiagnosticCode: String?,
)

private val greekDiagnosticTimeFormatter = DateTimeFormatter.ofPattern(
    "d MMM yyyy · HH:mm",
    Locale.forLanguageTag("el-GR"),
)

internal fun formatDiagnosticTime(
    rawTimestamp: String?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val raw = rawTimestamp?.trim().orEmpty()
    if (raw.isBlank()) return "Δεν υπάρχει ακόμη"
    val instant = runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: return "Άγνωστος χρόνος"
    return instant.atZone(zoneId).format(greekDiagnosticTimeFormatter)
}

internal fun diagnosticCodeDescription(rawCode: String?): String? {
    val code = rawCode?.trim()?.uppercase(Locale.ROOT).orEmpty()
    if (code.isBlank()) return null
    return when {
        code.startsWith("MFH-AUTH") && (code.contains("MFA") || code.contains("AAL")) ->
            "Χρειάστηκε επιπλέον επαλήθευση ταυτότητας."
        code.startsWith("MFH-AUTH") ->
            "Καταγράφηκε έλεγχος της σύνδεσης λογαριασμού."
        code.startsWith("MFH-NET") || code.startsWith("MFH-OFFLINE") ->
            "Καταγράφηκε αλλαγή σύνδεσης ή αναμονή συγχρονισμού."
        code.startsWith("MFH-API") && (code.contains("503") || code.contains("SERVER")) ->
            "Η υπηρεσία MyFinHub δεν ήταν προσωρινά διαθέσιμη."
        code.startsWith("MFH-API") ->
            "Καταγράφηκε συμβάν συγχρονισμού με το MyFinHub."
        code.startsWith("MFH-APP") ->
            "Καταγράφηκε τεχνικό συμβάν της εφαρμογής."
        else -> "Καταγράφηκε τεχνικό συμβάν για υποστήριξη."
    }
}
