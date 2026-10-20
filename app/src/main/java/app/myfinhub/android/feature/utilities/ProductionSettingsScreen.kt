package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import app.myfinhub.android.core.network.DeviceSessionRecord
import app.myfinhub.android.core.network.ServerFinanceHistorySummary
import app.myfinhub.android.core.network.visibleServerFinanceHistoryPoints
import app.myfinhub.android.core.network.WebAccountSecurityChange
import app.myfinhub.android.core.update.LocalUpdateController
import app.myfinhub.android.feature.plan.PlanningRule
import app.myfinhub.android.feature.plan.PlanningReviewSuggestion
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
    transactionRules: List<PlanningRule> = emptyList(),
    legacyReviewSuggestions: List<PlanningReviewSuggestion> = emptyList(),
    deviceSessions: List<DeviceSessionRecord> = emptyList(),
    deviceSessionsLoaded: Boolean = false,
    serverHistory: ServerFinanceHistorySummary? = null,
    serverHistoryEnabled: Boolean = false,
    serverHistoryLoaded: Boolean = false,
    onRefreshServerHistory: () -> Unit = {},
    accountSecurityEnabled: Boolean = false,
    accountSecuritySaving: Boolean = false,
    accountSecurityFeedback: String? = null,
    onChangeAccountSecurity: ((WebAccountSecurityChange) -> Unit)? = null,
    deviceSessionRevocationInFlight: Boolean = false,
    onRevokeDeviceSession: ((String) -> Unit)? = null,
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
    var pendingDeviceRevokeId by remember { mutableStateOf<String?>(null) }
    var visibleRuleCount by remember(transactionRules) { mutableStateOf(24) }
    var visibleReviewCount by remember(legacyReviewSuggestions) { mutableStateOf(12) }
    // Read-only history paging is scoped to the exact generation/revision.
    // A server refresh with a different revision resets the viewed window.
    var visibleServerHistoryCount by remember(
        serverHistory?.generation, serverHistory?.financeRevision,
    ) { mutableStateOf(24) }
    val confirmDevice = deviceSessions.singleOrNull {
        it.sessionId == pendingDeviceRevokeId && !it.current
    }
    if (confirmDevice != null && deviceSessionsLoaded &&
        onRevokeDeviceSession != null && !deviceSessionRevocationInFlight) {
        AlertDialog(
            onDismissRequest = { pendingDeviceRevokeId = null },
            title = { Text("Επιβεβαίωση αποσύνδεσης") },
            text = { Text("Θα αποσυνδεθεί η συσκευή «" + confirmDevice.label +
                "». Τα συγχρονισμένα οικονομικά δεδομένα δεν διαγράφονται.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeviceRevokeId = null
                        onRevokeDeviceSession(confirmDevice.sessionId)
                    },
                    modifier = Modifier.testTag("s9_device_revoke_confirm"),
                ) { Text("Αποσύνδεση") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeviceRevokeId = null }) { Text("Ακύρωση") }
            },
        )
    }

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

            if (transactionRules.isNotEmpty()) {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()
                    .testTag("s9_transaction_rules")) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        Text("Αυτόματη ταξινόμηση",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold)
                        Text(
                            "Κανόνες νέων κινήσεων · μόνο προβολή στο Android. " +
                                "Η σειρά και η επεξεργασία διαχειρίζονται από το Web. " +
                                "Το υπάρχον ιστορικό δεν αλλάζει.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${transactionRules.size} σύνολο · " +
                                "${transactionRules.count { it.enabled }} ενεργοί · " +
                                "${transactionRules.count { !it.enabled }} σε παύση · " +
                                "${transactionRules.count { it.warning != null }} χρειάζονται έλεγχο",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Web renders 24 rules per page. Avoid materializing
                        // thousands of Composables in a vertical scroll Column.
                        transactionRules.take(visibleRuleCount).forEachIndexed { index, rule ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("s9_transaction_rule_$index"),
                                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                            ) {
                                Text("${index + 1}. ${rule.title}",
                                    style = MaterialTheme.typography.titleSmall)
                                Text(
                                    rule.warning ?: if (rule.enabled) "Ενεργός" else "Σε παύση",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (rule.warning != null) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(rule.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Ιστορικές αντιστοιχίσεις: " +
                                        rule.historicalMatchCount +
                                        " · καμία παλιά κίνηση δεν τροποποιείται",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("s9_transaction_rule_matches_$index"),
                                )
                            }
                            if (index < visibleRuleCount - 1 && index < transactionRules.lastIndex)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (transactionRules.size > visibleRuleCount) {
                            MyFinHubOutlinedAction(
                                label = "Εμφάνιση περισσότερων κανόνων (" +
                                    (transactionRules.size - visibleRuleCount) + ")",
                                onClick = {
                                    visibleRuleCount = (visibleRuleCount + 24)
                                        .coerceAtMost(transactionRules.size)
                                },
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("s9_transaction_rules_more"),
                            )
                        }
                    }
                }
            }

            if (legacyReviewSuggestions.isNotEmpty()) {
                MyFinHubSectionCard(
                    modifier = Modifier.fillMaxWidth().testTag("s9_legacy_review_queue"),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        Text(
                            "Εκκρεμείς έλεγχοι παλιών κινήσεων",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Οι προτάσεις προκύπτουν από τις σημειώσεις του Web. " +
                                "Δεν αποτελούν βεβαιωμένες κατηγορίες και καμία εγγραφή " +
                                "δεν αλλάζει αυτόματα.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            legacyReviewSuggestions.size.toString() +
                                " κινήσεις χρειάζονται έλεγχο · " +
                                legacyReviewSuggestions.count { it.confidence == "high" } +
                                " υψηλής ένδειξης",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        legacyReviewSuggestions.take(visibleReviewCount).forEachIndexed { index, row ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("s9_legacy_review_" + index),
                                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                            ) {
                                Text(
                                    row.dateIso + " · " + when (row.confidence) {
                                        "high" -> "Ισχυρή ένδειξη"
                                        "medium" -> "Χρειάζεται επιβεβαίωση"
                                        else -> "Χαμηλή ένδειξη"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    row.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (index < legacyReviewSuggestions.lastIndex &&
                                index < visibleReviewCount - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                        if (legacyReviewSuggestions.size > visibleReviewCount) {
                            MyFinHubOutlinedAction(
                                label = "Περισσότερες προτάσεις ελέγχου (" +
                                    (legacyReviewSuggestions.size - visibleReviewCount) + ")",
                                onClick = {
                                    visibleReviewCount = (visibleReviewCount + 12)
                                        .coerceAtMost(legacyReviewSuggestions.size)
                                },
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("s9_legacy_review_more"),
                            )
                        }
                    }
                }
            }

            if (serverHistoryEnabled) {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()
                    .testTag("s9_server_finance_history")) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text("Ιστορικό αλλαγών στον server",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Μόνο ανάγνωση · δεν περιλαμβάνει αντίγραφα οικονομικών δεδομένων.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!serverHistoryLoaded || serverHistory == null) {
                            Text("Δεν φορτώθηκε ακόμη το ιστορικό. Δοκίμασε ανανέωση.")
                        } else if (!serverHistory.available) {
                            Text("Το ιστορικό αλλαγών δεν είναι διαθέσιμο.")
                        } else {
                            Text("Ιστορικό γενιάς " + serverHistory.generation +
                                " · αναθεώρηση " + serverHistory.financeRevision,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("Αναίρεση: " +
                                (if (serverHistory.canUndo) "διαθέσιμη" else "μη διαθέσιμη") +
                                " (" + serverHistory.undoDepth + ")" +
                                " · Επανάληψη: " +
                                (if (serverHistory.canRedo) "διαθέσιμη" else "μη διαθέσιμη") +
                                " (" + serverHistory.redoDepth + ")",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (serverHistory.points.isEmpty()) {
                                Text(
                                    "Δεν υπάρχουν καταγεγραμμένες αλλαγές.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                val visiblePoints = visibleServerFinanceHistoryPoints(
                                    serverHistory, visibleServerHistoryCount,
                                )
                                Text("Προβολή " + visiblePoints.size +
                                    " από " + serverHistory.points.size + " αλλαγές",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("s9_history_page_info"),
                                )
                                visiblePoints.forEachIndexed { index, point ->
                                    Text(
                                        (if (point.current) "Τρέχουσα · " else "") +
                                            point.createdAt.take(16).replace('T', ' ') +
                                            " · " + (if (amountsVisible) point.label
                                                else "Περιγραφή κρυμμένη"),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.testTag("s9_history_row_" + index),
                                    )
                                }
                                if (visiblePoints.size < serverHistory.points.size) {
                                    TextButton(
                                        onClick = {
                                            visibleServerHistoryCount =
                                                (visibleServerHistoryCount + 24)
                                                    .coerceAtMost(serverHistory.points.size)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                            .testTag("s9_history_more"),
                                    ) {
                                        Text("Προβολή παλαιότερων αλλαγών · " +
                                            (serverHistory.points.size - visiblePoints.size) +
                                            " ακόμη")
                                    }
                                }
                            }
                        }
                        MyFinHubOutlinedAction(
                            label = "Ανανέωση ιστορικού αλλαγών",
                            onClick = onRefreshServerHistory,
                            modifier = Modifier.fillMaxWidth()
                                .testTag("s9_history_refresh"),
                        )
                    }
                }
            }

            if (deviceSessionsLoaded) {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()
                    .testTag("s9_device_sessions")) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        Text("Συνδεδεμένες συσκευές",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (onRevokeDeviceSession != null)
                                "Αποσύνδεση άλλης συσκευής μόνο με επιβεβαίωση. Η τρέχουσα συνεδρία προστατεύεται."
                            else "Μόνο ανάγνωση · καμία απομακρυσμένη αποσύνδεση από εδώ.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (deviceSessions.isEmpty()) {
                            Text("Δεν υπάρχουν διαθέσιμες καταγεγραμμένες συσκευές.")
                        } else deviceSessions.forEach { device ->
                            Column(modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                                Text(device.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    device.platform + (if (device.current) " · Αυτή η συνεδρία" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text("Τελευταία δραστηριότητα (UTC): " +
                                    device.lastSeenAt.take(16).replace('T', ' '),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (!device.current && onRevokeDeviceSession != null) {
                                    MyFinHubOutlinedAction(
                                        label = "Αποσύνδεση συσκευής",
                                        onClick = { pendingDeviceRevokeId = device.sessionId },
                                        modifier = Modifier.fillMaxWidth()
                                            .testTag("s9_device_revoke_" + device.sessionId),
                                        enabled = !deviceSessionRevocationInFlight,
                                    )
                                }
                            }
                        }
                    }
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
                        onChangeAccountSecurity?.let { handler ->
                            AccountSecuritySettingsControls(
                                enabled = accountSecurityEnabled && amountsVisible,
                                saving = accountSecuritySaving,
                                feedback = accountSecurityFeedback,
                                onSubmit = handler,
                            )
                        }
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
