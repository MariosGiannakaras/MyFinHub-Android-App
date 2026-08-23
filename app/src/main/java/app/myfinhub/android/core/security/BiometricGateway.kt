package app.myfinhub.android.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity

interface BiometricGateway {
    fun capability(context: Context): BiometricCapability
    fun authenticate(activity: FragmentActivity, callback: (BiometricResult) -> Unit)
}

enum class BiometricCapability {
    AVAILABLE,
    NOT_ENROLLED,
    UNAVAILABLE,
}

sealed interface BiometricResult {
    data object Success : BiometricResult
    data object PinFallbackRequested : BiometricResult
    data object Cancelled : BiometricResult
    data class Error(val code: Int) : BiometricResult
}

class AndroidBiometricGateway : BiometricGateway {
    override fun capability(context: Context): BiometricCapability = when (
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    ) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED
        else -> BiometricCapability.UNAVAILABLE
    }

    override fun authenticate(
        activity: FragmentActivity,
        callback: (BiometricResult) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    callback(BiometricResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> callback(BiometricResult.PinFallbackRequested)
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> callback(BiometricResult.Cancelled)
                        else -> callback(BiometricResult.Error(errorCode))
                    }
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Ξεκλείδωμα MyFinHub")
            .setSubtitle("Επιβεβαίωσε την ταυτότητά σου για τοπική πρόσβαση")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Χρήση PIN")
            .build()
        prompt.authenticate(promptInfo)
    }
}
