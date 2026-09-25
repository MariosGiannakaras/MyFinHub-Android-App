package app.myfinhub.android.feature.utilities

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.designsystem.MyFinHubMotion
import app.myfinhub.android.designsystem.MyFinHubOutlinedAction
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing

@Composable
internal fun ProductionDiagnosticsCard(
    diagnostics: AppDiagnosticsSnapshot,
    modifier: Modifier = Modifier,
    supportDetailsInitiallyExpanded: Boolean = false,
) {
    var supportDetailsExpanded by rememberSaveable {
        mutableStateOf(supportDetailsInitiallyExpanded)
    }
    MyFinHubSectionCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
            Text(
                "Κατάσταση εφαρμογής",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Συνοπτική εικόνα για σύνδεση, συγχρονισμό και συνεδρία. Οι τεχνικές λεπτομέρειες εμφανίζονται μόνο όταν τις χρειάζεσαι για υποστήριξη.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DiagnosticSummaryRow("Σύνδεση", diagnostics.networkStatus)
            DiagnosticSummaryRow("Συγχρονισμός", humanReadableSyncStatus(diagnostics.apiStatus))
            DiagnosticSummaryRow("Συνεδρία", humanReadableSessionStatus(diagnostics.sessionStatus))
            DiagnosticSummaryRow(
                "Τελευταίος επιτυχής συγχρονισμός",
                formatDiagnosticTime(diagnostics.lastSuccessfulSync),
            )
            diagnosticCodeDescription(diagnostics.lastDiagnosticCode)?.let { description ->
                DiagnosticSummaryRow("Τελευταίο συμβάν", description)
            }
            MyFinHubOutlinedAction(
                label = if (supportDetailsExpanded) {
                    "Απόκρυψη λεπτομερειών υποστήριξης"
                } else {
                    "Λεπτομέρειες για υποστήριξη"
                },
                onClick = { supportDetailsExpanded = !supportDetailsExpanded },
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(
                visible = supportDetailsExpanded,
                enter = fadeIn(tween(MyFinHubMotion.StandardDurationMillis)) +
                    expandVertically(tween(MyFinHubMotion.StandardDurationMillis)),
                exit = fadeOut(tween(MyFinHubMotion.QuickDurationMillis)) +
                    shrinkVertically(tween(MyFinHubMotion.StandardDurationMillis)),
            ) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "Ασφαλή στοιχεία υποστήριξης",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Μπορείς να αντιγράψεις αυτά τα στοιχεία όταν ζητηθούν από υποστήριξη. Δεν περιλαμβάνουν οικονομικά δεδομένα, διαπιστευτήρια ή στοιχεία κάρτας.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DiagnosticSupportRow("Έκδοση", "${diagnostics.versionName} · ${diagnostics.buildType}")
                        DiagnosticSupportRow("Περιβάλλον", diagnostics.environment)
                        DiagnosticSupportRow("Υπηρεσία", diagnostics.apiHost)
                        DiagnosticSupportRow(
                            "Κωδικός υποστήριξης",
                            diagnostics.lastDiagnosticCode ?: "Δεν υπάρχει",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticSummaryRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DiagnosticSupportRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
