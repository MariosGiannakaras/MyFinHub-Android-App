package app.myfinhub.android.feature.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.myfinhub.android.core.auth.AuthAppState
import app.myfinhub.android.core.auth.AuthFailureKind
import app.myfinhub.android.core.auth.AuthFactor
import app.myfinhub.android.core.auth.AuthResult
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.auth.AuthSessionCoordinator
import app.myfinhub.android.core.auth.SupabaseAuthGateway
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.network.AndroidConnectivityObserver
import app.myfinhub.android.core.network.NetworkClientFactory
import app.myfinhub.android.core.network.NetworkStatus
import app.myfinhub.android.core.security.AndroidKeystoreCipher
import app.myfinhub.android.core.security.AndroidKeystorePinVerifier
import app.myfinhub.android.core.security.DataStoreEncryptedSessionStore
import app.myfinhub.android.core.security.DataStorePinAttemptLimiter
import app.myfinhub.android.core.security.LocalPinVerifier
import app.myfinhub.android.core.security.PinAttemptLimiter
import app.myfinhub.android.core.security.PinAttemptStatus
import app.myfinhub.android.core.ui.UserNotice
import app.myfinhub.android.core.ui.authFailureMessage
import app.myfinhub.android.core.ui.offlineUserNotice
import app.myfinhub.android.core.ui.toUserNotice
import app.myfinhub.android.core.ui.unexpectedUserNotice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

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

    /**
     * [offline] means the owner passed local PIN/biometric unlock but the stored server session has
     * not yet been revalidated because network/server validation is unavailable. Product reads may use encrypted cached
     * data, while network writes remain disabled until this returns to false.
     */
    data class Ready(
        val session: AuthSession,
        val offline: Boolean = false,
    ) : AuthShellUiState
}

class AuthShellViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val configuration = AppConfiguration.fromBuildConfig()
    private val connectivityObserver = AndroidConnectivityObserver(application)
    private val httpClient = NetworkClientFactory.create()
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

    private val mutableNotices = MutableSharedFlow<UserNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<UserNotice> = mutableNotices.asSharedFlow()

    private var offlineValidationInFlight = false

    init {
        initialize()
        viewModelScope.launch {
            connectivityObserver.status.drop(1).collect { status ->
                if (status != NetworkStatus.ONLINE) return@collect
                val current = mutableState.value as? AuthShellUiState.Ready ?: return@collect
                if (current.offline) validateOfflineReadySession(current.session)
            }
        }
    }

    fun initialize() {
        viewModelScope.launch {
            mutableState.value = AuthShellUiState.Loading
            try {
                when (val result = coordinator.initialize()) {
                    is AuthAppState.Unconfigured -> mutableState.value = AuthShellUiState.Unconfigured(
                        "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration.",
                    )
                    AuthAppState.LoginRequired -> mutableState.value = AuthShellUiState.Login()
                    is AuthAppState.Locked -> {
                        if (pinVerifier.isEnrolled()) {
                            mutableState.value = lockedState(result.session)
                        } else {
                            coordinator.logout(result.session)
                            pinLimiter.recordSuccess()
                            mutableState.value = AuthShellUiState.Login(
                                "Η τοπική ρύθμιση ξεκλειδώματος δεν ολοκληρώθηκε. Συνδέσου ξανά.",
                            )
                        }
                    }
                    is AuthAppState.Ready -> routeReady(result.session)
                    is AuthAppState.MfaRequired -> mutableState.value = AuthShellUiState.Mfa(result.session, result.factor)
                    is AuthAppState.Failure -> {
                        reportAuthFailure(result.failure, "Αρχικοποίηση συνεδρίας")
                        mutableState.value = AuthShellUiState.Login(authFailureMessage(result.failure.kind))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = AuthShellUiState.Login("Η ασφαλής συνεδρία δεν μπόρεσε να αρχικοποιηθεί.")
                mutableNotices.emit(
                    unexpectedUserNotice(
                        operation = "Αρχικοποίηση ασφαλούς συνεδρίας",
                        throwable = error,
                        message = "Η ασφαλής συνεδρία δεν μπόρεσε να αρχικοποιηθεί.",
                    ),
                )
            }
        }
    }

    fun signIn(email: String, password: CharArray) {
        val current = mutableState.value as? AuthShellUiState.Login ?: run {
            password.fill('\u0000')
            return
        }
        val passwordCopy = password.copyOf()
        password.fill('\u0000')
        if (connectivityObserver.current() != NetworkStatus.ONLINE) {
            passwordCopy.fill('\u0000')
            mutableState.value = current.copy(message = "Δεν υπάρχει σύνδεση για νέα σύνδεση λογαριασμού.")
            mutableNotices.tryEmit(offlineUserNotice("Σύνδεση"))
            return
        }
        mutableState.value = AuthShellUiState.Loading
        viewModelScope.launch {
            try {
                when (val result = coordinator.signIn(email.trim(), passwordCopy)) {
                    is AuthAppState.Ready -> routeReady(result.session)
                    is AuthAppState.MfaRequired -> mutableState.value = AuthShellUiState.Mfa(result.session, result.factor)
                    AuthAppState.LoginRequired -> mutableState.value = AuthShellUiState.Login()
                    is AuthAppState.Unconfigured -> mutableState.value = AuthShellUiState.Unconfigured(
                        "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration.",
                    )
                    is AuthAppState.Failure -> {
                        reportAuthFailure(result.failure, "Σύνδεση")
                        mutableState.value = AuthShellUiState.Login(authFailureMessage(result.failure.kind))
                    }
                    is AuthAppState.Locked -> mutableState.value = lockedState(result.session)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = AuthShellUiState.Login("Η σύνδεση δεν ολοκληρώθηκε.")
                mutableNotices.emit(unexpectedUserNotice("Σύνδεση", error, "Η σύνδεση δεν ολοκληρώθηκε."))
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
        if (connectivityObserver.current() != NetworkStatus.ONLINE) {
            codeCopy.fill('\u0000')
            mutableState.value = current.copy(message = "Δεν υπάρχει σύνδεση για επαλήθευση δύο παραγόντων.")
            mutableNotices.tryEmit(offlineUserNotice("Επαλήθευση δύο παραγόντων"))
            return
        }
        mutableState.value = AuthShellUiState.Loading
        viewModelScope.launch {
            try {
                when (
                    val result = coordinator.completeTotp(
                        session = current.session,
                        factorId = current.factor.id,
                        code = codeCopy,
                    )
                ) {
                    is AuthAppState.Ready -> routeReady(result.session)
                    is AuthAppState.Failure -> {
                        reportAuthFailure(result.failure, "Επαλήθευση δύο παραγόντων")
                        mutableState.value = current.copy(message = authFailureMessage(result.failure.kind))
                    }
                    AuthAppState.LoginRequired -> mutableState.value = AuthShellUiState.Login()
                    is AuthAppState.MfaRequired -> mutableState.value = AuthShellUiState.Mfa(result.session, result.factor)
                    is AuthAppState.Unconfigured -> mutableState.value = AuthShellUiState.Unconfigured(
                        "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration.",
                    )
                    is AuthAppState.Locked -> mutableState.value = lockedState(result.session)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = current.copy(message = "Η επαλήθευση δεν ολοκληρώθηκε.")
                mutableNotices.emit(
                    unexpectedUserNotice(
                        operation = "Επαλήθευση δύο παραγόντων",
                        throwable = error,
                        message = "Η επαλήθευση δεν ολοκληρώθηκε.",
                    ),
                )
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
        validateUnlockedSession(current.session)
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
                    validateUnlockedSession(current.session)
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = current.copy(showPin = true, message = "Το τοπικό ξεκλείδωμα δεν ολοκληρώθηκε.")
                mutableNotices.emit(
                    unexpectedUserNotice(
                        operation = "Έλεγχος τοπικού PIN",
                        throwable = error,
                        message = "Το τοπικό ξεκλείδωμα δεν ολοκληρώθηκε.",
                    ),
                )
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = current.copy(message = "Η ασφαλής αποθήκευση του PIN δεν ολοκληρώθηκε.")
                mutableNotices.emit(
                    unexpectedUserNotice(
                        operation = "Ρύθμιση τοπικού PIN",
                        throwable = error,
                        message = "Η ασφαλής αποθήκευση του PIN δεν ολοκληρώθηκε.",
                    ),
                )
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
            try {
                coordinator.logout(session)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableNotices.emit(
                    unexpectedUserNotice(
                        operation = "Αποσύνδεση",
                        throwable = error,
                        message = "Η αποσύνδεση ολοκληρώθηκε τοπικά, αλλά ο καθαρισμός της συνεδρίας χρειάζεται έλεγχο.",
                    ),
                )
            } finally {
                mutableState.value = AuthShellUiState.Login()
            }
        }
    }

    private fun validateUnlockedSession(session: AuthSession) {
        viewModelScope.launch {
            if (connectivityObserver.current() != NetworkStatus.ONLINE) {
                mutableState.value = AuthShellUiState.Ready(session = session, offline = true)
                mutableNotices.emit(
                    UserNotice(
                        message = "Άνοιξε σε λειτουργία χωρίς σύνδεση.",
                        details = "Ενέργεια: Τοπικό ξεκλείδωμα\nΚατηγορία: OFFLINE_LOCAL_UNLOCK\nΧρησιμοποιούνται μόνο κρυπτογραφημένα δεδομένα αυτής της συσκευής. Η server συνεδρία θα επαληθευτεί πριν από συγχρονισμό.",
                        diagnosticCode = "MFH-AUTH-OFFLINE-LOCAL-UNLOCK",
                    ),
                )
                return@launch
            }
            mutableState.value = AuthShellUiState.Loading
            try {
                when (val result = coordinator.afterLocalUnlock(session)) {
                    is AuthAppState.Ready -> routeReady(result.session)
                    AuthAppState.LoginRequired -> mutableState.value = AuthShellUiState.Login(
                        "Η προηγούμενη συνεδρία έληξε. Συνδέσου ξανά.",
                    )
                    is AuthAppState.Failure -> {
                        reportAuthFailure(result.failure, "Έλεγχος συνεδρίας μετά το ξεκλείδωμα")
                        // Local authentication already succeeded. A transient server/network failure
                        // must not re-lock the owner or force a full login; product access stays
                        // cache-only until the stored server session can be revalidated.
                        mutableState.value = AuthShellUiState.Ready(
                            session = result.recoverableSession ?: session,
                            offline = true,
                        )
                    }
                    is AuthAppState.MfaRequired -> mutableState.value = AuthShellUiState.Mfa(result.session, result.factor)
                    is AuthAppState.Unconfigured -> mutableState.value = AuthShellUiState.Unconfigured(
                        "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration.",
                    )
                    is AuthAppState.Locked -> mutableState.value = lockedState(result.session)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = AuthShellUiState.Ready(session = session, offline = true)
                mutableNotices.emit(
                    unexpectedUserNotice(
                        operation = "Έλεγχος συνεδρίας μετά το ξεκλείδωμα",
                        throwable = error,
                        message = "Η επαλήθευση της συνεδρίας δεν ολοκληρώθηκε. Τα τοπικά δεδομένα παραμένουν διαθέσιμα.",
                    ),
                )
            }
        }
    }

    private fun validateOfflineReadySession(session: AuthSession) {
        if (offlineValidationInFlight) return
        offlineValidationInFlight = true
        viewModelScope.launch {
            try {
                when (val result = coordinator.afterLocalUnlock(session)) {
                    is AuthAppState.Ready -> routeReady(result.session)
                    AuthAppState.LoginRequired -> mutableState.value = AuthShellUiState.Login(
                        "Η προηγούμενη συνεδρία έληξε. Συνδέσου ξανά.",
                    )
                    is AuthAppState.MfaRequired -> mutableState.value = AuthShellUiState.Mfa(result.session, result.factor)
                    is AuthAppState.Unconfigured -> mutableState.value = AuthShellUiState.Unconfigured(
                        "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration.",
                    )
                    is AuthAppState.Locked -> mutableState.value = lockedState(result.session)
                    is AuthAppState.Failure -> {
                        reportAuthFailure(result.failure, "Επαλήθευση συνεδρίας μετά την επανασύνδεση")
                        mutableState.value = AuthShellUiState.Ready(
                            session = result.recoverableSession ?: session,
                            offline = true,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = AuthShellUiState.Ready(session = session, offline = true)
                mutableNotices.emit(
                    unexpectedUserNotice(
                        operation = "Επαλήθευση συνεδρίας μετά την επανασύνδεση",
                        throwable = error,
                        message = "Η επαλήθευση της συνεδρίας δεν ολοκληρώθηκε. Τα τοπικά δεδομένα παραμένουν διαθέσιμα.",
                    ),
                )
            } finally {
                offlineValidationInFlight = false
            }
        }
    }

    private suspend fun routeReady(session: AuthSession) {
        mutableState.value = if (pinVerifier.isEnrolled()) {
            AuthShellUiState.Ready(session = session, offline = false)
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

    private suspend fun reportAuthFailure(failure: AuthResult.Failure, operation: String) {
        if (failure.kind.shouldNotify()) {
            mutableNotices.emit(failure.toUserNotice(operation))
        }
    }

    private fun AuthFailureKind.shouldNotify(): Boolean = when (this) {
        AuthFailureKind.INVALID_CREDENTIALS,
        AuthFailureKind.INVALID_MFA_CODE,
        AuthFailureKind.MFA_REQUIRED -> false
        else -> true
    }

    private fun lockMessage(status: PinAttemptStatus): String {
        val seconds = ((status.retryAfterMillis + 999) / 1_000).coerceAtLeast(1)
        return "Το PIN κλειδώθηκε προσωρινά. Δοκίμασε ξανά σε ${seconds}″."
    }
}
