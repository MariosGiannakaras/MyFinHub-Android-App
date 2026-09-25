package app.myfinhub.android.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.security.PinAttemptStatus
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(
    name = "auth_login_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun AuthLoginCompactLightScreenshot() {
    AuthFixture(AuthShellUiState.Login())
}

@PreviewTest
@Preview(
    name = "auth_pin_enrollment_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun AuthPinEnrollmentCompactLargeFontScreenshot() {
    AuthFixture(AuthShellUiState.PinEnrollment(syntheticSession()))
}

@PreviewTest
@Preview(
    name = "auth_locked_pin_fallback_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun AuthLockedPinFallbackCompactLightScreenshot() {
    AuthFixture(
        AuthShellUiState.Locked(
            session = syntheticSession(),
            showPin = true,
            pinStatus = PinAttemptStatus(allowed = true, attemptsRemaining = 5),
        ),
    )
}

@Composable
private fun AuthFixture(state: AuthShellUiState) {
    MyFinHubTheme(darkTheme = false) {
        AuthShellScreen(
            state = state,
            onSignIn = { _, password -> password.fill('\u0000') },
            onSubmitTotp = { code -> code.fill('\u0000') },
            onEnrollPin = { pin, confirmation ->
                pin.fill('\u0000')
                confirmation.fill('\u0000')
            },
            onVerifyPin = { pin -> pin.fill('\u0000') },
            onBiometricSuccess = {},
            onPinFallbackRequested = {},
            readyContent = {},
        )
    }
}

private fun syntheticSession() = AuthSession(
    accessToken = "synthetic-access",
    refreshToken = "synthetic-refresh",
    expiresAtEpochSeconds = 1,
    userId = "synthetic-owner",
    assuranceLevel = AssuranceLevel.AAL2,
)
