package app.myfinhub.android.core.auth

import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.security.SessionStore

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
    suspend fun initialize(): AuthAppState {
        if (!configuration.isConfigured) return AuthAppState.Unconfigured(configuration.missingFields)
        return sessionStore.load()?.let(AuthAppState::Locked) ?: AuthAppState.LoginRequired
    }

    suspend fun signIn(email: String, password: CharArray): AuthAppState = when (
        val result = authGateway.signInWithPassword(email, password)
    ) {
        is AuthResult.Success -> routeAuthenticatedSession(result.value)
        is AuthResult.Failure -> AuthAppState.Failure(result)
    }

    suspend fun afterLocalUnlock(session: AuthSession): AuthAppState {
        val validated = validateOrRefresh(session)
        return when (validated) {
            is AuthResult.Success -> routeAuthenticatedSession(validated.value)
            is AuthResult.Failure -> {
                if (validated.kind == AuthFailureKind.UNAUTHORIZED || validated.kind == AuthFailureKind.SESSION_EXPIRED) {
                    sessionStore.clear()
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
        val challenge = authGateway.challengeTotp(session.accessToken, factorId)
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
                if (verification.value.assuranceLevel != AssuranceLevel.AAL2) {
                    AuthAppState.Failure(
                        AuthResult.Failure(AuthFailureKind.MFA_REQUIRED),
                        recoverableSession = verification.value,
                    )
                } else {
                    sessionStore.save(verification.value)
                    AuthAppState.Ready(verification.value)
                }
            }
            is AuthResult.Failure -> AuthAppState.Failure(verification, session)
        }
    }

    suspend fun logout(session: AuthSession?): AuthAppState {
        session?.let { authGateway.signOut(it.accessToken) }
        sessionStore.clear()
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

    private suspend fun routeAuthenticatedSession(session: AuthSession): AuthAppState {
        if (session.assuranceLevel == AssuranceLevel.AAL2) {
            sessionStore.save(session)
            return AuthAppState.Ready(session)
        }

        return when (val factorsResult = authGateway.listFactors(session.accessToken)) {
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
