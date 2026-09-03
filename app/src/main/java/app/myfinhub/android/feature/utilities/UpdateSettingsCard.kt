package app.myfinhub.android.feature.utilities

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

@Composable
internal fun UpdateSettingsCard(
    currentVersionName: String,
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenInstallPermission: () -> Unit,
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
                    StatusText("Ο αυτόματος έλεγχος γίνεται όταν η ασφαλής συνεδρία είναι έτοιμη.")
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
                    StatusText("Το Android χρειάζεται να επιτρέψει στο MyFinHub να εγκαθιστά τις ιδιωτικές ενημερώσεις του.")
                    MyFinHubPrimaryAction("Άνοιγμα ρύθμισης εγκατάστασης", onOpenInstallPermission, Modifier.fillMaxWidth(), icon = null)
                    MyFinHubOutlinedAction("Έλεγχος άδειας και εγκατάσταση", onInstall, Modifier.fillMaxWidth())
                }
                is UpdateUiState.Installing -> {
                    StatusText("Η εγκατάσταση της έκδοσης ${state.release.versionName} ξεκίνησε. Το Android μπορεί να ζητήσει επιβεβαίωση.")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is UpdateUiState.Failure -> {
                    StatusText(failureMessage(state.kind))
                    if (state.release != null) {
                        MyFinHubOutlinedAction("Λήψη ξανά", onDownload, Modifier.fillMaxWidth())
                    } else {
                        MyFinHubOutlinedAction("Δοκιμή ξανά", onCheck, Modifier.fillMaxWidth())
                    }
                }
            }
            Text(
                "Η ενημέρωση δεν αποσυνδέει τον λογαριασμό. Μετά την επανεκκίνηση συνεχίζει η κανονική τοπική επαλήθευση PIN/βιομετρικού και ο έλεγχος της υπάρχουσας server session.",
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

private fun failureMessage(kind: UpdateFailureKind): String = when (kind) {
    UpdateFailureKind.BUILD_NOT_CONFIGURED -> "Η υπηρεσία ενημερώσεων δεν είναι ρυθμισμένη σε αυτό το build."
    UpdateFailureKind.AUTH_REQUIRED -> "Η ασφαλής συνεδρία δεν είναι διαθέσιμη για έλεγχο ενημέρωσης."
    UpdateFailureKind.MFA_REQUIRED -> "Απαιτείται ολοκληρωμένη επαλήθευση AAL2 για ιδιωτικές ενημερώσεις."
    UpdateFailureKind.NETWORK -> "Δεν ήταν δυνατή η σύνδεση με την υπηρεσία ενημερώσεων."
    UpdateFailureKind.SERVER -> "Η υπηρεσία ενημερώσεων δεν είναι προσωρινά διαθέσιμη."
    UpdateFailureKind.MALFORMED_METADATA -> "Τα στοιχεία της διαθέσιμης ενημέρωσης δεν είναι έγκυρα."
    UpdateFailureKind.INSECURE_DOWNLOAD -> "Η πηγή λήψης της ενημέρωσης απορρίφθηκε για λόγους ασφαλείας."
    UpdateFailureKind.DOWNLOAD_SIZE_MISMATCH,
    UpdateFailureKind.DOWNLOAD_DIGEST_MISMATCH -> "Το ληφθέν αρχείο δεν πέρασε τον έλεγχο ακεραιότητας και διαγράφηκε."
    UpdateFailureKind.WRONG_PACKAGE,
    UpdateFailureKind.WRONG_VERSION,
    UpdateFailureKind.WRONG_SIGNER,
    UpdateFailureKind.PACKAGE_UNREADABLE -> "Το APK δεν αναγνωρίστηκε ως έγκυρη νεότερη έκδοση του MyFinHub και διαγράφηκε."
    UpdateFailureKind.INSTALL_PERMISSION_REQUIRED -> "Απαιτείται άδεια εγκατάστασης ιδιωτικών ενημερώσεων."
    UpdateFailureKind.INSTALL_BLOCKED -> "Το Android εμπόδισε την έναρξη της εγκατάστασης."
    UpdateFailureKind.INSTALL_FAILED -> "Η εγκατάσταση δεν ολοκληρώθηκε. Το υπάρχον MyFinHub παραμένει εγκατεστημένο."
}
