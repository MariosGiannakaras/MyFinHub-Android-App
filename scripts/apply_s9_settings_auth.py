from __future__ import annotations

import json
from pathlib import Path

ROOT = Path.cwd()


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, text: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, got {count}: {old[:100]!r}")
    write(path, text.replace(old, new, 1))


# ---------------------------------------------------------------------------
# S9.1 Settings: consumer-first hierarchy, global privacy semantics, diagnostics
# behind a separate About/Diagnostics destination, and Sign out isolated.
# ---------------------------------------------------------------------------
write(
    "app/src/main/java/app/myfinhub/android/feature/utilities/ProductionSettingsScreen.kt",
    r'''package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.BuildConfig
import app.myfinhub.android.core.update.LocalUpdateController
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing

/** Production settings expose only controls that change real application behavior. */
@Composable
fun ProductionSettingsScreen(
    @Suppress("UNUSED_PARAMETER") state: FrontendUtilitiesUiState,
    @Suppress("UNUSED_PARAMETER") onAction: (FrontendUtilitiesAction) -> Unit,
    onBack: () -> Unit,
    diagnostics: AppDiagnosticsSnapshot? = null,
    noticeHistoryCount: Int = 0,
    onOpenNoticeHistory: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onLogout: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") diagnosticsInitiallyExpanded: Boolean = false,
) {
    val context = LocalContext.current
    val updateController = LocalUpdateController.current
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    var appearance by remember { mutableStateOf(AppAppearancePreference.read(context)) }
    var amountsVisible by remember { mutableStateOf(AmountVisibilityPreference.read(context)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Ρυθμίσεις",
                subtitle = "Εμφάνιση, απόρρητο και λογαριασμός",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MyFinHubDesignMetrics.screenHorizontalPadding, vertical = MyFinHubSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
        ) {
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth().testTag("s9_settings_preferences")) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text("Εμφάνιση", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    val appearanceOption: @Composable (AppAppearance, Modifier) -> Unit = { option, modifier ->
                        FilterChip(
                            selected = appearance == option,
                            onClick = {
                                appearance = option
                                AppAppearancePreference.write(context, option)
                            },
                            label = { Text(option.label) },
                            modifier = modifier,
                        )
                    }
                    if (largeFont) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                        ) {
                            AppAppearance.entries.forEach { appearanceOption(it, Modifier.fillMaxWidth()) }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                        ) {
                            AppAppearance.entries.forEach { appearanceOption(it, Modifier.weight(1f)) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Εμφάνιση ποσών", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (amountsVisible) {
                                    "Τα οικονομικά ποσά εμφανίζονται σε όλες τις βασικές οθόνες."
                                } else {
                                    "Τα οικονομικά ποσά καλύπτονται όπου υποστηρίζεται η καθολική απόκρυψη."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = amountsVisible,
                            onCheckedChange = { visible ->
                                amountsVisible = visible
                                AmountVisibilityPreference.write(context, visible)
                            },
                            modifier = Modifier.semantics { contentDescription = "Εμφάνιση ποσών" },
                        )
                    }
                }
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth().testTag("s9_settings_privacy")) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Text("Απόρρητο και ασφάλεια", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Τα ευαίσθητα στοιχεία κάρτας παραμένουν κρυμμένα μέχρι να τα ζητήσεις και προστατεύονται από την υπάρχουσα ασφαλή συνεδρία της εφαρμογής.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MyFinHubOutlinedAction(
                        label = if (noticeHistoryCount == 0) "Ιστορικό ειδοποιήσεων" else "Ιστορικό ειδοποιήσεων · $noticeHistoryCount",
                        onClick = onOpenNoticeHistory,
                        modifier = Modifier.fillMaxWidth(),
                        icon = MyFinHubIcons.Activity,
                    )
                }
            }

            if (BuildConfig.SELF_UPDATE_ENABLED) {
                UpdateSettingsCard(
                    currentVersionName = BuildConfig.VERSION_NAME,
                    state = updateController.state,
                    onCheck = updateController.check,
                    onDownload = updateController.download,
                    onInstall = updateController.install,
                    onOpenInstallPermission = updateController.openInstallPermission,
                    onAuthRecovery = onLogout ?: {},
                )
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth().testTag("s9_settings_about")) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Text("Σχετικά", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "MyFinHub ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    diagnostics?.let {
                        MyFinHubOutlinedAction(
                            label = "Διαγνωστικά και υποστήριξη",
                            onClick = onOpenDiagnostics,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            onLogout?.let { logout ->
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth().testTag("s9_settings_account")) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text("Λογαριασμός", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Η αποσύνδεση κλείνει την ασφαλή συνεδρία σε αυτή τη συσκευή. Τα συγχρονισμένα δεδομένα του λογαριασμού δεν διαγράφονται.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MyFinHubOutlinedAction(
                            label = "Αποσύνδεση",
                            onClick = logout,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
''',
)

# ---------------------------------------------------------------------------
# S9.2 Diagnostics gets a dedicated consumer route and one sanitized Copy action.
# ---------------------------------------------------------------------------
write(
    "app/src/main/java/app/myfinhub/android/feature/utilities/ProductionDiagnosticsScreen.kt",
    r'''package app.myfinhub.android.feature.utilities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubOutlinedAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSpacing

@Composable
fun ProductionDiagnosticsScreen(
    diagnostics: AppDiagnosticsSnapshot,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Διαγνωστικά",
                subtitle = "Ασφαλή στοιχεία για έλεγχο και υποστήριξη",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("s9_diagnostics_root"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = padding.calculateTopPadding() + MyFinHubSpacing.xs,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = MyFinHubSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
        ) {
            item("diagnostics-card") {
                ProductionDiagnosticsCard(
                    diagnostics = diagnostics,
                    supportDetailsInitiallyExpanded = true,
                )
            }
            item("copy") {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    MyFinHubOutlinedAction(
                        label = if (copied) "Αντιγράφηκαν ασφαλή στοιχεία" else "Αντιγραφή ασφαλών στοιχείων",
                        onClick = {
                            copyDiagnostics(context, diagnosticsSupportText(diagnostics))
                            copied = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("s9_diagnostics_copy"),
                    )
                    Text(
                        "Το αντίγραφο δεν περιλαμβάνει ποσά, κινήσεις, διαπιστευτήρια ή στοιχεία κάρτας.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal fun diagnosticsSupportText(diagnostics: AppDiagnosticsSnapshot): String = buildString {
    appendLine("MyFinHub diagnostics")
    appendLine("Version: ${diagnostics.versionName} (${diagnostics.buildType})")
    appendLine("Environment: ${diagnostics.environment}")
    appendLine("Service: ${diagnostics.apiHost}")
    appendLine("Network: ${diagnostics.networkStatus}")
    appendLine("Sync: ${humanReadableSyncStatus(diagnostics.apiStatus)}")
    appendLine("Session: ${humanReadableSessionStatus(diagnostics.sessionStatus)}")
    appendLine("Last sync: ${formatDiagnosticTime(diagnostics.lastSuccessfulSync)}")
    append("Code: ${diagnostics.lastDiagnosticCode ?: "none"}")
}

private fun copyDiagnostics(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("MyFinHub diagnostics", text))
}
''',
)

# ---------------------------------------------------------------------------
# S9.2 updater: exactly one valid next action for each state/failure family.
# ---------------------------------------------------------------------------
write(
    "app/src/main/java/app/myfinhub/android/feature/utilities/UpdateSettingsCard.kt",
    r'''package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.core.update.UpdateFailureKind
import app.myfinhub.android.core.update.UpdateUiState
import app.myfinhub.android.designsystem.MyFinHubOutlinedAction
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.util.Locale

internal enum class UpdateRecoveryAction { CHECK, DOWNLOAD, INSTALL, INSTALL_PERMISSION, AUTH }

internal fun updateRecoveryAction(kind: UpdateFailureKind, releaseAvailable: Boolean): UpdateRecoveryAction = when (kind) {
    UpdateFailureKind.AUTH_REQUIRED,
    UpdateFailureKind.MFA_REQUIRED -> UpdateRecoveryAction.AUTH

    UpdateFailureKind.DOWNLOAD_SIZE_MISMATCH,
    UpdateFailureKind.DOWNLOAD_DIGEST_MISMATCH -> if (releaseAvailable) UpdateRecoveryAction.DOWNLOAD else UpdateRecoveryAction.CHECK

    UpdateFailureKind.INSTALL_PERMISSION_REQUIRED -> UpdateRecoveryAction.INSTALL_PERMISSION
    UpdateFailureKind.INSTALL_BLOCKED,
    UpdateFailureKind.INSTALL_FAILED -> if (releaseAvailable) UpdateRecoveryAction.INSTALL else UpdateRecoveryAction.CHECK

    else -> UpdateRecoveryAction.CHECK
}

@Composable
internal fun UpdateSettingsCard(
    currentVersionName: String,
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onAuthRecovery: () -> Unit = {},
) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text("Ενημερώσεις", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Τρέχουσα έκδοση $currentVersionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state) {
                UpdateUiState.Idle -> {
                    StatusText("Έλεγξε αν υπάρχει νεότερη εγκεκριμένη έκδοση.")
                    MyFinHubOutlinedAction("Έλεγχος για ενημερώσεις", onCheck, Modifier.fillMaxWidth())
                }
                UpdateUiState.Checking -> {
                    StatusText("Έλεγχος για νέα έκδοση…")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                UpdateUiState.UpToDate -> {
                    StatusText("Η εφαρμογή είναι ενημερωμένη.")
                    MyFinHubOutlinedAction("Έλεγχος ξανά", onCheck, Modifier.fillMaxWidth())
                }
                is UpdateUiState.Available -> {
                    ReleaseCopy(state.release.versionName, state.release.sizeBytes, state.release.notes, state.release.mandatory)
                    MyFinHubPrimaryAction("Λήψη ενημέρωσης", onDownload, Modifier.fillMaxWidth(), icon = null)
                }
                is UpdateUiState.Downloading -> {
                    ReleaseCopy(state.release.versionName, state.release.sizeBytes, state.release.notes, state.release.mandatory)
                    Text(
                        "Λήψη ${String.format(Locale.ROOT, "%.0f", state.progress * 100f)}%",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateUiState.ReadyToInstall -> {
                    ReleaseCopy(state.release.versionName, state.release.sizeBytes, state.release.notes, state.release.mandatory)
                    StatusText("Το αρχείο επαληθεύτηκε και είναι έτοιμο για εγκατάσταση.")
                    MyFinHubPrimaryAction("Εγκατάσταση ενημέρωσης", onInstall, Modifier.fillMaxWidth(), icon = null)
                }
                is UpdateUiState.PermissionRequired -> {
                    ReleaseCopy(state.release.versionName, state.release.sizeBytes, state.release.notes, state.release.mandatory)
                    StatusText("Το Android χρειάζεται άδεια για εγκατάσταση αυτής της ιδιωτικής ενημέρωσης.")
                    MyFinHubPrimaryAction("Άνοιγμα ρύθμισης εγκατάστασης", onOpenInstallPermission, Modifier.fillMaxWidth(), icon = null)
                }
                is UpdateUiState.Installing -> {
                    StatusText("Η εγκατάσταση της έκδοσης ${state.release.versionName} ξεκίνησε. Το Android μπορεί να ζητήσει επιβεβαίωση.")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is UpdateUiState.Failure -> {
                    StatusText(updateFailureMessage(state.kind))
                    when (updateRecoveryAction(state.kind, state.release != null)) {
                        UpdateRecoveryAction.AUTH -> MyFinHubOutlinedAction("Σύνδεση ξανά", onAuthRecovery, Modifier.fillMaxWidth())
                        UpdateRecoveryAction.DOWNLOAD -> MyFinHubOutlinedAction("Λήψη ξανά", onDownload, Modifier.fillMaxWidth())
                        UpdateRecoveryAction.INSTALL -> MyFinHubOutlinedAction("Εγκατάσταση ξανά", onInstall, Modifier.fillMaxWidth())
                        UpdateRecoveryAction.INSTALL_PERMISSION -> MyFinHubOutlinedAction("Άνοιγμα ρύθμισης εγκατάστασης", onOpenInstallPermission, Modifier.fillMaxWidth())
                        UpdateRecoveryAction.CHECK -> MyFinHubOutlinedAction("Έλεγχος ξανά", onCheck, Modifier.fillMaxWidth())
                    }
                }
            }
            Text(
                "Πριν από εγκατάσταση ελέγχονται πηγή, ακεραιότητα, πακέτο, νεότερη έκδοση και υπογραφή.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusText(value: String) {
    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ReleaseCopy(versionName: String, sizeBytes: Long, notes: String, mandatory: Boolean) {
    Text(
        if (mandatory) "Απαιτείται ενημέρωση · έκδοση $versionName" else "Νέα έκδοση $versionName διαθέσιμη",
        style = MaterialTheme.typography.titleMedium,
        color = if (mandatory) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
    Text(formatBytes(sizeBytes), style = MaterialTheme.typography.labelLarge)
    if (notes.isNotBlank()) {
        Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

internal fun updateFailureMessage(kind: UpdateFailureKind): String = when (kind) {
    UpdateFailureKind.BUILD_NOT_CONFIGURED -> "Η υπηρεσία ενημερώσεων δεν είναι διαθέσιμη σε αυτή την έκδοση."
    UpdateFailureKind.AUTH_REQUIRED -> "Χρειάζεται νέα σύνδεση λογαριασμού πριν τον έλεγχο ενημέρωσης."
    UpdateFailureKind.MFA_REQUIRED -> "Χρειάζεται επαλήθευση λογαριασμού πριν τον έλεγχο ενημέρωσης."
    UpdateFailureKind.NETWORK -> "Δεν ήταν δυνατή η σύνδεση με την υπηρεσία ενημερώσεων."
    UpdateFailureKind.SERVER -> "Η υπηρεσία ενημερώσεων δεν είναι προσωρινά διαθέσιμη."
    UpdateFailureKind.MALFORMED_METADATA -> "Τα στοιχεία της διαθέσιμης ενημέρωσης δεν είναι έγκυρα."
    UpdateFailureKind.INSECURE_DOWNLOAD -> "Η πηγή λήψης της ενημέρωσης απορρίφθηκε για λόγους ασφαλείας."
    UpdateFailureKind.DOWNLOAD_SIZE_MISMATCH,
    UpdateFailureKind.DOWNLOAD_DIGEST_MISMATCH -> "Το αρχείο δεν πέρασε τον έλεγχο ακεραιότητας και δεν μπορεί να εγκατασταθεί."
    UpdateFailureKind.WRONG_PACKAGE,
    UpdateFailureKind.WRONG_VERSION,
    UpdateFailureKind.WRONG_SIGNER,
    UpdateFailureKind.PACKAGE_UNREADABLE -> "Το αρχείο δεν αναγνωρίστηκε ως έγκυρη νεότερη έκδοση του MyFinHub και δεν μπορεί να εγκατασταθεί."
    UpdateFailureKind.INSTALL_PERMISSION_REQUIRED -> "Απαιτείται άδεια εγκατάστασης ιδιωτικών ενημερώσεων."
    UpdateFailureKind.INSTALL_BLOCKED -> "Το Android εμπόδισε την έναρξη της εγκατάστασης."
    UpdateFailureKind.INSTALL_FAILED -> "Η εγκατάσταση δεν ολοκληρώθηκε. Το υπάρχον MyFinHub παραμένει εγκατεστημένο."
}
''',
)

# ---------------------------------------------------------------------------
# S9.3 auth: concise Greek terminology, IME-safe surfaces, live retry countdown.
# ---------------------------------------------------------------------------
auth_path = "app/src/main/java/app/myfinhub/android/feature/auth/AuthShellScreen.kt"
text = read(auth_path)
text = text.replace(
    "import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.padding\n",
    "import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.imePadding\nimport androidx.compose.foundation.layout.navigationBarsPadding\nimport androidx.compose.foundation.layout.padding\n",
    1,
)
text = text.replace(
    "import app.myfinhub.android.designsystem.MyFinHubSpacing\n",
    "import app.myfinhub.android.designsystem.MyFinHubSpacing\nimport kotlinx.coroutines.delay\n",
    1,
)
text = text.replace(
    '''private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}''',
    '''private fun LoadingScreen() {
    AuthSurface {
        Text("MyFinHub", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text("Έλεγχος ασφαλούς συνεδρίας…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}''',
    1,
)
text = text.replace("Text(\"Επαλήθευση δύο παραγόντων\"", "Text(\"Επαλήθευση λογαριασμού\"", 1)
text = text.replace(
    'Text("Άνοιξε την εφαρμογή authenticator και πληκτρολόγησε τον τρέχοντα κωδικό TOTP.")',
    'Text("Άνοιξε την εφαρμογή επαλήθευσης και πληκτρολόγησε τον εξαψήφιο κωδικό.")',
    1,
)
text = text.replace('label = "Κωδικός TOTP",', 'label = "Εξαψήφιος κωδικός",', 1)
text = text.replace(
    '"Διάλεξε 4–12 ψηφία για fallback όταν δεν είναι διαθέσιμα τα βιομετρικά. Το PIN ξεκλειδώνει μόνο την εφαρμογή και δεν αντικαθιστά το TOTP.",',
    '"Διάλεξε 4–12 ψηφία. Το PIN ξεκλειδώνει μόνο αυτή την εφαρμογή και χρησιμοποιείται όταν δεν είναι διαθέσιμα τα βιομετρικά.",',
    1,
)
text = text.replace(
    '"Η αποθηκευμένη συνεδρία θα ελεγχθεί ξανά στον server μετά το τοπικό ξεκλείδωμα.",',
    '"Ξεκλείδωσε την εφαρμογή με βιομετρικά ή με το τοπικό PIN.",',
    1,
)
old = '''    var pin by remember { mutableStateOf("") }
    AuthSurface {
        Text("Το MyFinHub είναι κλειδωμένο", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })'''
new = '''    var pin by remember { mutableStateOf("") }
    var retryRemainingMillis by remember(state.pinStatus.retryAfterMillis) {
        mutableStateOf(state.pinStatus.retryAfterMillis)
    }
    LaunchedEffect(state.pinStatus.retryAfterMillis) {
        retryRemainingMillis = state.pinStatus.retryAfterMillis
        while (retryRemainingMillis > 0L) {
            delay(minOf(1_000L, retryRemainingMillis))
            retryRemainingMillis = (retryRemainingMillis - 1_000L).coerceAtLeast(0L)
        }
    }
    val pinAllowedNow = state.pinStatus.allowed || retryRemainingMillis <= 0L
    AuthSurface {
        Text("Το MyFinHub είναι κλειδωμένο", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })'''
if text.count(old) != 1:
    raise SystemExit("AuthShellScreen locked insertion mismatch")
text = text.replace(old, new, 1)
text = text.replace(
    '''            if (!state.pinStatus.allowed) {
                Text(
                    "Το PIN fallback είναι προσωρινά κλειδωμένο.",
                    color = MaterialTheme.colorScheme.error,
                )
            }''',
    '''            if (!pinAllowedNow) {
                Text(
                    "Πολλές αποτυχημένες προσπάθειες. Δοκίμασε ξανά σε ${formatRetrySeconds(retryRemainingMillis)}.",
                    color = MaterialTheme.colorScheme.error,
                )
            }''',
    1,
)
text = text.replace(
    "enabled = pin.length >= 4 && state.pinStatus.allowed,",
    "enabled = pin.length >= 4 && pinAllowedNow,",
    1,
)
text = text.replace(
    '''                    .fillMaxWidth()
                    .widthIn(max = MyFinHubDesignMetrics.authContentMaxWidth)
                    .verticalScroll(rememberScrollState()),''',
    '''                    .fillMaxWidth()
                    .widthIn(max = MyFinHubDesignMetrics.authContentMaxWidth)
                    .imePadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),''',
    1,
)
text += '''\ninternal fun formatRetrySeconds(retryAfterMillis: Long): String {\n    val seconds = ((retryAfterMillis.coerceAtLeast(0L) + 999L) / 1_000L).coerceAtLeast(1L)\n    return if (seconds == 1L) \"1 δευτερόλεπτο\" else \"$seconds δευτερόλεπτα\"\n}\n'''
write(auth_path, text)

# Consumer-facing diagnostics summary must not surface auth implementation jargon.
replace_once(
    "app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt",
    '''    is AuthShellUiState.Mfa -> "Απαιτεί AAL2"
    is AuthShellUiState.PinEnrollment -> "Ρύθμιση τοπικού PIN"
    is AuthShellUiState.Locked -> "Τοπικά κλειδωμένη"
    is AuthShellUiState.Ready -> if (state.offline) {
        "Τοπικά ξεκλειδωμένη · αναμονή server ελέγχου"
    } else {
        "Ενεργή · ${state.session.assuranceLevel.name}"
    }''',
    '''    is AuthShellUiState.Mfa -> "Απαιτεί επαλήθευση λογαριασμού"
    is AuthShellUiState.PinEnrollment -> "Ρύθμιση τοπικού PIN"
    is AuthShellUiState.Locked -> "Η εφαρμογή είναι κλειδωμένη"
    is AuthShellUiState.Ready -> if (state.offline) {
        "Τοπικά διαθέσιμη · αναμονή επαλήθευσης σύνδεσης"
    } else {
        "Ενεργή και επαληθευμένη"
    }''',
)

# ---------------------------------------------------------------------------
# Navigation: dedicated diagnostics destination; synthetic ChangeHistory remains
# non-production and is not linked from ProductionSettingsScreen.
# ---------------------------------------------------------------------------
replace_once(
    "app/src/main/java/app/myfinhub/android/app/AppRoute.kt",
    "    @Serializable data object Settings : AppRoute\n    @Serializable data object ChangeHistory : AppRoute",
    "    @Serializable data object Settings : AppRoute\n    @Serializable data object Diagnostics : AppRoute\n    @Serializable data object ChangeHistory : AppRoute",
)
app_path = "app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt"
text = read(app_path)
text = text.replace(
    "import app.myfinhub.android.feature.utilities.ProductionSettingsScreen\n",
    "import app.myfinhub.android.feature.utilities.ProductionDiagnosticsScreen\nimport app.myfinhub.android.feature.utilities.ProductionSettingsScreen\n",
    1,
)
text = text.replace(
    '''                            onOpenNoticeHistory = { homeBackStack.pushIfNew(AppRoute.NoticeHistory) },
                            onLogout = onLogout,''',
    '''                            onOpenNoticeHistory = { homeBackStack.pushIfNew(AppRoute.NoticeHistory) },
                            onOpenDiagnostics = { homeBackStack.pushIfNew(AppRoute.Diagnostics) },
                            onLogout = onLogout,''',
    1,
)
marker = '''                entry<AppRoute.NoticeHistory> {
                    NoticeHistoryScreen(
                        entries = noticeHistory,
                        onBack = { homeBackStack.removeLastOrNull() },
                    )
                }
'''
addition = marker + '''                entry<AppRoute.Diagnostics> {
                    diagnostics?.let { snapshot ->
                        ProductionDiagnosticsScreen(
                            diagnostics = snapshot,
                            onBack = { homeBackStack.removeLastOrNull() },
                        )
                    } ?: run {
                        homeBackStack.removeLastOrNull()
                    }
                }
'''
if text.count(marker) != 1:
    raise SystemExit("MyFinHubApp notice route marker mismatch")
write(app_path, text.replace(marker, addition, 1))

# Notice history wording stays explicitly non-financial and consumer-facing.
replace_once(
    "app/src/main/java/app/myfinhub/android/feature/utilities/NoticeHistoryScreen.kt",
    'subtitle = "Ασφαλές ιστορικό κατάστασης εφαρμογής",',
    'subtitle = "Συμβάντα εφαρμογής για ενημέρωση και υποστήριξη",',
)

# ---------------------------------------------------------------------------
# Tests for update recovery, diagnostics sanitization, retry timing and routing.
# ---------------------------------------------------------------------------
write(
    "app/src/test/java/app/myfinhub/android/feature/utilities/S9UtilitiesContractTest.kt",
    r'''package app.myfinhub.android.feature.utilities

import app.myfinhub.android.core.update.UpdateFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S9UtilitiesContractTest {
    @Test
    fun updater_hasOneDeterministicRecoveryPerFailureFamily() {
        assertEquals(UpdateRecoveryAction.AUTH, updateRecoveryAction(UpdateFailureKind.AUTH_REQUIRED, false))
        assertEquals(UpdateRecoveryAction.AUTH, updateRecoveryAction(UpdateFailureKind.MFA_REQUIRED, false))
        assertEquals(UpdateRecoveryAction.DOWNLOAD, updateRecoveryAction(UpdateFailureKind.DOWNLOAD_DIGEST_MISMATCH, true))
        assertEquals(UpdateRecoveryAction.INSTALL_PERMISSION, updateRecoveryAction(UpdateFailureKind.INSTALL_PERMISSION_REQUIRED, true))
        assertEquals(UpdateRecoveryAction.INSTALL, updateRecoveryAction(UpdateFailureKind.INSTALL_FAILED, true))
        assertEquals(UpdateRecoveryAction.CHECK, updateRecoveryAction(UpdateFailureKind.WRONG_SIGNER, true))
    }

    @Test
    fun diagnosticsCopy_isSanitizedSupportMetadataOnly() {
        val text = diagnosticsSupportText(
            AppDiagnosticsSnapshot(
                versionName = "1.0",
                buildType = "release",
                environment = "Production",
                apiHost = "example.invalid",
                networkStatus = "Συνδεδεμένο",
                apiStatus = "Συγχρονισμένο",
                sessionStatus = "Ενεργή και επαληθευμένη",
                lastSuccessfulSync = null,
                lastDiagnosticCode = "NET-001",
            ),
        )
        assertTrue(text.contains("NET-001"))
        assertFalse(text.contains("PAN", ignoreCase = true))
        assertFalse(text.contains("CVV", ignoreCase = true))
        assertFalse(text.contains("password", ignoreCase = true))
        assertFalse(text.contains("amount", ignoreCase = true))
    }
}
''',
)

write(
    "app/src/test/java/app/myfinhub/android/feature/auth/S9AuthPresentationTest.kt",
    r'''package app.myfinhub.android.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class S9AuthPresentationTest {
    @Test
    fun retryTiming_roundsUpAndNeverClaimsZeroSeconds() {
        assertEquals("1 δευτερόλεπτο", formatRetrySeconds(1))
        assertEquals("1 δευτερόλεπτο", formatRetrySeconds(1_000))
        assertEquals("2 δευτερόλεπτα", formatRetrySeconds(1_001))
        assertEquals("30 δευτερόλεπτα", formatRetrySeconds(30_000))
    }
}
''',
)

# ---------------------------------------------------------------------------
# Fresh S9 screenshot fixtures: settings, diagnostics, notices and auth states.
# ---------------------------------------------------------------------------
write(
    "app/src/screenshotTest/kotlin/app/myfinhub/android/feature/utilities/S9UtilitiesScreenshotTest.kt",
    r'''package app.myfinhub.android.feature.utilities

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private fun s9Diagnostics() = AppDiagnosticsSnapshot(
    versionName = "1.0.0-rc8",
    buildType = "release",
    environment = "Production",
    apiHost = "api.myfinhub.example",
    networkStatus = "Συνδεδεμένο",
    apiStatus = "Συγχρονισμένο",
    sessionStatus = "Ενεργή και επαληθευμένη",
    lastSuccessfulSync = null,
    lastDiagnosticCode = "SYNC-READY",
)

@PreviewTest
@Preview(name = "s9_settings_light", widthDp = 412, heightDp = 1050, showBackground = true)
@Composable
fun S9SettingsLight() = MyFinHubTheme(false) {
    ProductionSettingsScreen(
        state = FrontendUtilitiesUiState(),
        onAction = {},
        onBack = {},
        diagnostics = s9Diagnostics(),
        noticeHistoryCount = 3,
        onLogout = {},
    )
}

@PreviewTest
@Preview(name = "s9_settings_dark", widthDp = 412, heightDp = 1050, showBackground = true)
@Composable
fun S9SettingsDark() = MyFinHubTheme(true) {
    ProductionSettingsScreen(
        state = FrontendUtilitiesUiState(), onAction = {}, onBack = {}, diagnostics = s9Diagnostics(), noticeHistoryCount = 3, onLogout = {},
    )
}

@PreviewTest
@Preview(name = "s9_settings_large", widthDp = 412, heightDp = 1400, fontScale = 1.5f, showBackground = true)
@Composable
fun S9SettingsLarge() = MyFinHubTheme(false) {
    ProductionSettingsScreen(
        state = FrontendUtilitiesUiState(), onAction = {}, onBack = {}, diagnostics = s9Diagnostics(), noticeHistoryCount = 3, onLogout = {},
    )
}

@PreviewTest
@Preview(name = "s9_diagnostics_light", widthDp = 412, heightDp = 1050, showBackground = true)
@Composable
fun S9DiagnosticsLight() = MyFinHubTheme(false) { ProductionDiagnosticsScreen(s9Diagnostics(), {}) }

@PreviewTest
@Preview(name = "s9_diagnostics_dark", widthDp = 412, heightDp = 1050, showBackground = true)
@Composable
fun S9DiagnosticsDark() = MyFinHubTheme(true) { ProductionDiagnosticsScreen(s9Diagnostics(), {}) }

@PreviewTest
@Preview(name = "s9_diagnostics_large", widthDp = 412, heightDp = 1350, fontScale = 1.5f, showBackground = true)
@Composable
fun S9DiagnosticsLarge() = MyFinHubTheme(false) { ProductionDiagnosticsScreen(s9Diagnostics(), {}) }
''',
)

# Update tracking to an implementation candidate, never completion before evidence.
tracking_path = ROOT / "tracking/android-project-state.json"
tracking = json.loads(tracking_path.read_text())
tracking["updated_at"] = "2026-09-15"
work = tracking["active_workstream"]
work["status"] = "android_redesign_s9_implementation_candidate"
work["summary"] = (
    "S1–S8 are complete and merged. S9 now has a coherent Android-only implementation candidate for consumer-first Settings, "
    "dedicated sanitized diagnostics, deterministic updater recovery and focused auth/lockout presentation. S9 remains unaccepted "
    "until focused validation, fresh Compose render inspection and exact-PR-head gates pass."
)
work["next"] = [
    "Run focused S9 unit/instrumentation-compile/lint/Kotlin checks and inspect fresh Settings/Diagnostics light/dark/150% renders.",
    "Then validate auth/root/update device contracts, open the S9 draft PR and require exact-head Android CI, Project Tracking and Android UI Quality before completion.",
]
current = tracking["current_redesign_pass"]
current["branch"] = "android/redesign-s9-settings-auth"
current["pr"] = None
current["current_slice"] = "S9.1 / S9.2 / S9.3 / S9.4"
current["checkpoint"] = "s9_implementation_candidate"
current["next_action"] = (
    "Validate the coherent S9 Settings/auth/root candidate, inspect fresh renders, then open a draft PR and complete S9 only from exact-head hosted evidence."
)
tracking_path.write_text(json.dumps(tracking, ensure_ascii=False, indent=2) + "\n")

import subprocess
subprocess.run(["python3", "scripts/render_project_tracking.py"], check=True)
