package app.myfinhub.android.feature.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.myfinhub.android.core.auth.AuthAppState
import app.myfinhub.android.core.auth.AuthFailureKind
import app.myfinhub.android.core.auth.AuthFactor
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.auth.AuthSessionCoordinator
import app.myfinhub.android.core.auth.SupabaseAuthGateway
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.security.AndroidKeystoreCipher
import app.myfinhub.android.core.security.AndroidKeystorePinVerifier
import app.myfinhub.android.core.security.DataStoreEncryptedSessionStore
import app.myfinhub.android.core.security.DataStorePinAttemptLimiter
import app.myfinhub.android.core.security.LocalPinVerifier
import app.myfinhub.android.core.security.PinAttemptLimiter
import app.myfinhub.android.core.security.PinAttemptStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

sealed interface AuthShellUiState {
    data object Loading : AuthShellUiState
    data class Unconfigured(val message: String) : AuthShellUiState
    data class Login(val message: String? = null) : AuthShellUiState
    data class Mfa(
        val session: AuthSession,
        val factor: AuthFactor,
        val message: String? = null,
    ) : AuthShellUiState
    data class PinEnrollment(
        val session: AuthSession,
        val message: String? = null,
    ) : AuthShellUiState
    data class Locked(
        val session: AuthSession,
        val showPin: Boolean,
        val pinStatus: PinAttemptStatus,
        val message: String? = null,
    ) : AuthShellUiState
    data class Ready(val session: AuthSession) : AuthShellUiState
}

class AuthShellViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val configuration = AppConfiguration.fromBuildConfig()
    private val httpClient = OkHttpClient.Builder().build()
    private val authGateway = SupabaseAuthGateway(configuration, httpClient)
    private val sessionStore = DataStoreEncryptedSessionStore(
        context = application,
        cipher = AndroidKeystoreCipher("myfinhub.auth-session.aes.v1"),
    )
    private val pinVerifier: LocalPinVerifier = AndroidKeystorePinVerifier(application)
    private val pinLimiter: PinAttemptLimiter = DataStorePinAttemptLimiter(application)
    private val coordinator = AuthSessionCoordinator(
        configuration = configuration,
        authGateway = authGateway,
        sessionStore = sessionStore,
    )

    private val mutableState = MutableStateFlow<AuthShellUiState>(AuthShellUiState.Loading)
    val state: StateFlow<AuthShellUiState> = mutableState.asStateFlow()

    init {
        initialize()
    }

    fun initialize() {
        viewModelScope.launch {
            mutableState.value = AuthShellUiState.Loading
            when (val result = coordinator.initialize()) {
                is AuthAppState.Unconfigured -> mutableState.value = AuthShellUiState.Unconfigured(
                    "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration.",
                )
                AuthAppState.LoginRequired -> mutableState.value = AuthShellUiState.Login()
                is AuthAppState.Locked -> {
                    if (pinVerifier.isEnrolled()) {
                        mutableState.value = lockedState(result.session)
                    } else {
                        // A stored AAL2 session can exist if the process died after server auth but
                        // before local PIN enrollment completed. Do not bypass local unlock or leave
                        // a device without enrolled biometrics permanently locked: discard that
                        // incomplete local session and require a fresh normal login.
                        coordinator.logout(result.session)
                        pinLimiter.recordSuccess()
                        mutableState.value = AuthShellUiState.Login(
                            "Η τοπική ρύθμιση ξεκλειδώματος δεν ολοκληρώθηκε. Συνδέσου ξανά.",
                        )
                    }
                }
                is AuthAppState.Ready -> routeReady(result.session)
                is AuthAppState.MfaRequired -> mutableState.value = AuthShellUiState.Mfa(result.session, result.factor)
                is AuthAppState.Failure -> mutableState.value = AuthShellUiState.Login(failureMessage(result.failure.kind))
            }
        }
    }

    fun signIn(email: String, password: CharArray) {
        val passwordCopy = password.copyOf()
        password.fill('\u0000')
        viewModelScope.launch {
            mutableState.value = AuthShellUiState.Loading
            try {
                when (val result = coordinator.signIn(email.trim(), passwordCopy)) {
                    is AuthAppState.Ready -> routeReady(result.session)
                    is AuthAppState.MfaRequired -> mutableState.value = AuthShellUiState.Mfa(result.session, result.factor)
                    AuthAppState.LoginRequired -> mutableState.value = AuthShellUiState.Login()
                    is AuthAppState.Unconfigured -> mutableState.value = AuthShellUiState.Unconfigured(
                        "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration.",
                    )
                    is AuthAppState.Failure -> mutableState.value = AuthShellUiState.Login(failureMessage(result.failure.kind))
                    is AuthAppState.Locked -> mutableState.value = lockedState(result.session)
                }
            } finally {
                passwordCopy.fill('\u0000')
            }
        }
    }

    fun submitTotp(code: CharArray) {
        val current = mutableState.value as? AuthShellUiState.Mfa ?: run {
            code.fill('\u0000')
            return
        }
        val codeCopy = code.copyOf()
        code.fill('\u0000')
        viewModelScope.launch {
            mutableState.value = AuthShellUiState.Loading
            try {
                when (
                    val result = coordinator.completeTotp(
                        session = current.session,
                        factorId = current.factor.id,
                        code = codeCopy,
                    )
                ) {
                    is AuthAppState.Ready -> routeReady(result.session)
                    is AuthAppState.Failure -> mutableState.value = current.copy(
                        message = failureMessage(result.failure.kind),
                    )
                    AuthAppState.LoginRequired -> mutableState.value = AuthShellUiState.Login()
                    is AuthAppState.MfaRequired -> mutableState.value = AuthShellUiState.Mfa(result.session, result.factor)
                    is AuthAppState.Unconfigured -> mutableState.value = AuthShellUiState.Unconfigured(
                        "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration.",
                    )
                    is AuthAppState.Locked -> mutableState.value = lockedState(result.session)
                }
            } finally {
                codeCopy.fill('\u0000')
            }
        }
    }

    fun requestPinFallback() {
        val current = mutableState.value as? AuthShellUiState.Locked ?: return
        mutableState.value = current.copy(showPin = true, message = null)
    }

    fun biometricSucceeded() {
        val current = mutableState.value as? AuthShellUiState.Locked ?: return
        validateUnlockedSession(current.session, fallbackToPin = false)
    }

    fun verifyPin(pin: CharArray) {
        val current = mutableState.value as? AuthShellUiState.Locked ?: run {
            pin.fill('\u0000')
            return
        }
        val pinCopy = pin.copyOf()
        pin.fill('\u0000')
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val status = pinLimiter.status(now)
                if (!status.allowed) {
                    mutableState.value = current.copy(
                        showPin = true,
                        pinStatus = status,
                        message = lockMessage(status),
                    )
                    return@launch
                }

                if (pinVerifier.verify(pinCopy)) {
                    pinLimiter.recordSuccess()
                    validateUnlockedSession(current.session, fallbackToPin = true)
                } else {
                    val next = pinLimiter.recordFailure(now)
                    mutableState.value = current.copy(
                        showPin = true,
                        pinStatus = next,
                        message = if (next.allowed) {
                            "Λάθος PIN. Απομένουν ${next.attemptsRemaining} προσπάθειες."
                        } else {
                            lockMessage(next)
                        },
                    )
                }
            } finally {
                pinCopy.fill('\u0000')
            }
        }
    }

    fun enrollPin(pin: CharArray, confirmation: CharArray) {
        val current = mutableState.value as? AuthShellUiState.PinEnrollment ?: run {
            pin.fill('\u0000')
            confirmation.fill('\u0000')
            return
        }
        val pinCopy = pin.copyOf()
        val confirmationCopy = confirmation.copyOf()
        pin.fill('\u0000')
        confirmation.fill('\u0000')

        viewModelScope.launch {
            try {
                if (!pinCopy.contentEquals(confirmationCopy)) {
                    mutableState.value = current.copy(message = "Τα PIN δεν ταιριάζουν.")
                    return@launch
                }
                if (pinCopy.size !in 4..12 || pinCopy.any { !it.isDigit() }) {
                    mutableState.value = current.copy(message = "Το PIN πρέπει να έχει 4–12 ψηφία.")
                    return@launch
                }
                pinVerifier.enroll(pinCopy)
                pinLimiter.recordSuccess()
                mutableState.value = AuthShellUiState.Ready(current.session)
            } finally {
                pinCopy.fill('\u0000')
                confirmationCopy.fill('\u0000')
            }
        }
    }

    fun logout() {
        val session = when (val current = mutableState.value) {
            is AuthShellUiState.Ready -> current.session
            is AuthShellUiState.Locked -> current.session
            is AuthShellUiState.PinEnrollment -> current.session
            is AuthShellUiState.Mfa -> current.session
            else -> null
        }
        viewModelScope.launch {
            mutableState.value = AuthShellUiState.Loading
            coordinator.logout(session)
            mutableState.value = AuthShellUiState.Login()
        }
    }

    private fun validateUnlockedSession(session: AuthSession, fallbackToPin: Boolean) {
        viewModelScope.launch {
            mutableState.value = AuthShellUiState.Loading
            when (val result = coordinator.afterLocalUnlock(session)) {
                is AuthAppState.Ready -> routeReady(result.session)
                AuthAppState.LoginRequired -> mutableState.value = AuthShellUiState.Login(
                    "Η προηγούμενη συνεδρία έληξε. Συνδέσου ξανά.",
                )
                is AuthAppState.Failure -> mutableState.value = lockedState(
                    session = result.recoverableSession ?: session,
                    showPin = fallbackToPin,
                    message = failureMessage(result.failure.kind),
                )
                is AuthAppState.MfaRequired -> mutableState.value = AuthShellUiState.Mfa(result.session, result.factor)
                is AuthAppState.Unconfigured -> mutableState.value = AuthShellUiState.Unconfigured(
                    "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration.",
                )
                is AuthAppState.Locked -> mutableState.value = lockedState(result.session)
            }
        }
    }

    private suspend fun routeReady(session: AuthSession) {
        mutableState.value = if (pinVerifier.isEnrolled()) {
            AuthShellUiState.Ready(session)
        } else {
            AuthShellUiState.PinEnrollment(session)
        }
    }

    private suspend fun lockedState(
        session: AuthSession,
        showPin: Boolean = false,
        message: String? = null,
    ): AuthShellUiState.Locked = AuthShellUiState.Locked(
        session = session,
        showPin = showPin,
        pinStatus = pinLimiter.status(System.currentTimeMillis()),
        message = message,
    )

    private fun failureMessage(kind: AuthFailureKind): String = when (kind) {
        AuthFailureKind.BUILD_NOT_CONFIGURED -> "Η έκδοση της εφαρμογής δεν είναι σωστά ρυθμισμένη."
        AuthFailureKind.INVALID_CREDENTIALS -> "Το email ή ο κωδικός δεν είναι σωστά."
        AuthFailureKind.MFA_REQUIRED -> "Απαιτείται επαλήθευση δύο παραγόντων."
        AuthFailureKind.INVALID_MFA_CODE -> "Ο κωδικός TOTP δεν είναι σωστός ή έχει λήξει."
        AuthFailureKind.SESSION_EXPIRED,
        AuthFailureKind.UNAUTHORIZED -> "Η συνεδρία δεν είναι πλέον έγκυρη."
        AuthFailureKind.RATE_LIMITED -> "Πολλές προσπάθειες. Δοκίμασε ξανά αργότερα."
        AuthFailureKind.NETWORK -> "Δεν υπάρχει σύνδεση με την υπηρεσία."
        AuthFailureKind.SERVER -> "Η υπηρεσία σύνδεσης δεν είναι προσωρινά διαθέσιμη."
        AuthFailureKind.MALFORMED_RESPONSE -> "Η υπηρεσία επέστρεψε μη αναμενόμενη απάντηση."
    }

    private fun lockMessage(status: PinAttemptStatus): String {
        val seconds = ((status.retryAfterMillis + 999) / 1_000).coerceAtLeast(1)
        return "Το PIN κλειδώθηκε προσωρινά. Δοκίμασε ξανά σε ${seconds}″."
    }
}
