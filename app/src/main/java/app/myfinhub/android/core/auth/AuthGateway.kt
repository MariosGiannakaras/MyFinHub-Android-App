package app.myfinhub.android.core.auth

interface AuthGateway {
    suspend fun signInWithPassword(email: String, password: CharArray): AuthResult<AuthSession>
    suspend fun refreshSession(refreshToken: String): AuthResult<AuthSession>
    suspend fun listFactors(accessToken: String): AuthResult<List<AuthFactor>>
    suspend fun challengeTotp(accessToken: String, factorId: String): AuthResult<AuthChallenge>
    suspend fun verifyTotp(
        accessToken: String,
        factorId: String,
        challengeId: String,
        code: CharArray,
    ): AuthResult<AuthSession>
    suspend fun signOut(accessToken: String): AuthResult<Unit>
}
