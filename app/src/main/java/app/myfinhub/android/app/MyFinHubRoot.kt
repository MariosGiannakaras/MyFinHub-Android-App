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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.auth.AuthShellScreen
import app.myfinhub.android.feature.auth.AuthShellUiState
import app.myfinhub.android.feature.auth.AuthShellViewModel

/**
 * Production application root.
 *
 * Local biometric/PIN success is handled by [AuthShellViewModel]. Only an Auth Ready session is
 * handed to [FinanceProductViewModel], which loads the server-authoritative canonical document
 * before exposing the product UI.
 */
@Composable
fun MyFinHubRoot(
    authViewModel: AuthShellViewModel = viewModel(),
    financeViewModel: FinanceProductViewModel = viewModel(),
) {
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val financeState by financeViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthShellUiState.Ready -> financeViewModel.attachSession(state.session)
            else -> financeViewModel.clear()
        }
    }
    LaunchedEffect(financeState) {
        if (financeState is FinanceProductState.AuthRejected) {
            financeViewModel.clear()
            authViewModel.logout()
        }
    }

    MyFinHubTheme {
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
                    onRetryLoad = financeViewModel::retryLoad,
                    onRetryMutation = financeViewModel::retryPendingMutation,
                    onDiscardMutation = financeViewModel::discardPendingAndReload,
                    onLogout = authViewModel::logout,
                    onHomeAction = financeViewModel::onHomeAction,
                    onActivityAction = financeViewModel::onActivityAction,
                    onQuickEntryAction = financeViewModel::onQuickEntryAction,
                    onPlanAction = financeViewModel::onPlanAction,
                )
            },
        )
    }
}

@Composable
private fun FinanceProductSurface(
    state: FinanceProductState,
    onRetryLoad: () -> Unit,
    onRetryMutation: () -> Unit,
    onDiscardMutation: () -> Unit,
    onLogout: () -> Unit,
    onHomeAction: (app.myfinhub.android.feature.home.HomeAction) -> Unit,
    onActivityAction: (app.myfinhub.android.feature.activity.ActivityAction) -> Unit,
    onQuickEntryAction: (app.myfinhub.android.feature.quickentry.QuickEntryAction) -> Unit,
    onPlanAction: (app.myfinhub.android.feature.plan.PlanAction) -> Unit,
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
                    planState = projection.planState,
                    onPlanAction = onPlanAction,
                    insightsState = projection.insightsState,
                )
                if (state.saving) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    )
                }
            }
            state.issue?.let { issue ->
                AlertDialog(
                    onDismissRequest = {},
                    title = {
                        Text(
                            if (issue.kind == FinanceSyncIssueKind.REVISION_CONFLICT) {
                                "Υπάρχει νεότερη έκδοση"
                            } else {
                                "Η αποθήκευση δεν ολοκληρώθηκε"
                            },
                        )
                    },
                    text = { Text(issue.message) },
                    confirmButton = {
                        Button(onClick = onRetryMutation) {
                            Text("Επανάληψη στα νεότερα δεδομένα")
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
