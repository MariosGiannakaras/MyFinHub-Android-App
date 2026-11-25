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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.BuildConfig
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
