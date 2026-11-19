package app.myfinhub.android.feature.utilities

import android.content.Intent
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
import androidx.compose.runtime.Composable
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
import app.myfinhub.android.designsystem.MyFinHubSettingsActionRow
import app.myfinhub.android.designsystem.MyFinHubSettingsDivider
import app.myfinhub.android.designsystem.MyFinHubSettingsSection
import app.myfinhub.android.designsystem.MyFinHubSettingsSwitchRow
import app.myfinhub.android.designsystem.MyFinHubSpacing

/** Local preferences and Android settings; long-form financial administration remains on Web. */
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
    var appearance by remember { mutableStateOf(AppAppearancePreference.read(context)) }
    var amountsVisible by remember { mutableStateOf(AmountVisibilityPreference.read(context)) }

    val openAndroidDetails = {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)),
            )
        }
        Unit
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
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = MyFinHubDesignMetrics.screenHorizontalPadding, vertical = MyFinHubSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
        ) {
            MyFinHubSettingsSection("Εμφάνιση") {
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
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
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
                MyFinHubSettingsDivider()
                MyFinHubSettingsSwitchRow(
                    label = "Εμφάνιση ποσών",
                    checked = amountsVisible,
                    onCheckedChange = {
                        amountsVisible = it
                        AmountVisibilityPreference.write(context, it)
                    },
                    supportingText = if (amountsVisible) null else "Τα ποσά εμφανίζονται κρυμμένα.",
                )
            }

            MyFinHubSettingsSection("Ασφάλεια και ειδοποιήσεις") {
                MyFinHubSettingsActionRow(
                    label = "Ειδοποιήσεις Android",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(intent) }.onFailure { openAndroidDetails() }
                    },
                )
                onOpenNoticeHistory?.let { openHistory ->
                    MyFinHubSettingsDivider()
                    MyFinHubSettingsActionRow(
                        label = "Ιστορικό ειδοποιήσεων",
                        trailingText = noticeHistoryCount.takeIf { it > 0 }?.toString(),
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

            MyFinHubSettingsSection("Σχετικά") {
                Column(modifier = Modifier.padding(horizontal = MyFinHubSpacing.sm, vertical = MyFinHubSpacing.xs)) {
                    Text("MyFinHub Android", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Έκδοση ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                onOpenDiagnostics?.let { openDiagnostics ->
                    MyFinHubSettingsDivider()
                    MyFinHubSettingsActionRow(
                        label = "Διαγνωστικά και υποστήριξη",
                        onClick = openDiagnostics,
                        modifier = Modifier.testTag("mobile_settings_diagnostics"),
                    )
                }
                MyFinHubSettingsDivider()
                MyFinHubSettingsActionRow(
                    label = "Πληροφορίες και άδειες Android",
                    onClick = openAndroidDetails,
                )
            }

            onLogout?.let { logout ->
                MyFinHubSettingsSection("Λογαριασμός") {
                    MyFinHubSettingsActionRow(
                        label = "Αποσύνδεση από αυτή τη συσκευή",
                        onClick = logout,
                        destructive = true,
                    )
                }
            }
        }
    }
}
