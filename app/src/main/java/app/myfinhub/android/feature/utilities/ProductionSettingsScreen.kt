package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing

/** Production settings put everyday preferences first and keep engineering diagnostics secondary. */
@Composable
fun ProductionSettingsScreen(
    state: FrontendUtilitiesUiState,
    onAction: (FrontendUtilitiesAction) -> Unit,
    onBack: () -> Unit,
    diagnostics: AppDiagnosticsSnapshot? = null,
    onLogout: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var appearance by remember { mutableStateOf(AppAppearancePreference.read(context)) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Ρυθμίσεις",
                subtitle = "Εμφάνιση, ιδιωτικότητα και λογαριασμός",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Text("Εμφάνιση", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Διάλεξε θέμα για αυτή τη συσκευή.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppAppearance.entries.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        ) {
                            RadioButton(
                                selected = appearance == option,
                                onClick = {
                                    appearance = option
                                    AppAppearancePreference.write(context, option)
                                },
                                modifier = Modifier.semantics { contentDescription = "Θέμα ${option.label}" },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when (option) {
                                        AppAppearance.SYSTEM -> "Ακολουθεί το φωτεινό ή σκούρο θέμα του κινητού."
                                        AppAppearance.LIGHT -> "Χρησιμοποιεί πάντα φωτεινό θέμα."
                                        AppAppearance.DARK -> "Χρησιμοποιεί πάντα σκούρο θέμα."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (index != AppAppearance.entries.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Text("Ιδιωτικότητα", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    PreferenceSwitchRow(
                        title = "Απόκρυψη ποσών στην εκκίνηση",
                        subtitle = "Ξεκινά την Αρχική με κρυμμένα ποσά.",
                        checked = state.settings.hideAmountsOnStart,
                        iconTone = FinanceTone.Neutral,
                        onChanged = { onAction(FrontendUtilitiesAction.ToggleHideAmountsOnStart) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PreferenceSwitchRow(
                        title = "Επιβεβαίωση ευαίσθητων ενεργειών",
                        subtitle = "Πρόσθετη επιβεβαίωση πριν από ενέργειες με ευαίσθητα στοιχεία.",
                        checked = state.settings.extraSensitiveScreenCheck,
                        iconTone = FinanceTone.Neutral,
                        onChanged = { onAction(FrontendUtilitiesAction.ToggleExtraSensitiveScreenCheck) },
                    )
                }
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Text("Υπενθυμίσεις", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    PreferenceSwitchRow(
                        title = "Επερχόμενες υποχρεώσεις",
                        subtitle = "Εμφάνιση υπενθυμίσεων για πραγματικές προγραμματισμένες υποχρεώσεις.",
                        checked = state.settings.remindersEnabled,
                        iconTone = FinanceTone.Attention,
                        onChanged = { onAction(FrontendUtilitiesAction.ToggleReminders) },
                    )
                }
            }

            onLogout?.let { logout ->
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text("Λογαριασμός", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Η αποσύνδεση κλείνει την ενεργή συνεδρία σε αυτή τη συσκευή.",
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

            diagnostics?.let { snapshot ->
                MyFinHubOutlinedAction(
                    label = if (diagnosticsExpanded) "Απόκρυψη τεχνικών πληροφοριών" else "Τεχνικές πληροφορίες",
                    onClick = { diagnosticsExpanded = !diagnosticsExpanded },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (diagnosticsExpanded) DiagnosticsCard(snapshot)
            }
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    iconTone: FinanceTone,
    onChanged: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
    ) {
        MyFinHubIconBadge(
            icon = if (iconTone == FinanceTone.Attention) MyFinHubIcons.Attention else MyFinHubIcons.Goal,
            tone = iconTone,
            contentDescription = null,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onChanged() },
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}