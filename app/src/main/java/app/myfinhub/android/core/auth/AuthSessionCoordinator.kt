package app.myfinhub.android.core.auth

import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.security.SessionStore
import java.util.concurrent.atomic.AtomicLong
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
    // A late credential refresh, password response, TOTP verification or rejected session
    // must not rewrite credentials after logout or clear those of a subsequent session.
    private val authGeneration = AtomicLong(0L)
    private val sessionWriteGate = Mutex()

    suspend fun initialize(): AuthAppState {
        if (!configuration.isConfigured) return AuthAppState.Unconfigured(configuration.missingFields)
        return sessionStore.load()?.let(AuthAppState::Locked) ?: AuthAppState.LoginRequired
    }

    suspend fun signIn(email: String, password: CharArray): AuthAppState {
        val generation = authGeneration.get()
        return when (val result = authGateway.signInWithPassword(email, password)) {
            is AuthResult.Success -> routeAuthenticatedSession(result.value, generation)
            is AuthResult.Failure -> if (authGeneration.get() == generation) {
                AuthAppState.Failure(result)
            } else AuthAppState.LoginRequired
        }
    }

    suspend fun afterLocalUnlock(session: AuthSession): AuthAppState {
        val generation = authGeneration.get()
        val validated = validateOrRefresh(session)
        if (authGeneration.get() != generation) return AuthAppState.LoginRequired
        return when (validated) {
            is AuthResult.Success -> routeAuthenticatedSession(validated.value, generation)
            is AuthResult.Failure -> {
                if (validated.kind == AuthFailureKind.UNAUTHORIZED || validated.kind == AuthFailureKind.SESSION_EXPIRED) {
                    // A stale rejection must not wipe credentials saved by another auth generation.
                    sessionWriteGate.withLock {
                        if (authGeneration.get() == generation) sessionStore.clear()
                    }
                    AuthAppState.LoginRequired
                } else {
                    AuthAppState.Failure(validated, recoverableSession = session)
                }
            }
        }
    }

    suspend fun completeTotp(
        session: AuthSession,
        factorId: String,
        code: CharArray,
    ): AuthAppState {
        val generation = authGeneration.get()
        val challenge = authGateway.challengeTotp(session.accessToken, factorId)
        if (authGeneration.get() != generation) return AuthAppState.LoginRequired
        if (challenge is AuthResult.Failure) return AuthAppState.Failure(challenge, session)
        challenge as AuthResult.Success

        return when (
            val verification = authGateway.verifyTotp(
                accessToken = session.accessToken,
                factorId = factorId,
                challengeId = challenge.value.id,
                code = code,
            )
        ) {
            is AuthResult.Success -> {
                if (authGeneration.get() != generation) {
                    AuthAppState.LoginRequired
                } else if (verification.value.assuranceLevel != AssuranceLevel.AAL2) {
                    AuthAppState.Failure(
                        AuthResult.Failure(AuthFailureKind.MFA_REQUIRED),
                        recoverableSession = verification.value,
                    )
                } else {
                    persistAuthenticatedSession(verification.value, generation)
                }
            }
            is AuthResult.Failure -> if (authGeneration.get() == generation) {
                AuthAppState.Failure(verification, session)
            } else AuthAppState.LoginRequired
        }
    }

    suspend fun logout(session: AuthSession?): AuthAppState {
        // Invalidate in-flight auth before any suspension, then clear under the same
        // gate as credential saves. A late answer can neither restore nor clear the new state.
        authGeneration.incrementAndGet()
        sessionWriteGate.withLock { sessionStore.clear() }
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

    private suspend fun persistAuthenticatedSession(
        session: AuthSession,
        generation: Long,
    ): AuthAppState = sessionWriteGate.withLock {
        if (authGeneration.get() != generation) return@withLock AuthAppState.LoginRequired
        sessionStore.save(session)
        if (authGeneration.get() == generation) AuthAppState.Ready(session)
        else AuthAppState.LoginRequired
    }

    private suspend fun routeAuthenticatedSession(session: AuthSession, generation: Long): AuthAppState {
        if (authGeneration.get() != generation) return AuthAppState.LoginRequired
        if (session.assuranceLevel == AssuranceLevel.AAL2) {
            return persistAuthenticatedSession(session, generation)
        }

        return when (val factorsResult = authGateway.listFactors(session.accessToken)) {
            is AuthResult.Success -> {
                if (authGeneration.get() != generation) return AuthAppState.LoginRequired
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
            is AuthResult.Failure -> if (authGeneration.get() == generation) {
                AuthAppState.Failure(factorsResult, recoverableSession = session)
            } else AuthAppState.LoginRequired
        }
    }
}
