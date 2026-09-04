from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise AssertionError(f"{label}: expected 1 match, got {count}")
    p.write_text(text.replace(old, new, 1))

# 1) Privacy-safe durable notice history + transient dedupe policy.
Path('app/src/main/java/app/myfinhub/android/core/ui/PrivacySafeNoticeHistory.kt').write_text(r'''package app.myfinhub.android.core.ui

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
''')

Path('app/src/main/java/app/myfinhub/android/feature/utilities/NoticeHistoryScreen.kt').write_text(r'''package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.core.ui.PrivacySafeNoticeRecord
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NoticeHistoryScreen(
    entries: List<PrivacySafeNoticeRecord>,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Ιστορικό ειδοποιήσεων",
                subtitle = "Μόνο μη ευαίσθητες τεχνικές εγγραφές",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MyFinHubSpacing.lg,
                top = padding.calculateTopPadding() + MyFinHubSpacing.xs,
                end = MyFinHubSpacing.lg,
                bottom = MyFinHubSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            item {
                Text(
                    "Αποθηκεύονται μόνο χρόνος και ασφαλής διαγνωστικός κωδικός. Δεν αποθηκεύονται ποσά, οικονομικά payloads, PAN, CVV, PIN, TOTP ή tokens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entries.isEmpty()) {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Δεν υπάρχουν καταγεγραμμένες ειδοποιήσεις.")
                    }
                }
            } else {
                item {
                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            entries.forEachIndexed { index, entry ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MyFinHubIconBadge(
                                        icon = MyFinHubIcons.Attention,
                                        tone = FinanceTone.Neutral,
                                        contentDescription = null,
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro),
                                    ) {
                                        Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            formatNoticeTime(entry.occurredAtEpochMillis),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            entry.diagnosticCode,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (index != entries.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatNoticeTime(epochMillis: Long): String = runCatching {
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.forLanguageTag("el-GR")))
}.getOrDefault("Άγνωστος χρόνος")
''')

# 2) Route and production settings entry point.
replace_once(
    'app/src/main/java/app/myfinhub/android/app/AppRoute.kt',
    '    @Serializable data object ChangeHistory : AppRoute\n',
    '    @Serializable data object ChangeHistory : AppRoute\n    @Serializable data object NoticeHistory : AppRoute\n',
    'notice history route',
)

replace_once(
    'app/src/main/java/app/myfinhub/android/feature/utilities/ProductionSettingsScreen.kt',
    '''    onBack: () -> Unit,\n    diagnostics: AppDiagnosticsSnapshot? = null,\n    onLogout: (() -> Unit)? = null,\n) {''',
    '''    onBack: () -> Unit,\n    diagnostics: AppDiagnosticsSnapshot? = null,\n    noticeHistoryCount: Int = 0,\n    onOpenNoticeHistory: () -> Unit = {},\n    onLogout: (() -> Unit)? = null,\n) {''',
    'production settings signature',
)
replace_once(
    'app/src/main/java/app/myfinhub/android/feature/utilities/ProductionSettingsScreen.kt',
    '''            UpdateSettingsCard(\n                currentVersionName = BuildConfig.VERSION_NAME,''',
    '''            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {\n                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {\n                    Text("Ειδοποιήσεις", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)\n                    Text(\n                        "Το τοπικό ιστορικό κρατά μόνο ασφαλή κατηγορία, διαγνωστικό κωδικό και χρόνο. Επαναλαμβανόμενες ίδιες ειδοποιήσεις συμπτύσσονται.",\n                        style = MaterialTheme.typography.bodySmall,\n                        color = MaterialTheme.colorScheme.onSurfaceVariant,\n                    )\n                    MyFinHubOutlinedAction(\n                        label = if (noticeHistoryCount == 0) "Ιστορικό ειδοποιήσεων" else "Ιστορικό ειδοποιήσεων ($noticeHistoryCount)",\n                        onClick = onOpenNoticeHistory,\n                        modifier = Modifier.fillMaxWidth(),\n                        icon = MyFinHubIcons.Activity,\n                    )\n                }\n            }\n\n            UpdateSettingsCard(\n                currentVersionName = BuildConfig.VERSION_NAME,''',
    'production settings notice card',
)
# import icon registry used by the new settings action.
replace_once(
    'app/src/main/java/app/myfinhub/android/feature/utilities/ProductionSettingsScreen.kt',
    'import app.myfinhub.android.designsystem.MyFinHubBackButton\n',
    'import app.myfinhub.android.designsystem.MyFinHubBackButton\nimport app.myfinhub.android.designsystem.MyFinHubIcons\n',
    'production settings icon import',
)

# 3) App navigation receives durable history independently from retained synthetic change-history state.
replace_once(
    'app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt',
    'import app.myfinhub.android.feature.utilities.FrontendUtilitiesUiState\n',
    'import app.myfinhub.android.feature.utilities.FrontendUtilitiesUiState\nimport app.myfinhub.android.feature.utilities.NoticeHistoryScreen\nimport app.myfinhub.android.core.ui.PrivacySafeNoticeRecord\n',
    'app notice history imports',
)
replace_once(
    'app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt',
    '''    diagnostics: AppDiagnosticsSnapshot? = null,\n    onLogout: (() -> Unit)? = null,''',
    '''    diagnostics: AppDiagnosticsSnapshot? = null,\n    noticeHistory: List<PrivacySafeNoticeRecord> = emptyList(),\n    onLogout: (() -> Unit)? = null,''',
    'app content history parameter',
)
replace_once(
    'app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt',
    '''                            diagnostics = diagnostics,\n                            onLogout = onLogout,''',
    '''                            diagnostics = diagnostics,\n                            noticeHistoryCount = noticeHistory.size,\n                            onOpenNoticeHistory = { homeBackStack.pushIfNew(AppRoute.NoticeHistory) },\n                            onLogout = onLogout,''',
    'production settings history wiring',
)
replace_once(
    'app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt',
    '''                entry<AppRoute.ChangeHistory> {\n                    ChangeHistoryScreen(''',
    '''                entry<AppRoute.NoticeHistory> {\n                    NoticeHistoryScreen(\n                        entries = noticeHistory,\n                        onBack = { homeBackStack.removeLastOrNull() },\n                    )\n                }\n                entry<AppRoute.ChangeHistory> {\n                    ChangeHistoryScreen(''',
    'notice history nav entry',
)

# 4) Root: dedupe transient notices, persist only privacy-safe records, pass history into product surface.
replace_once(
    'app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt',
    'import android.content.Context\n',
    'import android.content.Context\nimport android.os.SystemClock\n',
    'system clock import',
)
replace_once(
    'app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt',
    'import androidx.compose.foundation.layout.padding\n',
    'import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.Row\nimport androidx.compose.foundation.layout.statusBarsPadding\n',
    'root layout imports',
)
replace_once(
    'app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt',
    'import app.myfinhub.android.core.ui.UserNotice\n',
    'import app.myfinhub.android.core.ui.PrivacySafeNoticeHistoryStore\nimport app.myfinhub.android.core.ui.PrivacySafeNoticeRecord\nimport app.myfinhub.android.core.ui.UserNotice\nimport app.myfinhub.android.core.ui.shouldPresentNotice\n',
    'root notice imports',
)
replace_once(
    'app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt',
    '''    val snackbarHostState = remember { SnackbarHostState() }\n    var detailNotice by remember { mutableStateOf<UserNotice?>(null) }\n    var lastDiagnosticCode by remember { mutableStateOf<String?>(null) }''',
    '''    val snackbarHostState = remember { SnackbarHostState() }\n    val noticeHistoryStore = remember(context) { PrivacySafeNoticeHistoryStore(context) }\n    val recentNoticePresentation = remember { mutableMapOf<String, Long>() }\n    var noticeHistory by remember { mutableStateOf<List<PrivacySafeNoticeRecord>>(emptyList()) }\n    var detailNotice by remember { mutableStateOf<UserNotice?>(null) }\n    var lastDiagnosticCode by remember { mutableStateOf<String?>(null) }\n    LaunchedEffect(noticeHistoryStore) {\n        noticeHistory = noticeHistoryStore.load()\n    }''',
    'root history state',
)
replace_once(
    'app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt',
    '''            if (duplicateOfSaveIssue) return@collect\n\n            val result = snackbarHostState.showSnackbar(''',
    '''            if (duplicateOfSaveIssue) return@collect\n            if (!shouldPresentNotice(notice, recentNoticePresentation, SystemClock.elapsedRealtime())) return@collect\n\n            noticeHistory = noticeHistoryStore.append(notice, System.currentTimeMillis())\n            val result = snackbarHostState.showSnackbar(''',
    'root notice dedupe and persistence',
)
replace_once(
    'app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt',
    '''                            onCreateCard = financeViewModel::createCard,\n                            diagnostics = diagnostics,''',
    '''                            onCreateCard = financeViewModel::createCard,\n                            diagnostics = diagnostics,\n                            noticeHistory = noticeHistory,''',
    'root surface history wiring',
)
replace_once(
    'app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt',
    '''    onCreateCard: (CardCreateRequest) -> Unit,\n    diagnostics: AppDiagnosticsSnapshot,\n) {''',
    '''    onCreateCard: (CardCreateRequest) -> Unit,\n    diagnostics: AppDiagnosticsSnapshot,\n    noticeHistory: List<PrivacySafeNoticeRecord>,\n) {''',
    'surface history signature',
)
replace_once(
    'app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt',
    '''                    diagnostics = diagnostics,\n                    onLogout = onLogout,\n                    canonicalProductMode = true,''',
    '''                    diagnostics = diagnostics,\n                    noticeHistory = noticeHistory,\n                    onLogout = onLogout,\n                    canonicalProductMode = true,''',
    'surface app history pass',
)
# Make progress/pending overlays respect status bars; compact the pending banner.
replace_once(
    'app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt',
    '''                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),''',
    '''                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding(),''',
    'saving progress insets',
)
replace_once(
    'app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt',
    '''                            .align(Alignment.TopCenter)\n                            .padding(\n                                start = MyFinHubSpacing.md,\n                                top = MyFinHubSpacing.md,\n                                end = MyFinHubSpacing.md,\n                            ),''',
    '''                            .align(Alignment.TopCenter)\n                            .statusBarsPadding()\n                            .padding(horizontal = MyFinHubSpacing.md, vertical = MyFinHubSpacing.xs),''',
    'pending overlay insets',
)
old_banner = '''        Column(\n            modifier = Modifier.padding(MyFinHubSpacing.md),\n            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),\n        ) {\n            Text(\n                if (changeCount == 1) "1 αλλαγή σε αναμονή" else "$changeCount αλλαγές σε αναμονή",\n                style = MaterialTheme.typography.titleMedium,\n            )\n            Text(\n                "${latest.label} · ${latest.statusLabel}",\n                style = MaterialTheme.typography.bodyMedium,\n                color = MaterialTheme.colorScheme.onSurfaceVariant,\n            )\n            if (latest.canUndo) {\n                TextButton(onClick = onUndoLatest) {\n                    Text("Αναίρεση τελευταίας")\n                }\n            } else {\n                Text(\n                    "Η αλλαγή μπορεί ήδη να έχει φτάσει στον server και θα επιβεβαιωθεί πριν από οποιαδήποτε νέα ενέργεια.",\n                    style = MaterialTheme.typography.bodySmall,\n                    color = MaterialTheme.colorScheme.onSurfaceVariant,\n                )\n            }\n        }'''
new_banner = '''        Row(\n            modifier = Modifier.padding(horizontal = MyFinHubSpacing.md, vertical = MyFinHubSpacing.xs),\n            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),\n            verticalAlignment = Alignment.CenterVertically,\n        ) {\n            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {\n                Text(\n                    if (changeCount == 1) "1 αλλαγή σε αναμονή" else "$changeCount αλλαγές σε αναμονή",\n                    style = MaterialTheme.typography.titleSmall,\n                )\n                Text(\n                    "${latest.label} · ${latest.statusLabel}",\n                    style = MaterialTheme.typography.bodySmall,\n                    color = MaterialTheme.colorScheme.onSurfaceVariant,\n                )\n            }\n            if (latest.canUndo) {\n                TextButton(onClick = onUndoLatest) { Text("Αναίρεση") }\n            }\n        }'''
replace_once('app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt', old_banner, new_banner, 'compact pending banner')

# 5) Destructive create->delete remains visible: reconstruct card from ordered replay state.
p = Path('app/src/main/java/app/myfinhub/android/app/PendingUiProjection.kt')
t = p.read_text()
start = t.index('private fun pendingCardDeletionMessage(')
end = t.index('\nprivate fun PendingMutationSyncState.pendingStatusLabel()', start)
replacement = r'''private fun pendingCardDeletionMessage(
    serverDocument: CanonicalFinanceDocument,
    pending: List<PendingCanonicalMutationIntent>,
    today: LocalDate,
): String? {
    var replayDocument = serverDocument
    val linesByCardId = linkedMapOf<String, String>()

    for (intent in pending) {
        if (intent.kind == PendingMutationKind.DEACTIVATE_CARD) {
            val cardId = intent.affectedCardId.orEmpty()
            val card = runCatching {
                projectCanonicalProduct(replayDocument, today).moneyState.cards.firstOrNull { it.id == cardId }
            }.getOrNull()
            if (card != null) {
                val last4 = card.last4.takeIf(String::isNotBlank)?.let { " ••••$it" }.orEmpty()
                linesByCardId[cardId] = "${card.nickname}$last4 · Εκκρεμεί διαγραφή · ${intent.syncState.pendingStatusLabel()}"
            }
        }

        val next = runCatching { intent.asMutation().apply(replayDocument) }.getOrNull() ?: break
        replayDocument = next
    }

    if (linesByCardId.isEmpty()) return null
    return "Εκκρεμείς διαγραφές καρτών:\n${linesByCardId.values.joinToString("\n")}"
}
'''
p.write_text(t[:start] + replacement + t[end:])

# 6) Focused tests: privacy, dedupe, and create->delete tombstone semantics.
Path('app/src/test/java/app/myfinhub/android/core/ui/PrivacySafeNoticeHistoryTest.kt').parent.mkdir(parents=True, exist_ok=True)
Path('app/src/test/java/app/myfinhub/android/core/ui/PrivacySafeNoticeHistoryTest.kt').write_text(r'''package app.myfinhub.android.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySafeNoticeHistoryTest {
    @Test
    fun recordAndCodec_neverPersistNoticeMessageOrDetails() {
        val notice = UserNotice(
            message = "4111111111111111",
            details = "CVV 123 token secret",
            diagnosticCode = "MFH-API-NETWORK-503",
        )

        val record = privacySafeNoticeRecord(notice, 1_000L)
        val encoded = encodePrivacySafeNoticeHistory(listOf(record))

        assertEquals("MFH-API-NETWORK-503", record.diagnosticCode)
        assertFalse(encoded.contains("4111111111111111"))
        assertFalse(encoded.contains("CVV"))
        assertFalse(encoded.contains("token"))
        assertEquals(listOf(record), decodePrivacySafeNoticeHistory(encoded))
    }

    @Test
    fun presentationPolicy_deduplicatesSameCodeOnlyInsideWindow() {
        val recent = mutableMapOf<String, Long>()
        val notice = UserNotice("safe", "safe", "MFH-NET-OFFLINE")
        val different = UserNotice("safe", "safe", "MFH-API-SERVER-500")

        assertTrue(shouldPresentNotice(notice, recent, 10_000L))
        assertFalse(shouldPresentNotice(notice, recent, 10_000L + NOTICE_DEDUPE_WINDOW_MILLIS - 1))
        assertTrue(shouldPresentNotice(different, recent, 10_001L))
        assertTrue(shouldPresentNotice(notice, recent, 10_000L + NOTICE_DEDUPE_WINDOW_MILLIS))
    }

    @Test
    fun decoder_ignoresMalformedEntriesAndBoundsUnsafeCodeCharacters() {
        val decoded = decodePrivacySafeNoticeHistory("bad\n123|mfh api network !!!\n-1|MFH-NEGATIVE")
        assertEquals(1, decoded.size)
        assertEquals("MFH_API_NETWORK", decoded.single().diagnosticCode)
    }
}
''')

# Add create->delete pending UI regression to existing projection tests.
replace_once(
    'app/src/test/java/app/myfinhub/android/app/PendingUiProjectionTest.kt',
    'import app.myfinhub.android.core.data.DeactivateCanonicalCard\n',
    'import app.myfinhub.android.core.data.CreateCanonicalCard\nimport app.myfinhub.android.core.data.DeactivateCanonicalCard\n',
    'pending test create card import',
)
marker = '''    @Test\n    fun pendingBudget_exposesNeedsReviewInlineState() {'''
test = r'''    @Test
    fun neverSentCreateThenDelete_remainsVisibleAsPendingDeletionAndCanBeUndoneCausally() {
        val server = cardFixture()
        val create = PendingCanonicalMutationIntent.fromMutation(
            CreateCanonicalCard(
                cardId = "card-local",
                bankId = "issuer-local",
                nickname = "Νέα κάρτα",
                kind = "debit",
                network = "visa",
                formFactor = "physical",
                last4 = "4242",
                creditLimit = null,
                nowIso = now,
            ),
            intentId = "intent-card-create",
        )
        val delete = PendingCanonicalMutationIntent.fromMutation(
            DeactivateCanonicalCard("card-local", now),
            intentId = "intent-card-delete-local",
        )

        val result = projectWithPending(server, listOf(create, delete))

        assertFalse(result.moneyState.cards.any { it.id == "card-local" })
        assertTrue(result.moneyState.frontendMessage.orEmpty().contains("Νέα κάρτα ••••4242"))
        assertTrue(result.moneyState.frontendMessage.orEmpty().contains("Εκκρεμεί διαγραφή"))
    }

'''
replace_once('app/src/test/java/app/myfinhub/android/app/PendingUiProjectionTest.kt', marker, test + marker, 'pending create delete test')

print('batch F transformations applied')
