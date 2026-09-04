package app.myfinhub.android.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.myfinhub.android.BuildConfig
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.network.NetworkStatus
import app.myfinhub.android.core.ui.PrivacySafeNoticeHistoryStore
import app.myfinhub.android.core.ui.PrivacySafeNoticeRecord
import app.myfinhub.android.core.ui.UserNotice
import app.myfinhub.android.core.ui.shouldPresentNotice
import app.myfinhub.android.core.update.LocalUpdateController
import app.myfinhub.android.core.update.UpdateController
import app.myfinhub.android.core.update.UpdateViewModel
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.auth.AuthShellScreen
import app.myfinhub.android.feature.auth.AuthShellUiState
import app.myfinhub.android.feature.auth.AuthShellViewModel
import app.myfinhub.android.feature.money.CardCreateRequest
import app.myfinhub.android.feature.money.CardSecretUiState
import app.myfinhub.android.feature.money.CardSecretViewModel
import app.myfinhub.android.feature.utilities.AppAppearance
import app.myfinhub.android.feature.utilities.AppAppearancePreference
import app.myfinhub.android.feature.utilities.AppDiagnosticsSnapshot
import java.net.URI
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.merge

/**
 * Production application root.
 *
 * Local biometric/PIN success is handled by [AuthShellViewModel]. A locally unlocked offline Ready
 * session may render only the encrypted device cache; finance network writes remain disabled until
 * AuthShell revalidates the server session. Card-secret and updater controllers are also detached
 * while the session is offline-only so no network-sensitive capability can use an unvalidated token.
 */
@Composable
fun MyFinHubRoot(
    authViewModel: AuthShellViewModel = viewModel(),
    financeViewModel: FinanceProductViewModel = viewModel(),
    cardSecretViewModel: CardSecretViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appearancePreferences = remember(context) {
        context.applicationContext.getSharedPreferences(AppAppearancePreference.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var appearance by remember(context) { mutableStateOf(AppAppearancePreference.read(context)) }
    DisposableEffect(appearancePreferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == AppAppearancePreference.KEY) {
                appearance = AppAppearance.fromStorage(preferences.getString(key, null))
            }
        }
        appearancePreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { appearancePreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    DisposableEffect(lifecycleOwner, updateViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) updateViewModel.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            updateViewModel.onAppResumed()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (appearance) {
        AppAppearance.SYSTEM -> systemDark
        AppAppearance.LIGHT -> false
        AppAppearance.DARK -> true
    }

    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val financeState by financeViewModel.state.collectAsStateWithLifecycle()
    val cardSecretState by cardSecretViewModel.state.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val networkStatus by financeViewModel.networkStatus.collectAsStateWithLifecycle()
    val lastSuccessfulSync by financeViewModel.lastSuccessfulSync.collectAsStateWithLifecycle()
    val latestFinanceState by rememberUpdatedState(financeState)
    val snackbarHostState = remember { SnackbarHostState() }
    val noticeHistoryStore = remember(context) { PrivacySafeNoticeHistoryStore(context) }
    val recentNoticePresentation = remember { mutableMapOf<String, Long>() }
    var noticeHistory by remember { mutableStateOf<List<PrivacySafeNoticeRecord>>(emptyList()) }
    var detailNotice by remember { mutableStateOf<UserNotice?>(null) }
    var lastDiagnosticCode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(noticeHistoryStore) {
        noticeHistory = noticeHistoryStore.load()
    }
    val snackbarBottomPadding = if (
        authState is AuthShellUiState.Ready && financeState is FinanceProductState.Ready
    ) MyFinHubDesignMetrics.productSnackbarBottomClearance else MyFinHubSpacing.sm

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthShellUiState.Ready -> {
                financeViewModel.attachSession(
                    session = state.session,
                    allowAutomaticSync = !state.offline,
                )
                if (state.offline) {
                    cardSecretViewModel.clear()
                    updateViewModel.clearSession()
                } else {
                    cardSecretViewModel.attachSession(state.session)
                    updateViewModel.attachSession(state.session)
                }
            }
            else -> {
                // Finance clear is intentionally volatile-only; encrypted offline cache survives lock.
                financeViewModel.clear()
                cardSecretViewModel.clear()
                updateViewModel.clearSession()
            }
        }
    }
    LaunchedEffect(financeViewModel, cardSecretViewModel) {
        financeViewModel.committedCardDeletions.collect { cardId ->
            cardSecretViewModel.purgeCard(cardId)
        }
    }
    LaunchedEffect(financeState, cardSecretState) {
        if (shouldLogoutForProductAuthRejection(financeState, cardSecretState)) {
            financeViewModel.clear()
            cardSecretViewModel.clear()
            authViewModel.logout()
        }
    }
    LaunchedEffect(authViewModel, financeViewModel, cardSecretViewModel) {
        merge(
            authViewModel.notices,
            financeViewModel.notices,
            cardSecretViewModel.notices,
        ).collect { notice ->
            lastDiagnosticCode = notice.diagnosticCode
            val issue = (latestFinanceState as? FinanceProductState.Ready)?.issue
            val duplicateOfSaveIssue = issue != null &&
                notice.details.contains("Ενέργεια: Αποθήκευση οικονομικών δεδομένων")
            if (duplicateOfSaveIssue) return@collect
            if (!shouldPresentNotice(notice, recentNoticePresentation, SystemClock.elapsedRealtime())) return@collect

            noticeHistory = noticeHistoryStore.append(notice, System.currentTimeMillis())
            val result = snackbarHostState.showSnackbar(
                message = notice.message,
                actionLabel = "Λεπτομέρειες",
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                detailNotice = notice
            }
        }
    }

    val configuration = remember { AppConfiguration.fromBuildConfig() }
    val diagnostics = AppDiagnosticsSnapshot(
        versionName = BuildConfig.VERSION_NAME,
        buildType = BuildConfig.BUILD_TYPE,
        environment = environmentLabel(configuration.myFinHubApiBaseUrl),
        apiHost = runCatching { URI(configuration.myFinHubApiBaseUrl).host }.getOrNull().orEmpty().ifBlank { "Μη ρυθμισμένο" },
        networkStatus = networkStatusLabel(networkStatus),
        apiStatus = financeDiagnosticStatus(financeState),
        sessionStatus = authDiagnosticStatus(authState),
        lastSuccessfulSync = lastSuccessfulSync,
        lastDiagnosticCode = lastDiagnosticCode,
    )
    val updateController = UpdateController(
        state = updateState,
        check = { updateViewModel.checkForUpdates() },
        download = updateViewModel::downloadAvailableUpdate,
        install = updateViewModel::installReadyUpdate,
        openInstallPermission = {
            updateViewModel.installPermissionIntent()?.let { intent -> context.startActivity(intent) }
        },
    )

    MyFinHubTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalUpdateController provides updateController) {
            Box(modifier = Modifier.fillMaxSize()) {
                AuthShellScreen(
                    state = authState,
                    onSignIn = authViewModel::signIn,
                    onSubmitTotp = authViewModel::submitTotp,
                    onEnrollPin = authViewModel::enrollPin,
                    onVerifyPin = authViewModel::verifyPin,
                    onBiometricSuccess = authViewModel::biometricSucceeded,
                    onPinFallbackRequested = authViewModel::requestPinFallback,
                    readyContent = {
                        FinanceProductSurface(
                            state = financeState,
                            cardSecretState = cardSecretState,
                            onRetryLoad = financeViewModel::retryLoad,
                            onRetryMutation = financeViewModel::retryPendingMutation,
                            onUndoPendingChange = financeViewModel::undoLatestPendingMutation,
                            onLogout = authViewModel::logout,
                            onHomeAction = financeViewModel::onHomeAction,
                            onActivityAction = financeViewModel::onActivityAction,
                            onQuickEntryAction = financeViewModel::onQuickEntryAction,
                            onPlanAction = financeViewModel::onPlanAction,
                            onCardDetailOpened = cardSecretViewModel::openCard,
                            onCardDetailClosed = cardSecretViewModel::closeCard,
                            onRevealCardSecrets = cardSecretViewModel::reveal,
                            onHideCardSecrets = cardSecretViewModel::hideSecrets,
                            onSaveLocalCvv = cardSecretViewModel::saveCvv,
                            onDeleteLocalCvv = cardSecretViewModel::deleteCvv,
                            onDeleteCard = financeViewModel::deleteCard,
                            onCreateCard = financeViewModel::createCard,
                            diagnostics = diagnostics,
                            noticeHistory = noticeHistory,
                        )
                    },
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = MyFinHubSpacing.md)
                        .padding(bottom = snackbarBottomPadding),
                )
            }

            detailNotice?.let { notice ->
                UserNoticeDetailsDialog(
                    notice = notice,
                    onDismiss = { detailNotice = null },
                )
            }
        }
    }
}

@Composable
internal fun UserNoticeDetailsDialog(
    notice: UserNotice,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Λεπτομέρειες σφάλματος") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                Text(notice.details)
                Text(
                    "Κωδικός: ${notice.diagnosticCode}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Κλείσιμο")
            }
        },
    )
}

@Composable
private fun FinanceProductSurface(
    state: FinanceProductState,
    cardSecretState: CardSecretUiState,
    onRetryLoad: () -> Unit,
    onRetryMutation: () -> Unit,
    onUndoPendingChange: () -> Unit,
    onLogout: () -> Unit,
    onHomeAction: (app.myfinhub.android.feature.home.HomeAction) -> Unit,
    onActivityAction: (app.myfinhub.android.feature.activity.ActivityAction) -> Unit,
    onQuickEntryAction: (app.myfinhub.android.feature.quickentry.QuickEntryAction) -> Unit,
    onPlanAction: (app.myfinhub.android.feature.plan.PlanAction) -> Unit,
    onCardDetailOpened: (String) -> Unit,
    onCardDetailClosed: (String) -> Unit,
    onRevealCardSecrets: () -> Unit,
    onHideCardSecrets: () -> Unit,
    onSaveLocalCvv: (CharArray) -> Unit,
    onDeleteLocalCvv: () -> Unit,
    onDeleteCard: (String) -> Unit,
    onCreateCard: (CardCreateRequest) -> Unit,
    diagnostics: AppDiagnosticsSnapshot,
    noticeHistory: List<PrivacySafeNoticeRecord>,
) {
    when (state) {
        FinanceProductState.Idle,
        FinanceProductState.Loading,
        FinanceProductState.AuthRejected -> FinanceLoadingScreen()

        is FinanceProductState.Failure -> FinanceFailureScreen(
            message = state.message,
            retryable = state.retryable,
            onRetry = onRetryLoad,
            onLogout = onLogout,
        )

        is FinanceProductState.Ready -> {
            val projection = state.projection
            Box(modifier = Modifier.fillMaxSize()) {
                MyFinHubAppContent(
                    homeState = projection.homeState,
                    onHomeAction = onHomeAction,
                    activityState = projection.activityState,
                    onActivityAction = onActivityAction,
                    quickEntryState = projection.quickEntryState,
                    onQuickEntryAction = onQuickEntryAction,
                    moneyState = projection.moneyState,
                    cardSecretState = cardSecretState,
                    onCardDetailOpened = onCardDetailOpened,
                    onCardDetailClosed = onCardDetailClosed,
                    onRevealCardSecrets = onRevealCardSecrets,
                    onHideCardSecrets = onHideCardSecrets,
                    onSaveLocalCvv = onSaveLocalCvv,
                    onDeleteLocalCvv = onDeleteLocalCvv,
                    onDeleteCard = onDeleteCard,
                    onCreateCard = onCreateCard,
                    planState = projection.planState,
                    onPlanAction = onPlanAction,
                    insightsState = projection.insightsState,
                    diagnostics = diagnostics,
                    noticeHistory = noticeHistory,
                    onLogout = onLogout,
                    canonicalProductMode = true,
                )
                if (state.saving) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding(),
                    )
                }
                if (state.latestPendingChange != null || state.issue != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = MyFinHubSpacing.md, vertical = MyFinHubSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                    ) {
                        state.latestPendingChange?.let { latest ->
                            PendingChangesBanner(
                                changeCount = state.pendingChangeCount,
                                latest = latest,
                                onUndoLatest = onUndoPendingChange,
                            )
                        }
                        state.issue?.let { issue ->
                            FinanceSyncIssueBanner(
                                issue = issue,
                                onRetryMutation = onRetryMutation,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PendingChangesBanner(
    changeCount: Int,
    latest: PendingChangeUi,
    onUndoLatest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = MyFinHubDesignMetrics.cardElevation,
        shadowElevation = MyFinHubDesignMetrics.cardElevation,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MyFinHubSpacing.md, vertical = MyFinHubSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
                Text(
                    if (changeCount == 1) "1 αλλαγή σε αναμονή" else "$changeCount αλλαγές σε αναμονή",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${latest.label} · ${latest.statusLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (latest.canUndo) {
                TextButton(onClick = onUndoLatest) { Text("Αναίρεση") }
            }
        }
    }
}

@Composable
private fun FinanceSyncIssueBanner(
    issue: FinanceSyncIssue,
    onRetryMutation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (issue.kind) {
        FinanceSyncIssueKind.REVISION_CONFLICT -> "Χρειάζεται νέος συγχρονισμός"
        FinanceSyncIssueKind.WAITING_FOR_NETWORK -> "Αναμονή σύνδεσης"
        FinanceSyncIssueKind.SAVE_FAILED -> "Εκκρεμεί ασφαλής συγχρονισμός"
    }
    val retryLabel = when (issue.kind) {
        FinanceSyncIssueKind.REVISION_CONFLICT -> "Φόρτωση και επανάληψη"
        FinanceSyncIssueKind.WAITING_FOR_NETWORK -> "Δοκιμή ξανά"
        FinanceSyncIssueKind.SAVE_FAILED -> "Επανάληψη με έλεγχο"
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = MyFinHubDesignMetrics.cardElevation,
        shadowElevation = MyFinHubDesignMetrics.cardElevation,
    ) {
        Column(
            modifier = Modifier.padding(MyFinHubSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                issue.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetryMutation) {
                Text(retryLabel)
            }
            Text(
                "Οι τοπικές αλλαγές παραμένουν ορατές ως εκκρεμείς μέχρι να επιβεβαιωθεί ο συγχρονισμός.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun environmentLabel(apiBaseUrl: String): String = when {
    apiBaseUrl.isBlank() -> "Μη ρυθμισμένο"
    apiBaseUrl.contains("localhost", ignoreCase = true) || apiBaseUrl.contains("127.0.0.1") -> "Τοπικό public client"
    else -> "Production public client"
}

private fun networkStatusLabel(status: NetworkStatus): String = when (status) {
    NetworkStatus.ONLINE -> "Συνδεδεμένο"
    NetworkStatus.OFFLINE -> "Χωρίς σύνδεση"
    NetworkStatus.UNKNOWN -> "Μη επιβεβαιωμένη σύνδεση"
}

private fun financeDiagnosticStatus(state: FinanceProductState): String = when (state) {
    FinanceProductState.Idle -> "Δεν έχει ξεκινήσει"
    FinanceProductState.Loading -> "Συγχρονισμός"
    FinanceProductState.AuthRejected -> "Απαιτεί νέα σύνδεση"
    is FinanceProductState.Failure -> "Μη διαθέσιμο"
    is FinanceProductState.Ready -> when {
        state.saving -> "Αποθήκευση"
        state.issue != null -> "Απαιτεί ανάκτηση"
        state.offline -> "Offline cache · ${state.pendingTransactionCount} εκκρεμείς"
        state.pendingTransactionCount > 0 -> "Συγχρονισμός · ${state.pendingTransactionCount} εκκρεμείς"
        else -> "Συγχρονισμένο"
    }
}

private fun authDiagnosticStatus(state: AuthShellUiState): String = when (state) {
    AuthShellUiState.Loading -> "Έλεγχος"
    is AuthShellUiState.Unconfigured -> "Μη ρυθμισμένο"
    is AuthShellUiState.Login -> "Απαιτεί σύνδεση"
    is AuthShellUiState.Mfa -> "Απαιτεί AAL2"
    is AuthShellUiState.PinEnrollment -> "Ρύθμιση τοπικού PIN"
    is AuthShellUiState.Locked -> "Τοπικά κλειδωμένη"
    is AuthShellUiState.Ready -> if (state.offline) {
        "Τοπικά ξεκλειδωμένη · αναμονή server ελέγχου"
    } else {
        "Ενεργή · ${state.session.assuranceLevel.name}"
    }
}

@Composable
private fun FinanceLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            CircularProgressIndicator()
            Text("Φόρτωση των οικονομικών δεδομένων…")
        }
    }
}

@Composable
private fun FinanceFailureScreen(
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(MyFinHubSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
        ) {
            Text("Δεν ήταν δυνατή η φόρτωση", style = MaterialTheme.typography.headlineSmall)
            Text(message)
            if (retryable) {
                MyFinHubPrimaryAction(
                    label = "Δοκιμή ξανά",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    icon = null,
                )
            }
            TextButton(onClick = onLogout) {
                Text("Αποσύνδεση")
            }
        }
    }
}
