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
import app.myfinhub.android.BuildConfig
import app.myfinhub.android.core.update.LocalUpdateController
import app.myfinhub.android.designsystem.MyFinHubBackButton
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
    onLogout: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val updateController = LocalUpdateController.current
    var appearance by remember { mutableStateOf(AppAppearancePreference.read(context)) }
    var amountsVisible by remember { mutableStateOf(AmountVisibilityPreference.read(context)) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Ρυθμίσεις",
                subtitle = "Εμφάνιση και λογαριασμός",
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
                        "Οι επιλογές αποθηκεύονται σε αυτή τη συσκευή και εφαρμόζονται αμέσως.",
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Εμφάνιση ποσών", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Εμφανίζει υπόλοιπα και ποσά στην Αρχική. Όταν είναι κλειστό, παραμένουν καλυμμένα.",
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

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Text("Ιδιωτικότητα", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Τα ασφαλή στοιχεία κάρτας εμφανίζονται μόνο μετά από την απαιτούμενη επαλήθευση. Το CVV παραμένει κρυπτογραφημένο μόνο στη συσκευή και τα screenshots μπλοκάρονται όσο προβάλλονται μυστικά κάρτας.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Δεν εμφανίζονται διακόπτες για μυστικά κάρτας χωρίς ενεργή production συμπεριφορά.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Text("Ειδοποιήσεις", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Το τοπικό ιστορικό κρατά μόνο ασφαλή κατηγορία, διαγνωστικό κωδικό και χρόνο. Επαναλαμβανόμενες ίδιες ειδοποιήσεις συμπτύσσονται.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MyFinHubOutlinedAction(
                        label = if (noticeHistoryCount == 0) "Ιστορικό ειδοποιήσεων" else "Ιστορικό ειδοποιήσεων ($noticeHistoryCount)",
                        onClick = onOpenNoticeHistory,
                        modifier = Modifier.fillMaxWidth(),
                        icon = MyFinHubIcons.Activity,
                    )
                }
            }

            UpdateSettingsCard(
                currentVersionName = BuildConfig.VERSION_NAME,
                state = updateController.state,
                onCheck = updateController.check,
                onDownload = updateController.download,
                onInstall = updateController.install,
                onOpenInstallPermission = updateController.openInstallPermission,
            )

            onLogout?.let { logout ->
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text("Λογαριασμός", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Η αποσύνδεση κλείνει την ενεργή συνεδρία σε αυτή τη συσκευή. Τα συγχρονισμένα οικονομικά δεδομένα παραμένουν στον λογαριασμό σου.",
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
