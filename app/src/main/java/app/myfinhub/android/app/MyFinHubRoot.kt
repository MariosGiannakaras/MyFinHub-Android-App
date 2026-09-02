package app.myfinhub.android.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.myfinhub.android.BuildConfig
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.network.NetworkStatus
import app.myfinhub.android.core.ui.UserNotice
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.auth.AuthShellScreen
import app.myfinhub.android.feature.auth.AuthShellUiState
import app.myfinhub.android.feature.auth.AuthShellViewModel
import app.myfinhub.android.feature.money.CardSecretUiState
import app.myfinhub.android.feature.money.CardSecretViewModel
import app.myfinhub.android.feature.utilities.AppDiagnosticsSnapshot
import java.net.URI
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.merge

/**
 * Production application root.
 *
 * Local biometric/PIN success is handled by [AuthShellViewModel]. Only an Auth Ready session is
 * handed to production finance and card-secret controllers. Sensitive card values are never part of
 * the canonical product state and are cleared whenever auth leaves Ready.
 */
@Composable
fun MyFinHubRoot(
    authViewModel: AuthShellViewModel = viewModel(),
    financeViewModel: FinanceProductViewModel = viewModel(),
    cardSecretViewModel: CardSecretViewModel = viewModel(),
) {
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val financeState by financeViewModel.state.collectAsStateWithLifecycle()
    val cardSecretState by cardSecretViewModel.state.collectAsStateWithLifecycle()
    val networkStatus by financeViewModel.networkStatus.collectAsStateWithLifecycle()
    val lastSuccessfulSync by financeViewModel.lastSuccessfulSync.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var detailNotice by remember { mutableStateOf<UserNotice?>(null) }
    var lastDiagnosticCode by remember { mutableStateOf<String?>(null) }
    val snackbarBottomPadding = if (
        authState is AuthShellUiState.Ready && financeState is FinanceProductState.Ready
    ) 152.dp else 12.dp

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthShellUiState.Ready -> {
                financeViewModel.attachSession(state.session)
                cardSecretViewModel.attachSession(state.session)
            }
            else -> {
                financeViewModel.clear()
                cardSecretViewModel.clear()
            }
        }
    }
    LaunchedEffect(financeViewModel, cardSecretViewModel) {
        financeViewModel.committedCardDeletions.collect { cardId ->
            cardSecretViewModel.purgeCard(cardId)
        }
    }
    LaunchedEffect(financeState, cardSecretState) {
        if (financeState is FinanceProductState.AuthRejected || cardSecretState is CardSecretUiState.AuthRejected) {
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

    MyFinHubTheme {
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
                        onDiscardMutation = financeViewModel::discardPendingAndReload,
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
                        diagnostics = diagnostics,
                    )
                },
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
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

@Composable
internal fun UserNoticeDetailsDialog(
    notice: UserNotice,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Λεπτομέρειες σφάλματος") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    onDiscardMutation: () -> Unit,
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
    diagnostics: AppDiagnosticsSnapshot,
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
                    planState = projection.planState,
                    onPlanAction = onPlanAction,
                    insightsState = projection.insightsState,
                    diagnostics = diagnostics,
                    onLogout = onLogout,
                    canonicalProductMode = true,
                )
                if (state.saving) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    )
                }
            }
            state.issue?.let { issue ->
                val retryLabel = when (issue.kind) {
                    FinanceSyncIssueKind.REVISION_CONFLICT -> "Φόρτωση νεότερων και επανάληψη"
                    FinanceSyncIssueKind.WAITING_FOR_NETWORK -> "Δοκιμή ξανά"
                    FinanceSyncIssueKind.SAVE_FAILED -> "Επανάληψη αποθήκευσης"
                }
                AlertDialog(
                    onDismissRequest = {},
                    title = {
                        Text(
                            when (issue.kind) {
                                FinanceSyncIssueKind.REVISION_CONFLICT -> "Υπάρχει νεότερη έκδοση"
                                FinanceSyncIssueKind.WAITING_FOR_NETWORK -> "Αναμονή σύνδεσης"
                                FinanceSyncIssueKind.SAVE_FAILED -> "Η αποθήκευση δεν ολοκληρώθηκε"
                            },
                        )
                    },
                    text = { Text(issue.message) },
                    confirmButton = {
                        Button(onClick = onRetryMutation) {
                            Text(retryLabel)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDiscardMutation) {
                            Text("Απόρριψη τοπικής αλλαγής")
                        }
                    },
                )
            }
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
    is AuthShellUiState.Ready -> "Ενεργή · ${state.session.assuranceLevel.name}"
}

@Composable
private fun FinanceLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Δεν ήταν δυνατή η φόρτωση", style = MaterialTheme.typography.headlineSmall)
            Text(message)
            if (retryable) {
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Δοκιμή ξανά")
                }
            }
            TextButton(onClick = onLogout) {
                Text("Αποσύνδεση")
            }
        }
    }
}
