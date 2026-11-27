package app.myfinhub.android.feature.utilities

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.BuildConfig
import app.myfinhub.android.app.DeviceSessionsUiSnapshot
import app.myfinhub.android.core.network.normalizedDeviceNickname
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.core.update.LocalUpdateController
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSettingsLinkRow
import app.myfinhub.android.designsystem.MyFinHubSettingsSection
import app.myfinhub.android.designsystem.MyFinHubSettingsToggleRow
import app.myfinhub.android.designsystem.MyFinHubSpacing

/** Local Android preferences and app information; no Web financial administration. */
@Composable
fun MobileDeviceSettingsScreen(
    onBack: () -> Unit,
    onLogout: (() -> Unit)?,
    noticeHistoryCount: Int = 0,
    onOpenNoticeHistory: (() -> Unit)? = null,
    onOpenDiagnostics: (() -> Unit)? = null,
    deviceSessions: DeviceSessionsUiSnapshot = DeviceSessionsUiSnapshot(),
    onRenameDeviceSession: ((String, String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val updater = LocalUpdateController.current
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val preferences = remember(context) {
        context.applicationContext.getSharedPreferences(
            AppAppearancePreference.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
    }
    var appearance by remember(context) { mutableStateOf(AppAppearancePreference.read(context)) }
    var amountsVisible by remember(context) { mutableStateOf(AmountVisibilityPreference.read(context)) }
    // Home has a quick privacy toggle: keep a retained Settings route in sync.
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                AppAppearancePreference.KEY -> appearance = AppAppearancePreference.read(context)
                AmountVisibilityPreference.KEY -> amountsVisible = AmountVisibilityPreference.read(context)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Ρυθμίσεις",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = MyFinHubDesignMetrics.screenHorizontalPadding,
                    vertical = MyFinHubSpacing.sm,
                ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
        ) {
            MyFinHubSettingsSection(title = "Εμφάνιση") {
                val appearanceOption: @Composable (AppAppearance, Modifier) -> Unit = { option, modifier ->
                    FilterChip(
                        selected = appearance == option,
                        onClick = {
                            appearance = option
                            AppAppearancePreference.write(context, option)
                        },
                        label = { Text(option.label) },
                        modifier = modifier.heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget),
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
                MyFinHubSettingsToggleRow(
                    label = "Εμφάνιση ποσών",
                    checked = amountsVisible,
                    onCheckedChange = { visible ->
                        amountsVisible = visible
                        AmountVisibilityPreference.write(context, visible)
                    },
                    supporting = "Η απόκρυψη καλύπτει ποσά, όχι γραφήματα ή την κατεύθυνση των μεταβολών.",
                )
            }

            MyFinHubSettingsSection(title = "Ασφάλεια και ειδοποιήσεις") {
                Text(
                    "Το PIN και τα βιομετρικά προστατεύουν την υπάρχουσα συνεδρία.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MyFinHubSettingsLinkRow(
                    label = "Ειδοποιήσεις Android",
                    supporting = "Διαχείριση από τις ρυθμίσεις συσκευής",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(intent) }.onFailure {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + context.packageName),
                                    ),
                                )
                            }
                        }
                    },
                )
                onOpenNoticeHistory?.let { openHistory ->
                    MyFinHubSettingsLinkRow(
                        label = if (noticeHistoryCount > 0) {
                            "Ιστορικό ειδοποιήσεων · $noticeHistoryCount"
                        } else {
                            "Ιστορικό ειδοποιήσεων"
                        },
                        onClick = openHistory,
                        modifier = Modifier.testTag("mobile_settings_notice_history"),
                    )
                }
            }

            if (deviceSessions.loaded && deviceSessions.devices.any { it.defaultLabel != null }) {
                DeviceNicknameSettings(
                    snapshot = deviceSessions,
                    onRename = onRenameDeviceSession,
                )
            }

            if (BuildConfig.SELF_UPDATE_ENABLED) {
                UpdateSettingsCard(
                    currentVersionName = BuildConfig.VERSION_NAME,
                    state = updater.state,
                    onCheck = updater.check,
                    onDownload = updater.download,
                    onInstall = updater.install,
                    onOpenInstallPermission = updater.openInstallPermission,
                    onAuthRecovery = onLogout ?: {},
                )
            }

            MyFinHubSettingsSection(title = "Σχετικά με την εφαρμογή") {
                Text(
                    "MyFinHub Android · Έκδοση " + BuildConfig.VERSION_NAME + " (build " + BuildConfig.VERSION_CODE + ")",
                    style = MaterialTheme.typography.bodyMedium,
                )
                onOpenDiagnostics?.let { openDiagnostics ->
                    MyFinHubSettingsLinkRow(
                        label = "Διαγνωστικά και υποστήριξη",
                        onClick = openDiagnostics,
                        modifier = Modifier.testTag("mobile_settings_diagnostics"),
                    )
                }
                MyFinHubSettingsLinkRow(
                    label = "Πληροφορίες και άδειες Android",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + context.packageName),
                                ),
                            )
                        }
                    },
                )
            }

            onLogout?.let { logout ->
                TextButton(
                    onClick = logout,
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget),
                ) {
                    Text(
                        "Αποσύνδεση από αυτή τη συσκευή",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Only server-confirmed, uniquely identified devices from the current authenticated frame. */
@Composable
private fun DeviceNicknameSettings(
    snapshot: DeviceSessionsUiSnapshot,
    onRename: ((String, String) -> Unit)?,
) {
    val busy = snapshot.renamingSessionId != null || snapshot.revokingSessionId != null
    var editingId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    val editing = snapshot.devices.singleOrNull { it.sessionId == editingId && it.defaultLabel != null }
    LaunchedEffect(snapshot.loaded, snapshot.devices, busy, onRename == null) {
        if (!snapshot.loaded || editing == null || busy || onRename == null) {
            editingId = null
            draft = ""
        }
    }
    if (snapshot.loaded && editing != null && !busy && onRename != null) {
        val normalized = normalizedDeviceNickname(draft)
        AlertDialog(
            onDismissRequest = { editingId = null; draft = "" },
            title = { Text("Όνομα συσκευής") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Text(editing.defaultLabel.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    MyFinHubOutlinedField(
                        label = "Προαιρετικό όνομα (έως 60 χαρακτήρες)",
                        value = draft,
                        onValueChange = { if (it.length <= 120) draft = it },
                        modifier = Modifier.testTag("device_nickname_field"),
                    )
                    Text("Κενό όνομα επαναφέρει την προεπιλεγμένη ονομασία.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = normalized != null,
                    onClick = {
                        val target = snapshot.devices.singleOrNull { it.sessionId == editingId && it.defaultLabel != null }
                        val name = normalizedDeviceNickname(draft)
                        editingId = null
                        draft = ""
                        if (target != null && name != null && snapshot.loaded && !busy) {
                            onRename(target.sessionId, name)
                        }
                    },
                    modifier = Modifier.testTag("device_nickname_confirm"),
                ) { Text("Αποθήκευση") }
            },
            dismissButton = {
                TextButton(onClick = { editingId = null; draft = "" }) { Text("Ακύρωση") }
            },
        )
    }
    MyFinHubSettingsSection(title = "Συνδεδεμένες συσκευές") {
        if (snapshot.devices.isEmpty()) {
            Text("Δεν υπάρχουν διαθέσιμες καταγεγραμμένες συσκευές.")
        } else snapshot.devices.forEach { device ->
            val details = listOfNotNull(
                device.systemName,
                device.browserName,
                device.deviceType,
            ).distinct().joinToString(" · ")
            val supporting = listOfNotNull(
                device.platform,
                details.takeIf(String::isNotBlank),
                "Αυτή η συνεδρία".takeIf { device.current },
            ).joinToString(" · ")
            if (device.defaultLabel != null && onRename != null && !busy) {
                MyFinHubSettingsLinkRow(
                    label = device.label,
                    supporting = supporting,
                    onClick = {
                        draft = device.nickname.orEmpty()
                        editingId = device.sessionId
                    },
                    modifier = Modifier.testTag("device_nickname_" + device.sessionId),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                    Text(device.label, style = MaterialTheme.typography.bodyMedium)
                    Text(supporting, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
