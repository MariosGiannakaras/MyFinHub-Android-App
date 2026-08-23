package app.myfinhub.android

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.fragment.app.FragmentActivity
import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthFactor
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.security.BiometricCapability
import app.myfinhub.android.core.security.BiometricGateway
import app.myfinhub.android.core.security.BiometricResult
import app.myfinhub.android.core.security.PinAttemptStatus
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.auth.AuthShellScreen
import app.myfinhub.android.feature.auth.AuthShellUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuthShellTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<AuthTestActivity>()

    @Test
    fun loginAndTotpExposeOnlyAccountAuthenticationInputs() {
        composeRule.setContent {
            MyFinHubTheme {
                AuthShellScreen(
                    state = AuthShellUiState.Login(),
                    onSignIn = { _, password -> password.fill('\u0000') },
                    onSubmitTotp = { code -> code.fill('\u0000') },
                    onEnrollPin = { pin, confirmation -> pin.fill('\u0000'); confirmation.fill('\u0000') },
                    onVerifyPin = { pin -> pin.fill('\u0000') },
                    onBiometricSuccess = {},
                    onPinFallbackRequested = {},
                    readyContent = {},
                    biometricGateway = FakeBiometricGateway(BiometricCapability.UNAVAILABLE),
                )
            }
        }

        composeRule.onNodeWithText("Σύνδεση στο MyFinHub").assertIsDisplayed()
        composeRule.onNodeWithText("Email").assertIsDisplayed()
        composeRule.onNodeWithText("Κωδικός").assertIsDisplayed()
        composeRule.onNodeWithText("Supabase URL").assertDoesNotExist()
        composeRule.onNodeWithText("Vercel project").assertDoesNotExist()

        composeRule.setContent {
            MyFinHubTheme {
                AuthShellScreen(
                    state = AuthShellUiState.Mfa(
                        session = aal1Session(),
                        factor = AuthFactor("factor-synthetic", "totp", "verified", "Authenticator"),
                    ),
                    onSignIn = { _, password -> password.fill('\u0000') },
                    onSubmitTotp = { code -> code.fill('\u0000') },
                    onEnrollPin = { pin, confirmation -> pin.fill('\u0000'); confirmation.fill('\u0000') },
                    onVerifyPin = { pin -> pin.fill('\u0000') },
                    onBiometricSuccess = {},
                    onPinFallbackRequested = {},
                    readyContent = {},
                    biometricGateway = FakeBiometricGateway(BiometricCapability.UNAVAILABLE),
                )
            }
        }

        composeRule.onNodeWithText("Επαλήθευση δύο παραγόντων").assertIsDisplayed()
        composeRule.onNodeWithText("Κωδικός TOTP").assertIsDisplayed()
    }

    @Test
    fun lockedStatePromptsBiometricFirstAndHonorsPinFallbackResult() {
        val gateway = FakeBiometricGateway(
            capability = BiometricCapability.AVAILABLE,
            result = BiometricResult.PinFallbackRequested,
        )
        var pinFallbackRequests = 0

        composeRule.setContent {
            MyFinHubTheme {
                AuthShellScreen(
                    state = AuthShellUiState.Locked(
                        session = aal2Session(),
                        showPin = false,
                        pinStatus = PinAttemptStatus(allowed = true, attemptsRemaining = 5),
                    ),
                    onSignIn = { _, password -> password.fill('\u0000') },
                    onSubmitTotp = { code -> code.fill('\u0000') },
                    onEnrollPin = { pin, confirmation -> pin.fill('\u0000'); confirmation.fill('\u0000') },
                    onVerifyPin = { pin -> pin.fill('\u0000') },
                    onBiometricSuccess = {},
                    onPinFallbackRequested = { pinFallbackRequests += 1 },
                    readyContent = {},
                    biometricGateway = gateway,
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(1, gateway.authenticateCalls)
        assertEquals(1, pinFallbackRequests)
        composeRule.onNodeWithText("Ξεκλείδωμα με βιομετρικά").assertIsDisplayed()
    }

    private fun aal1Session() = AuthSession(
        accessToken = "synthetic-aal1",
        refreshToken = "synthetic-refresh",
        expiresAtEpochSeconds = 99_999,
        userId = "synthetic-owner",
        assuranceLevel = AssuranceLevel.AAL1,
    )

    private fun aal2Session() = aal1Session().copy(assuranceLevel = AssuranceLevel.AAL2)
}

private class FakeBiometricGateway(
    private val capability: BiometricCapability,
    private val result: BiometricResult = BiometricResult.Cancelled,
) : BiometricGateway {
    var authenticateCalls: Int = 0
        private set

    override fun capability(context: Context): BiometricCapability = capability

    override fun authenticate(activity: FragmentActivity, callback: (BiometricResult) -> Unit) {
        authenticateCalls += 1
        callback(result)
    }
}
