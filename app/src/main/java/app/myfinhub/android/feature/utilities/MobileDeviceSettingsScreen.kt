package app.myfinhub.android.feature.utilities

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.BuildConfig
import app.myfinhub.android.core.update.LocalUpdateController
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubOutlinedAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing

/** Owner-approved phone-only preferences; no web finance administration. */
@Composable
fun MobileDeviceSettingsScreen(onBack: () -> Unit, onLogout: (() -> Unit)?) {
    val context = LocalContext.current
    val updater = LocalUpdateController.current
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    var appearance by remember { mutableStateOf(AppAppearancePreference.read(context)) }
    var amountsVisible by remember { mutableStateOf(AmountVisibilityPreference.read(context)) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Εφαρμογή",
                subtitle = "Ρυθμίσεις συσκευής",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = MyFinHubDesignMetrics.screenHorizontalPadding, vertical = MyFinHubSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
        ) {
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text("Εμφάνιση", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    val option: @Composable (AppAppearance, Modifier) -> Unit = { item, modifier ->
                        FilterChip(
                            selected = appearance == item,
                            onClick = {
                                appearance = item
                                AppAppearancePreference.write(context, item)
                            },
                            label = { Text(item.label) },
                            modifier = modifier,
                        )
                    }
                    if (largeFont) {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            AppAppearance.entries.forEach { option(it, Modifier.fillMaxWidth()) }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            AppAppearance.entries.forEach { option(it, Modifier.weight(1f)) }
                        }
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Εμφάνιση ποσών", style = MaterialTheme.typography.titleMedium)
                            Text("Ισχύει στις οικονομικές οθόνες.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = amountsVisible,
                            onCheckedChange = {
                                amountsVisible = it
                                AmountVisibilityPreference.write(context, it)
                            },
                        )
                    }
                }
            }
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text("Ασφάλεια και ειδοποιήσεις", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Το PIN και η βιομετρική προστασία εφαρμόζονται μέσω της υπάρχουσας ασφαλούς ροής εισόδου.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MyFinHubOutlinedAction(
                        label = "Ειδοποιήσεις Android",
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            runCatching { context.startActivity(intent) }.onFailure {
                                runCatching {
                                    context.startActivity(Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + context.packageName),
                                    ))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
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
            Text("MyFinHub " + BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            onLogout?.let { logout ->
                MyFinHubOutlinedAction(
                    label = "Αποσύνδεση από αυτή τη συσκευή",
                    onClick = logout,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
