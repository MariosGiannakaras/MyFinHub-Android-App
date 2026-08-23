package app.myfinhub.android.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.auth.AuthShellScreen
import app.myfinhub.android.feature.auth.AuthShellViewModel

/**
 * Production application root.
 *
 * Local biometric/PIN success is handled by [AuthShellViewModel], which delegates to the server
 * session coordinator before it can expose the authenticated MyFinHub product shell.
 */
@Composable
fun MyFinHubRoot(
    authViewModel: AuthShellViewModel = viewModel(),
) {
    val authState by authViewModel.state.collectAsStateWithLifecycle()

    MyFinHubTheme {
        AuthShellScreen(
            state = authState,
            onSignIn = authViewModel::signIn,
            onSubmitTotp = authViewModel::submitTotp,
            onEnrollPin = authViewModel::enrollPin,
            onVerifyPin = authViewModel::verifyPin,
            onBiometricSuccess = authViewModel::biometricSucceeded,
            onPinFallbackRequested = authViewModel::requestPinFallback,
            readyContent = { MyFinHubApp() },
        )
    }
}
