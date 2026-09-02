package app.myfinhub.android.core.auth

import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.security.SessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionCoordinatorTest {
    private val configured = AppConfiguration(
        myFinHubApiBaseUrl = "https://api.example.test",
        supabaseUrl = "https://project.supabase.test",
        supabasePublishableKey = "synthetic-publishable-key",
    )

    @Test
    fun initialize_withStoredSession_returnsLockedWithoutServerBypass() = runBlocking {
        val store = InMemorySessionStore(aal2Session())
        val gateway = FakeAuthGateway()
        val coordinator = AuthSessionCoordinator(configured, gateway, store, nowEpochSeconds = { 1_000 })

        val state = coordinator.initialize()

        assertTrue(state is AuthAppState.Locked)
        assertEquals(0, gateway.validateCalls)
    }

    @Test
    fun localUnlock_validatesServerSession_beforeReady() = runBlocking {
        val session = aal2Session()
        val store = InMemorySessionStore(session)
        val gateway = FakeAuthGateway(validateResult = AuthResult.Success(Unit))
        val coordinator = AuthSessionCoordinator(configured, gateway, store, nowEpochSeconds = { 1_000 })

        val state = coordinator.afterLocalUnlock(session)

        assertTrue(state is AuthAppState.Ready)
        assertEquals(1, gateway.validateCalls)
    }

    @Test
    fun expiredSession_transientRefreshFailureKeepsRecoverableStoredSession() = runBlocking {
        val session = aal2Session(expiresAt = 1_010)
        val store = InMemorySessionStore(session)
        val gateway = FakeAuthGateway(
            refreshResult = AuthResult.Failure(AuthFailureKind.NETWORK, retryable = true),
        )
        val coordinator = AuthSessionCoordinator(configured, gateway, store, nowEpochSeconds = { 1_000 })

        val state = coordinator.afterLocalUnlock(session)

        assertTrue(state is AuthAppState.Failure)
        state as AuthAppState.Failure
        assertSame(session, state.recoverableSession)
        assertEquals(session, store.value)
    }

    @Test
    fun revokedSession_withRejectedRefresh_returnsLoginAndClearsStore() = runBlocking {
        val session = aal2Session()
        val store = InMemorySessionStore(session)
        val gateway = FakeAuthGateway(
            validateResult = AuthResult.Failure(AuthFailureKind.UNAUTHORIZED),
            refreshResult = AuthResult.Failure(AuthFailureKind.SESSION_EXPIRED),
        )
        val coordinator = AuthSessionCoordinator(configured, gateway, store, nowEpochSeconds = { 1_000 })

        val state = coordinator.afterLocalUnlock(session)

        assertEquals(AuthAppState.LoginRequired, state)
        assertNull(store.value)
    }

    @Test
    fun aal1PasswordSession_requiresVerifiedTotpInsteadOfBecomingReady() = runBlocking {
        val session = aal1Session()
        val factor = AuthFactor("factor-1", "totp", "verified", "Authenticator")
        val store = InMemorySessionStore()
        val gateway = FakeAuthGateway(
            signInResult = AuthResult.Success(session),
            factorsResult = AuthResult.Success(listOf(factor)),
        )
        val coordinator = AuthSessionCoordinator(configured, gateway, store)

        val state = coordinator.signIn("owner@example.test", "synthetic-password".toCharArray())

        assertTrue(state is AuthAppState.MfaRequired)
        assertNull(store.value)
    }

    @Test
    fun successfulTotpAal2Session_isPersistedAndReady() = runBlocking {
        val aal1 = aal1Session()
        val aal2 = aal2Session()
        val store = InMemorySessionStore()
        val gateway = FakeAuthGateway(
            challengeResult = AuthResult.Success(AuthChallenge("challenge-1")),
            verifyResult = AuthResult.Success(aal2),
        )
        val coordinator = AuthSessionCoordinator(configured, gateway, store)

        val state = coordinator.completeTotp(
            session = aal1,
            factorId = "factor-1",
            code = "123456".toCharArray(),
        )

        assertTrue(state is AuthAppState.Ready)
        assertEquals(aal2, store.value)
    }

    @Test
    fun logout_attemptsServerRevokeAndAlwaysClearsEncryptedSessionBoundary() = runBlocking {
        val session = aal2Session()
        val store = InMemorySessionStore(session)
        val gateway = FakeAuthGateway()
        val coordinator = AuthSessionCoordinator(configured, gateway, store)

        val state = coordinator.logout(session)

        assertEquals(AuthAppState.LoginRequired, state)
        assertNull(store.value)
        assertEquals(1, gateway.signOutCalls)
    }

    @Test
    fun logout_unexpectedRemoteExceptionStillClearsLocalSession() = runBlocking {
        val session = aal2Session()
        val store = InMemorySessionStore(session)
        val gateway = FakeAuthGateway(throwOnSignOut = true)
        val coordinator = AuthSessionCoordinator(configured, gateway, store)

        runCatching { coordinator.logout(session) }

        assertNull(store.value)
        assertEquals(1, gateway.signOutCalls)
    }

    private fun aal1Session() = AuthSession(
        accessToken = "synthetic-aal1-token",
        refreshToken = "synthetic-refresh",
        expiresAtEpochSeconds = 10_000,
        userId = "owner",
        assuranceLevel = AssuranceLevel.AAL1,
    )

    private fun aal2Session(expiresAt: Long = 10_000) = AuthSession(
        accessToken = "synthetic-aal2-token",
        refreshToken = "synthetic-refresh",
        expiresAtEpochSeconds = expiresAt,
        userId = "owner",
        assuranceLevel = AssuranceLevel.AAL2,
    )
}

private class InMemorySessionStore(
    var value: AuthSession? = null,
) : SessionStore {
    override suspend fun load(): AuthSession? = value
    override suspend fun save(session: AuthSession) { value = session }
    override suspend fun clear() { value = null }
}

private class FakeAuthGateway(
    private val signInResult: AuthResult<AuthSession> = AuthResult.Failure(AuthFailureKind.INVALID_CREDENTIALS),
    private val refreshResult: AuthResult<AuthSession> = AuthResult.Failure(AuthFailureKind.SESSION_EXPIRED),
    private val validateResult: AuthResult<Unit> = AuthResult.Success(Unit),
    private val factorsResult: AuthResult<List<AuthFactor>> = AuthResult.Success(emptyList()),
    private val challengeResult: AuthResult<AuthChallenge> = AuthResult.Failure(AuthFailureKind.INVALID_MFA_CODE),
    private val verifyResult: AuthResult<AuthSession> = AuthResult.Failure(AuthFailureKind.INVALID_MFA_CODE),
    private val throwOnSignOut: Boolean = false,
) : AuthGateway {
    var validateCalls: Int = 0
    var signOutCalls: Int = 0

    override suspend fun signInWithPassword(email: String, password: CharArray) = signInResult
    override suspend fun refreshSession(refreshToken: String) = refreshResult
    override suspend fun validateSession(accessToken: String): AuthResult<Unit> {
        validateCalls += 1
        return validateResult
    }
    override suspend fun listFactors(accessToken: String) = factorsResult
    override suspend fun challengeTotp(accessToken: String, factorId: String) = challengeResult
    override suspend fun verifyTotp(
        accessToken: String,
        factorId: String,
        challengeId: String,
        code: CharArray,
    ) = verifyResult
    override suspend fun signOut(accessToken: String): AuthResult<Unit> {
        signOutCalls += 1
        if (throwOnSignOut) error("synthetic remote revoke failure")
        return AuthResult.Success(Unit)
    }
}
