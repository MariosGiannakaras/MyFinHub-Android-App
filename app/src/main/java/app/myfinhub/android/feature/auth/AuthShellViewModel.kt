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
import app.myfinhub.android.core.network.SharedAppLockApi
import app.myfinhub.android.core.network.SharedAppLockResult
import app.myfinhub.android.core.network.SharedAppLockFailure
import app.myfinhub.android.core.network.SharedAppLockVerification
import app.myfinhub.android.core.network.productionSharedAppLockAuthority
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Existing local authentication has completed; the server privacy gate has not. */
    data class SharedLocked(
        val session: AuthSession,
        val requiresPin: Boolean = false,
        val retryAfterMillis: Long = 0,
        val busy: Boolean = false,
        val message: String? = null,
    ) : AuthShellUiState {
        override fun toString() = "SharedLocked(<redacted>)"
    }

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

/**
 * A reconnect response belongs only to the offline-ready owner and auth generation
 * which started it. A logout or session transition must never re-open the finance UI.
 */
internal fun mayPublishOfflineRevalidation(
    expectedSession: AuthSession,
    currentState: AuthShellUiState,
    attemptEpoch: Long,
    currentEpoch: Long,
): Boolean {
    val ready = currentState as? AuthShellUiState.Ready ?: return false
    return attemptEpoch == currentEpoch && ready.offline && ready.session == expectedSession
}

/** A locked PIN/biometric completion may only act on the same visible locked session. */
internal fun mayResumeLockedAuth(
    expectedSession: AuthSession,
    state: AuthShellUiState,
    startedEpoch: Long,
    currentEpoch: Long,
): Boolean = startedEpoch == currentEpoch &&
    (state as? AuthShellUiState.Locked)?.session == expectedSession

/** Loading is the only valid destination of a pending initial server session validation. */
internal fun mayPublishInitialUnlock(
    state: AuthShellUiState,
    startedEpoch: Long,
    currentEpoch: Long,
): Boolean = startedEpoch == currentEpoch && state is AuthShellUiState.Loading

/** PIN enrollment cannot unlock a different owner or a session which has logged out. */
internal fun mayFinishPinEnrollment(
    expectedSession: AuthSession,
    state: AuthShellUiState,
    startedEpoch: Long,
    currentEpoch: Long,
): Boolean = startedEpoch == currentEpoch &&
    (state as? AuthShellUiState.PinEnrollment)?.session == expectedSession

class AuthShellViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val configuration = AppConfiguration.fromBuildConfig()
    private val connectivityObserver = AndroidConnectivityObserver(application)
    private val httpClient = NetworkClientFactory.create()
    private val authGateway = SupabaseAuthGateway(configuration, httpClient)
    private val sharedLockApi = SharedAppLockApi(configuration, httpClient)
    private val sharedAuthority = productionSharedAppLockAuthority
    private val sharedMutex = Mutex()
    internal val sharedLockRevision = sharedAuthority.revision
    internal fun canPresentSharedLock(session: AuthSession) = sharedAuthority.canPresent(session)
    internal fun currentSharedReady() = (mutableState.value as? AuthShellUiState.Ready)
        ?.takeIf { sharedAuthority.canPresent(it.session) }
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
    private var offlineValidationJob: Job? = null
    private var initialUnlockJob: Job? = null
    private var authenticationJob: Job? = null
    private var sharedJob: Job? = null
    private var leaseJob: Job? = null
    private var touchJob: Job? = null
    private var authTransitionEpoch = 0L
    private var appForeground = true
    private var validatingUnlockSession: AuthSession? = null

    private fun invalidateOfflineRevalidation() {
        authTransitionEpoch += 1
        offlineValidationJob?.cancel()
        offlineValidationJob = null
        offlineValidationInFlight = false
        initialUnlockJob?.cancel()
        initialUnlockJob = null
        authenticationJob?.cancel()
        authenticationJob = null
        sharedJob?.cancel()
        sharedJob = null
        leaseJob?.cancel()
        leaseJob = null
        touchJob?.cancel()
        touchJob = null
    }

    init {
        initialize()
        viewModelScope.launch {
            connectivityObserver.status.drop(1).collect { status ->
                val current = mutableState.value as? AuthShellUiState.Ready ?: return@collect
                if (status != NetworkStatus.ONLINE) {
                    // A live server PIN ticket cannot establish offline authorization.
                    if (sharedAuthority.requestGrant(current.session)?.ticket != null) {
                        dropSharedAuthority(current.session)
                        lockAfterSharedLoss(current.session)
                    } else if (!current.offline) {
                        // Reconnect must re-check server PIN status before any sync;
                        // an offline grant cannot send protected requests.
                        offlineAfterLocalUnlock(current.session)
                    }
                    return@collect
                }
                if (current.offline) validateOfflineReadySession(current.session)
            }
        }
        viewModelScope.launch {
            sharedAuthority.revision.collect {
                val current = mutableState.value as? AuthShellUiState.Ready ?: return@collect
                if (!sharedAuthority.canPresent(current.session)) lockAfterSharedLoss(current.session)
            }
        }
    }

    fun initialize() {
        invalidateOfflineRevalidation()
        sharedAuthority.clear()
        val epoch = authTransitionEpoch
        mutableState.value = AuthShellUiState.Loading
        viewModelScope.launch {
            try {
                val result = coordinator.initialize()
                if (!mayPublishInitialUnlock(mutableState.value, epoch, authTransitionEpoch)) return@launch
                when (result) {
                    is AuthAppState.Unconfigured -> mutableState.value = AuthShellUiState.Unconfigured(
                        "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration.",
                    )
                    AuthAppState.LoginRequired -> mutableState.value = AuthShellUiState.Login()
                    is AuthAppState.Locked -> {
                        if (pinVerifier.isEnrolled()) {
                            val locked = lockedState(result.session)
                            if (mayPublishInitialUnlock(mutableState.value, epoch, authTransitionEpoch)) {
                                mutableState.value = locked
                            }
                        } else {
                            coordinator.logout(result.session)
                            pinLimiter.recordSuccess()
                            if (mayPublishInitialUnlock(mutableState.value, epoch, authTransitionEpoch)) {
                                mutableState.value = AuthShellUiState.Login(
                                    "Η τοπική ρύθμιση ξεκλειδώματος δεν ολοκληρώθηκε. Συνδέσου ξανά.",
                                )
                            }
                        }
                    }
                    is AuthAppState.Ready -> routeReady(result.session) {
                        mayPublishInitialUnlock(mutableState.value, epoch, authTransitionEpoch)
                    }
                    is AuthAppState.MfaRequired -> mutableState.value = AuthShellUiState.Mfa(result.session, result.factor)
                    is AuthAppState.Failure -> {
                        reportAuthFailure(result.failure, "Αρχικοποίηση συνεδρίας")
                        mutableState.value = AuthShellUiState.Login(authFailureMessage(result.failure.kind))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (mayPublishInitialUnlock(mutableState.value, epoch, authTransitionEpoch)) {
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
        invalidateOfflineRevalidation()
        val epoch = authTransitionEpoch
        mutableState.value = AuthShellUiState.Loading
        authenticationJob = viewModelScope.launch {
            fun stillCurrent() = mayPublishInitialUnlock(
                mutableState.value, epoch, authTransitionEpoch,
            )
            try {
                val result = coordinator.signIn(email.trim(), passwordCopy)
                if (!stillCurrent()) return@launch
                when (result) {
                    is AuthAppState.Ready -> routeReady(result.session, ::stillCurrent)
                    is AuthAppState.MfaRequired -> mutableState.value = AuthShellUiState.Mfa(result.session, result.factor)
                    AuthAppState.LoginRequired -> mutableState.value = AuthShellUiState.Login()
                    is AuthAppState.Unconfigured -> mutableState.value = AuthShellUiState.Unconfigured(
                        "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration.",
                    )
                    is AuthAppState.Failure -> {
                        reportAuthFailure(result.failure, "Σύνδεση")
                        if (stillCurrent()) mutableState.value = AuthShellUiState.Login(authFailureMessage(result.failure.kind))
                    }
                    is AuthAppState.Locked -> {
                        val locked = lockedState(result.session)
                        if (stillCurrent()) mutableState.value = locked
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (stillCurrent()) {
                    mutableState.value = AuthShellUiState.Login("Η σύνδεση δεν ολοκληρώθηκε.")
                    mutableNotices.emit(unexpectedUserNotice("Σύνδεση", error, "Η σύνδεση δεν ολοκληρώθηκε."))
                }
            } finally {
                passwordCopy.fill('\u0000')
                if (authTransitionEpoch == epoch) authenticationJob = null
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
        invalidateOfflineRevalidation()
        val epoch = authTransitionEpoch
        mutableState.value = AuthShellUiState.Loading
        authenticationJob = viewModelScope.launch {
            fun stillCurrent() = mayPublishInitialUnlock(
                mutableState.value, epoch, authTransitionEpoch,
            )
            try {
                val result = coordinator.completeTotp(
                    session = current.session,
                    factorId = current.factor.id,
                    code = codeCopy,
                )
                if (!stillCurrent()) return@launch
                when (result) {
                    is AuthAppState.Ready -> routeReady(result.session, ::stillCurrent)
                    is AuthAppState.Failure -> {
                        reportAuthFailure(result.failure, "Επαλήθευση δύο παραγόντων")
                        if (stillCurrent()) {
                            mutableState.value = current.copy(message = authFailureMessage(result.failure.kind))
                        }
                    }
                    AuthAppState.LoginRequired -> mutableState.value = AuthShellUiState.Login()
                    is AuthAppState.MfaRequired -> mutableState.value = AuthShellUiState.Mfa(result.session, result.factor)
                    is AuthAppState.Unconfigured -> mutableState.value = AuthShellUiState.Unconfigured(
                        "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration.",
                    )
                    is AuthAppState.Locked -> {
                        val locked = lockedState(result.session)
                        if (stillCurrent()) mutableState.value = locked
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (stillCurrent()) {
                    mutableState.value = current.copy(message = "Η επαλήθευση δεν ολοκληρώθηκε.")
                    mutableNotices.emit(
                        unexpectedUserNotice(
                            operation = "Επαλήθευση δύο παραγόντων",
                            throwable = error,
                            message = "Η επαλήθευση δεν ολοκληρώθηκε.",
                        ),
                    )
                }
            } finally {
                codeCopy.fill('\u0000')
                if (authTransitionEpoch == epoch) authenticationJob = null
            }
        }
    }

    fun onAppForegrounded() {
        appForeground = true
    }

    /** Revoke finance access synchronously on host Activity stop; do not clear encrypted data. */
    fun onAppBackgrounded() {
        appForeground = false
        val session = when (val current = mutableState.value) {
            is AuthShellUiState.Ready -> current.session
            is AuthShellUiState.Locked -> current.session
            is AuthShellUiState.SharedLocked -> current.session
            AuthShellUiState.Loading -> validatingUnlockSession
            else -> null
        } ?: return
        // Invalidate any PIN/biometric completion or server revalidation racing ON_STOP.
        invalidateOfflineRevalidation()
        validatingUnlockSession = null
        // Block first; the server revocation is best effort and serialized with
        // verification/touch so a later local verification cannot race an old lock.
        dropSharedAuthority(session, rememberDisabled = true)
        mutableState.value = AuthShellUiState.Locked(
            session = session,
            showPin = false,
            // Provisional UI only; PIN verification consults persisted throttle.
            pinStatus = app.myfinhub.android.core.security.PinAttemptStatus(true, 5),
        )
        val epoch = authTransitionEpoch
        viewModelScope.launch {
            val currentThrottle = pinLimiter.status(System.currentTimeMillis())
            val locked = mutableState.value as? AuthShellUiState.Locked ?: return@launch
            if (authTransitionEpoch == epoch && locked.session == session) {
                mutableState.value = locked.copy(pinStatus = currentThrottle)
            }
        }
    }

    fun requestPinFallback() {
        val current = mutableState.value as? AuthShellUiState.Locked ?: return
        mutableState.value = current.copy(showPin = true, message = null)
    }

    fun biometricSucceeded() {
        if (!appForeground) return
        val current = mutableState.value as? AuthShellUiState.Locked ?: return
        validateUnlockedSession(current.session)
    }

    fun verifyPin(pin: CharArray) {
        if (!appForeground) {
            pin.fill('\u0000')
            return
        }
        val current = mutableState.value as? AuthShellUiState.Locked ?: run {
            pin.fill('\u0000')
            return
        }
        val epoch = authTransitionEpoch
        val pinCopy = pin.copyOf()
        pin.fill('\u0000')
        viewModelScope.launch {
            fun stillLocked() = mayResumeLockedAuth(
                current.session, mutableState.value, epoch, authTransitionEpoch,
            )
            try {
                if (!stillLocked()) return@launch
                val now = System.currentTimeMillis()
                val status = pinLimiter.status(now)
                if (!stillLocked()) return@launch
                if (!status.allowed) {
                    mutableState.value = current.copy(
                        showPin = true,
                        pinStatus = status,
                        message = lockMessage(status),
                    )
                    return@launch
                }

                if (pinVerifier.verify(pinCopy)) {
                    if (!stillLocked()) return@launch
                    pinLimiter.recordSuccess()
                    if (stillLocked()) validateUnlockedSession(current.session)
                } else {
                    if (!stillLocked()) return@launch
                    val next = pinLimiter.recordFailure(now)
                    if (stillLocked()) {
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
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (stillLocked()) {
                    mutableState.value = current.copy(showPin = true, message = "Το τοπικό ξεκλείδωμα δεν ολοκληρώθηκε.")
                    mutableNotices.emit(
                        unexpectedUserNotice(
                            operation = "Έλεγχος τοπικού PIN",
                            throwable = error,
                            message = "Το τοπικό ξεκλείδωμα δεν ολοκληρώθηκε.",
                        ),
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
        val epoch = authTransitionEpoch
        val pinCopy = pin.copyOf()
        val confirmationCopy = confirmation.copyOf()
        pin.fill('\u0000')
        confirmation.fill('\u0000')
        viewModelScope.launch {
            fun stillEnrolling() = mayFinishPinEnrollment(
                current.session, mutableState.value, epoch, authTransitionEpoch,
            )
            try {
                if (!stillEnrolling()) return@launch
                if (!pinCopy.contentEquals(confirmationCopy)) {
                    mutableState.value = current.copy(message = "Τα PIN δεν ταιριάζουν.")
                    return@launch
                }
                if (pinCopy.size !in 4..12 || pinCopy.any { !it.isDigit() }) {
                    mutableState.value = current.copy(message = "Το PIN πρέπει να έχει 4–12 ψηφία.")
                    return@launch
                }
                pinVerifier.enroll(pinCopy)
                if (!stillEnrolling()) return@launch
                pinLimiter.recordSuccess()
                if (stillEnrolling()) routeSharedReady(current.session, ::stillEnrolling)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (stillEnrolling()) {
                    mutableState.value = current.copy(message = "Η ασφαλής αποθήκευση του PIN δεν ολοκληρώθηκε.")
                    mutableNotices.emit(
                        unexpectedUserNotice(
                            operation = "Ρύθμιση τοπικού PIN",
                            throwable = error,
                            message = "Η ασφαλής αποθήκευση του PIN δεν ολοκληρώθηκε.",
                        ),
                    )
                }
            } finally {
                pinCopy.fill('\u0000')
                confirmationCopy.fill('\u0000')
            }
        }
    }

    fun logout() {
        try {
            app.myfinhub.android.feature.bankalert.BankSmsInboxStore(getApplication<Application>()).disable()
            app.myfinhub.android.feature.bankalert.clearBankSmsNotification(getApplication<Application>())
        } catch (_: Exception) { /* Local sign-out must still proceed. */ }
        val session = when (val current = mutableState.value) {
            is AuthShellUiState.Ready -> current.session
            is AuthShellUiState.Locked -> current.session
            is AuthShellUiState.PinEnrollment -> current.session
            is AuthShellUiState.SharedLocked -> current.session
            is AuthShellUiState.Mfa -> current.session
            else -> null
        }
        // Revoke visible finance access synchronously, before asynchronous server sign-out.
        // Cancel the reconnect check: its stale result must not restore Ready after logout.
        invalidateOfflineRevalidation()
        if (session != null) dropSharedAuthority(session) else sharedAuthority.clear()
        mutableState.value = AuthShellUiState.Loading
        viewModelScope.launch {
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
        if ((mutableState.value as? AuthShellUiState.Locked)?.session != session) return
        // Enter Loading and invalidate prior work before a potentially long server check.
        invalidateOfflineRevalidation()
        val epoch = authTransitionEpoch
        validatingUnlockSession = session
        mutableState.value = AuthShellUiState.Loading
        initialUnlockJob = viewModelScope.launch {
            fun stillCurrent() = mayPublishInitialUnlock(
                mutableState.value, epoch, authTransitionEpoch,
            )
            try {
                if (!stillCurrent()) return@launch
                if (connectivityObserver.current() != NetworkStatus.ONLINE) {
                    if (!offlineAfterLocalUnlock(session)) return@launch
                    mutableNotices.emit(
                        UserNotice(
                            message = "Άνοιξε σε λειτουργία χωρίς σύνδεση.",
                            details = "Ενέργεια: Τοπικό ξεκλείδωμα\nΚατηγορία: OFFLINE_LOCAL_UNLOCK\nΧρησιμοποιούνται μόνο κρυπτογραφημένα δεδομένα αυτής της συσκευής. Η server συνεδρία θα επαληθευτεί πριν από συγχρονισμό.",
                            diagnosticCode = "MFH-AUTH-OFFLINE-LOCAL-UNLOCK",
                        ),
                    )
                    return@launch
                }
                val result = coordinator.afterLocalUnlock(session)
                if (!stillCurrent()) return@launch
                when (result) {
                    is AuthAppState.Ready -> routeReady(result.session, ::stillCurrent)
                    AuthAppState.LoginRequired -> mutableState.value = AuthShellUiState.Login(
                        "Η προηγούμενη συνεδρία έληξε. Συνδέσου ξανά.",
                    )
                    is AuthAppState.Failure -> {
                        reportAuthFailure(result.failure, "Έλεγχος συνεδρίας μετά το ξεκλείδωμα")
                        if (stillCurrent()) {
                            offlineAfterLocalUnlock(result.recoverableSession ?: session)
                        }
                    }
                    is AuthAppState.MfaRequired -> mutableState.value = AuthShellUiState.Mfa(result.session, result.factor)
                    is AuthAppState.Unconfigured -> mutableState.value = AuthShellUiState.Unconfigured(
                        "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration.",
                    )
                    is AuthAppState.Locked -> {
                        val locked = lockedState(result.session)
                        if (stillCurrent()) mutableState.value = locked
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (stillCurrent()) {
                    if (!offlineAfterLocalUnlock(session)) return@launch
                    mutableNotices.emit(
                        unexpectedUserNotice(
                            operation = "Έλεγχος συνεδρίας μετά το ξεκλείδωμα",
                            throwable = error,
                            message = "Η επαλήθευση της συνεδρίας δεν ολοκληρώθηκε. Τα τοπικά δεδομένα παραμένουν διαθέσιμα.",
                        ),
                    )
                }
            } finally {
                if (authTransitionEpoch == epoch) {
                    initialUnlockJob = null
                    validatingUnlockSession = null
                }
            }
        }
    }

    private fun validateOfflineReadySession(session: AuthSession) {
        val epoch = authTransitionEpoch
        if (offlineValidationInFlight ||
            !mayPublishOfflineRevalidation(session, mutableState.value, epoch, authTransitionEpoch)
        ) return
        offlineValidationInFlight = true
        offlineValidationJob = viewModelScope.launch {
            fun stillCurrent() = mayPublishOfflineRevalidation(
                session, mutableState.value, epoch, authTransitionEpoch,
            )
            try {
                val result = coordinator.afterLocalUnlock(session)
                if (!stillCurrent()) return@launch
                when (result) {
                    is AuthAppState.Ready -> routeReady(result.session, ::stillCurrent)
                    AuthAppState.LoginRequired -> mutableState.value = AuthShellUiState.Login(
                        "Η προηγούμενη συνεδρία έληξε. Συνδέσου ξανά.",
                    )
                    is AuthAppState.MfaRequired -> mutableState.value = AuthShellUiState.Mfa(result.session, result.factor)
                    is AuthAppState.Unconfigured -> mutableState.value = AuthShellUiState.Unconfigured(
                        "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration.",
                    )
                    is AuthAppState.Locked -> {
                        val locked = lockedState(result.session)
                        if (stillCurrent()) mutableState.value = locked
                    }
                    is AuthAppState.Failure -> {
                        reportAuthFailure(result.failure, "Επαλήθευση συνεδρίας μετά την επανασύνδεση")
                        if (stillCurrent()) {
                            offlineAfterLocalUnlock(result.recoverableSession ?: session)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (stillCurrent()) {
                    if (!offlineAfterLocalUnlock(session)) return@launch
                    mutableNotices.emit(
                        unexpectedUserNotice(
                            operation = "Επαλήθευση συνεδρίας μετά την επανασύνδεση",
                            throwable = error,
                            message = "Η επαλήθευση της συνεδρίας δεν ολοκληρώθηκε. Τα τοπικά δεδομένα παραμένουν διαθέσιμα.",
                        ),
                    )
                }
            } finally {
                if (authTransitionEpoch == epoch) {
                    offlineValidationInFlight = false
                    offlineValidationJob = null
                }
            }
        }
    }

    private suspend fun routeReady(
        session: AuthSession,
        stillAuthorized: () -> Boolean = { true },
    ) {
        val pinEnrolled = pinVerifier.isEnrolled()
        if (!stillAuthorized()) return
        if (!pinEnrolled) {
            mutableState.value = AuthShellUiState.PinEnrollment(session)
        } else if (!appForeground) {
            val locked = lockedState(session)
            if (stillAuthorized()) mutableState.value = locked
        } else routeSharedReady(session, stillAuthorized)
    }

    // No offline PIN bypass. A prior disabled response is retained only in process memory,
    // for the exact previously validated bearer. A cold/offline start must reconnect.
    private fun offlineAfterLocalUnlock(session: AuthSession): Boolean {
        if (appForeground && sharedAuthority.publishOfflineDisabled(session)) {
            mutableState.value = AuthShellUiState.Ready(session, offline = true)
            return true
        }
        sharedAuthority.clear()
        mutableState.value = AuthShellUiState.SharedLocked(
            session, message = "Συνδέσου στο διαδίκτυο για έλεγχο του κοινού PIN.",
        )
        return false
    }

    private fun stillShared(state: AuthShellUiState.SharedLocked, epoch: Long) =
        appForeground && epoch == authTransitionEpoch && mutableState.value === state

    private suspend fun routeSharedReady(session: AuthSession, stillAuthorized: () -> Boolean) {
        if (!appForeground || !stillAuthorized()) return
        val checking = AuthShellUiState.SharedLocked(session, busy = true)
        val epoch = authTransitionEpoch
        mutableState.value = checking
        sharedAuthority.clear()
        val result = sharedMutex.withLock { sharedLockApi.status(session) }
        if (!stillShared(checking, epoch)) return
        when (result) {
            is SharedAppLockResult.Success -> {
                if (result.value.enabled) {
                    mutableState.value = checking.copy(
                        busy = false, requiresPin = true,
                        retryAfterMillis = result.value.retryAfterMs,
                    )
                } else {
                    sharedAuthority.publishDisabled(session)
                    mutableState.value = AuthShellUiState.Ready(session)
                }
            }
            is SharedAppLockResult.Failure -> mutableState.value = checking.copy(
                busy = false, message = sharedFailureMessage(result.kind),
            )
        }
    }

    fun retrySharedStatus() {
        val current = mutableState.value as? AuthShellUiState.SharedLocked ?: return
        if (!appForeground || current.busy) return
        val checking = current.copy(busy = true, message = null)
        val epoch = authTransitionEpoch
        mutableState.value = checking
        sharedJob = viewModelScope.launch {
            try {
                when (val auth = coordinator.afterLocalUnlock(current.session)) {
                    is AuthAppState.Ready -> routeSharedReady(auth.session) { stillShared(checking, epoch) }
                    AuthAppState.LoginRequired -> if (stillShared(checking, epoch)) {
                        mutableState.value = AuthShellUiState.Login("Η προηγούμενη συνεδρία έληξε. Συνδέσου ξανά.")
                    }
                    is AuthAppState.MfaRequired -> if (stillShared(checking, epoch)) {
                        mutableState.value = AuthShellUiState.Mfa(auth.session, auth.factor)
                    }
                    else -> if (stillShared(checking, epoch)) {
                        mutableState.value = checking.copy(busy = false, message = "Δεν ήταν δυνατός ο έλεγχος συνεδρίας.")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (stillShared(checking, epoch)) mutableState.value = checking.copy(
                    busy = false, message = "Δεν ήταν δυνατός ο έλεγχος συνεδρίας.",
                )
            }
        }
    }

    fun verifySharedPin(pin: CharArray) {
        val current = mutableState.value as? AuthShellUiState.SharedLocked
        if (current == null || !appForeground || !current.requiresPin || current.busy) {
            pin.fill('\u0000')
            return
        }
        val copy = pin.copyOf()
        pin.fill('\u0000')
        val checking = current.copy(busy = true, message = null)
        val epoch = authTransitionEpoch
        mutableState.value = checking
        sharedJob = viewModelScope.launch {
            try {
                val verified = sharedMutex.withLock {
                    if (!stillShared(checking, epoch)) return@withLock null
                    val startedAt = monotonicMillis()
                    sharedLockApi.verify(current.session, copy) to startedAt
                } ?: return@launch
                if (!stillShared(checking, epoch)) return@launch
                when (val result = verified.first) {
                    is SharedAppLockResult.Success -> when (val value = result.value) {
                        is SharedAppLockVerification.Verified -> {
                            if (sharedAuthority.publishVerified(current.session, value, verified.second)) {
                                mutableState.value = AuthShellUiState.Ready(current.session)
                                watchSharedLease(current.session)
                            } else {
                                mutableState.value = checking.copy(busy = false, message = "Απαιτείται νέο ξεκλείδωμα.")
                            }
                        }
                        is SharedAppLockVerification.Rejected -> {
                            val reason = value.reason
                            mutableState.value = checking.copy(
                                busy = false, requiresPin = value.status.enabled,
                                retryAfterMillis = value.status.retryAfterMs,
                                message = when (reason) {
                                    SharedAppLockVerification.Rejection.WRONG_PIN -> "Λάθος κοινό PIN."
                                    SharedAppLockVerification.Rejection.RATE_LIMITED -> "Πολλές προσπάθειες. Δοκίμασε ξανά σε λίγο."
                                    SharedAppLockVerification.Rejection.NOT_CONFIGURED -> "Η ρύθμιση άλλαξε. Έλεγξε ξανά την κατάσταση."
                                },
                            )
                        }
                    }
                    is SharedAppLockResult.Failure -> mutableState.value = checking.copy(
                        busy = false, message = sharedFailureMessage(result.kind),
                    )
                }
            } finally {
                copy.fill('\u0000')
            }
        }
    }

    fun recoverSharedPin(email: String, password: CharArray, code: CharArray, newPin: CharArray) {
        val current = mutableState.value as? AuthShellUiState.SharedLocked
        if (current == null || !appForeground || !current.requiresPin || current.busy) {
            password.fill('\u0000'); code.fill('\u0000'); newPin.fill('\u0000')
            return
        }
        val passwordCopy = password.copyOf()
        val codeCopy = code.copyOf()
        val pinCopy = newPin.copyOf()
        password.fill('\u0000'); code.fill('\u0000'); newPin.fill('\u0000')
        val checking = current.copy(busy = true, message = null)
        val epoch = authTransitionEpoch
        mutableState.value = checking
        sharedJob = viewModelScope.launch {
            try {
                val result = sharedMutex.withLock {
                    if (!stillShared(checking, epoch)) return@withLock null
                    sharedLockApi.recover(current.session, email, passwordCopy, codeCopy, pinCopy)
                } ?: return@launch
                if (!stillShared(checking, epoch)) return@launch
                // Recovery revokes server tickets; it is never an unlock.
                sharedAuthority.clear()
                mutableState.value = when (result) {
                    is SharedAppLockResult.Success -> checking.copy(
                        busy = false, retryAfterMillis = 0L,
                        message = "Το κοινό PIN άλλαξε. Ξεκλείδωσε με το νέο PIN.",
                    )
                    is SharedAppLockResult.Failure -> checking.copy(
                        busy = false, message = sharedFailureMessage(result.kind),
                    )
                }
            } finally {
                passwordCopy.fill('\u0000'); codeCopy.fill('\u0000'); pinCopy.fill('\u0000')
            }
        }
    }

    fun onUserInteraction() {
        if (!appForeground || touchJob?.isActive == true) return
        val current = mutableState.value as? AuthShellUiState.Ready ?: return
        val grant = sharedAuthority.beginTouch(current.session) ?: return
        val ticket = grant.ticket ?: return
        touchJob = viewModelScope.launch {
            sharedMutex.withLock {
                if (!appForeground || !sharedAuthority.isCurrent(grant)) return@withLock
                val startedAt = monotonicMillis()
                val result = sharedLockApi.touch(current.session, ticket)
                if (result is SharedAppLockResult.Success) {
                    if (!sharedAuthority.renew(grant, startedAt)) sharedAuthority.reject(grant)
                } else sharedAuthority.reject(grant)
            }
        }
    }

    private fun watchSharedLease(session: AuthSession) {
        leaseJob?.cancel()
        leaseJob = viewModelScope.launch {
            while ((mutableState.value as? AuthShellUiState.Ready)?.session == session) {
                delay(500)
                if (sharedAuthority.expireIfNeeded(session)) return@launch
            }
        }
    }

    private fun lockAfterSharedLoss(session: AuthSession) {
        if ((mutableState.value as? AuthShellUiState.Ready)?.session != session) return
        invalidateOfflineRevalidation()
        mutableState.value = AuthShellUiState.SharedLocked(
            session, message = "Απαιτείται νέος έλεγχος του κοινού PIN.",
        )
    }

    private fun dropSharedAuthority(session: AuthSession, rememberDisabled: Boolean = false) {
        val ticket = sharedAuthority.clear(rememberDisabled) ?: return
        if (ticket.valueFor(session) == null) return
        viewModelScope.launch {
            sharedMutex.withLock { sharedLockApi.lock(session, ticket) }
        }
    }

    private fun sharedFailureMessage(kind: SharedAppLockFailure): String = when (kind) {
        SharedAppLockFailure.INVALID_INPUT -> "Έλεγξε τα στοιχεία που πληκτρολόγησες."
        SharedAppLockFailure.RATE_LIMITED -> "Πολλές προσπάθειες. Δοκίμασε αργότερα."
        SharedAppLockFailure.AUTH_REQUIRED, SharedAppLockFailure.MFA_REQUIRED, SharedAppLockFailure.FORBIDDEN ->
            "Δεν επιβεβαιώθηκε η ασφαλής πρόσβαση. Έλεγξε ξανά τη συνεδρία."
        else -> "Η προστασία του κοινού PIN δεν είναι διαθέσιμη. Δοκίμασε ξανά."
    }

    private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000

    override fun onCleared() {
        sharedAuthority.clear()
        super.onCleared()
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
