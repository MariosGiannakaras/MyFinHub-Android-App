package app.myfinhub.android.feature.utilities

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
