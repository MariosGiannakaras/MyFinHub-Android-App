package app.myfinhub.android.core.auth

import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.security.SessionStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AuthAppState {
    data class Unconfigured(val missingFields: List<String>) : AuthAppState
    data object LoginRequired : AuthAppState
    data class Locked(val session: AuthSession) : AuthAppState
    data class MfaRequired(val session: AuthSession, val factor: AuthFactor) : AuthAppState
    data class Ready(val session: AuthSession) : AuthAppState
    data class Failure(val failure: AuthResult.Failure, val recoverableSession: AuthSession? = null) : AuthAppState
}

/**
 * Coordinates server authentication separately from local app unlock.
 *
 * Biometric/PIN success only allows this coordinator to validate the stored server session; it does
 * not create, upgrade or authorize a Supabase session by itself.
 */
class AuthSessionCoordinator(
    private val configuration: AppConfiguration,
    private val authGateway: AuthGateway,
    private val sessionStore: SessionStore,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    // Serialize credential-store updates across concurrent network results and explicit logout.
    // A stale validation/refresh/TOTP response may no longer persist a bearer after logout.
    private val credentialMutex = Mutex()
    private var credentialEpoch = 0L

    private suspend fun captureCredentialEpoch(): Long = credentialMutex.withLock { credentialEpoch }

    private suspend fun isCredentialEpochCurrent(epoch: Long): Boolean =
        credentialMutex.withLock { credentialEpoch == epoch }

    private suspend fun persistIfCurrent(session: AuthSession, epoch: Long): Boolean =
        credentialMutex.withLock {
            if (credentialEpoch != epoch) false
            else {
                sessionStore.save(session)
                true
            }
        }

    suspend fun initialize(): AuthAppState {
        if (!configuration.isConfigured) return AuthAppState.Unconfigured(configuration.missingFields)
        return sessionStore.load()?.let(AuthAppState::Locked) ?: AuthAppState.LoginRequired
    }

    suspend fun signIn(email: String, password: CharArray): AuthAppState {
        val epoch = captureCredentialEpoch()
        val result = authGateway.signInWithPassword(email, password)
        if (!isCredentialEpochCurrent(epoch)) return AuthAppState.LoginRequired
        return when (result) {
            is AuthResult.Success -> routeAuthenticatedSession(result.value, epoch)
            is AuthResult.Failure -> AuthAppState.Failure(result)
        }
    }

    suspend fun afterLocalUnlock(session: AuthSession): AuthAppState {
        val epoch = captureCredentialEpoch()
        val validated = validateOrRefresh(session)
        if (!isCredentialEpochCurrent(epoch)) return AuthAppState.LoginRequired
        return when (validated) {
            is AuthResult.Success -> routeAuthenticatedSession(validated.value, epoch)
            is AuthResult.Failure -> {
                if (validated.kind == AuthFailureKind.UNAUTHORIZED || validated.kind == AuthFailureKind.SESSION_EXPIRED) {
                    credentialMutex.withLock {
                        // A delayed rejection must not erase credentials from a later auth generation.
                        if (credentialEpoch == epoch) sessionStore.clear()
                    }
                    AuthAppState.LoginRequired
                } else {
                    if (isCredentialEpochCurrent(epoch)) AuthAppState.Failure(validated, recoverableSession = session)
                    else AuthAppState.LoginRequired
                }
            }
        }
    }

    suspend fun completeTotp(
        session: AuthSession,
        factorId: String,
        code: CharArray,
    ): AuthAppState {
        val epoch = captureCredentialEpoch()
        val challenge = authGateway.challengeTotp(session.accessToken, factorId)
        if (!isCredentialEpochCurrent(epoch)) return AuthAppState.LoginRequired
        if (challenge is AuthResult.Failure) return AuthAppState.Failure(challenge, session)
        challenge as AuthResult.Success

        val verification = authGateway.verifyTotp(
            accessToken = session.accessToken,
            factorId = factorId,
            challengeId = challenge.value.id,
            code = code,
        )
        if (!isCredentialEpochCurrent(epoch)) return AuthAppState.LoginRequired
        return when (verification) {
            is AuthResult.Success -> {
                if (verification.value.assuranceLevel != AssuranceLevel.AAL2) {
                    AuthAppState.Failure(
                        AuthResult.Failure(AuthFailureKind.MFA_REQUIRED),
                        recoverableSession = verification.value,
                    )
                } else {
                    if (persistIfCurrent(verification.value, epoch)) AuthAppState.Ready(verification.value)
                    else AuthAppState.LoginRequired
                }
            }
            is AuthResult.Failure -> AuthAppState.Failure(verification, session)
        }
    }

    suspend fun logout(session: AuthSession?): AuthAppState {
        // Invalidate every in-flight authentication generation and clear credentials atomically,
        // before waiting on remote sign-out. Late responses cannot repopulate the encrypted store.
        credentialMutex.withLock {
            credentialEpoch += 1
            sessionStore.clear()
        }
        session?.let { authGateway.signOut(it.accessToken) }
        return AuthAppState.LoginRequired
    }

    private suspend fun validateOrRefresh(session: AuthSession): AuthResult<AuthSession> {
        if (session.expiresWithin(nowEpochSeconds())) {
            return authGateway.refreshSession(session.refreshToken)
        }

        return when (val validation = authGateway.validateSession(session.accessToken)) {
            is AuthResult.Success -> AuthResult.Success(session)
            is AuthResult.Failure -> if (
                validation.kind == AuthFailureKind.UNAUTHORIZED ||
                validation.kind == AuthFailureKind.SESSION_EXPIRED
            ) {
                authGateway.refreshSession(session.refreshToken)
            } else {
                validation
            }
        }
    }

    private suspend fun routeAuthenticatedSession(session: AuthSession, epoch: Long): AuthAppState {
        if (session.assuranceLevel == AssuranceLevel.AAL2) {
            return if (persistIfCurrent(session, epoch)) AuthAppState.Ready(session)
            else AuthAppState.LoginRequired
        }

        val factorsResult = authGateway.listFactors(session.accessToken)
        if (!isCredentialEpochCurrent(epoch)) return AuthAppState.LoginRequired
        return when (factorsResult) {
            is AuthResult.Success -> {
                val factor = factorsResult.value.firstOrNull(AuthFactor::isVerifiedTotp)
                if (factor == null) {
                    AuthAppState.Failure(
                        AuthResult.Failure(
                            kind = AuthFailureKind.MFA_REQUIRED,
                            message = "No verified TOTP factor is available for the owner account.",
                        ),
                        recoverableSession = session,
                    )
                } else {
                    AuthAppState.MfaRequired(session, factor)
                }
            }
            is AuthResult.Failure -> AuthAppState.Failure(factorsResult, recoverableSession = session)
        }
    }
}
