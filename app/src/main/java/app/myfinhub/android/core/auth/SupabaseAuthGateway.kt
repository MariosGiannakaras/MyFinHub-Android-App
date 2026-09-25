package app.myfinhub.android.core.auth

import app.myfinhub.android.core.config.AppConfiguration
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SupabaseAuthGateway(
    private val configuration: AppConfiguration,
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val jwtClaimsParser: JwtClaimsParser = JwtClaimsParser(),
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : AuthGateway {

    override suspend fun signInWithPassword(email: String, password: CharArray): AuthResult<AuthSession> {
        if (!configuration.isConfigured) return notConfigured()
        if (email.isBlank() || password.isEmpty()) return AuthResult.Failure(AuthFailureKind.INVALID_CREDENTIALS)
        val body = json.encodeToString(
            PasswordGrantRequest(email = email.trim(), password = password.concatToString()),
        )
        return tokenRequest("password", body, AuthFailureKind.INVALID_CREDENTIALS)
    }

    override suspend fun refreshSession(refreshToken: String): AuthResult<AuthSession> {
        if (!configuration.isConfigured) return notConfigured()
        if (refreshToken.isBlank()) return AuthResult.Failure(AuthFailureKind.SESSION_EXPIRED)
        val body = json.encodeToString(RefreshGrantRequest(refreshToken))
        return tokenRequest("refresh_token", body, AuthFailureKind.SESSION_EXPIRED)
    }

    override suspend fun validateSession(accessToken: String): AuthResult<Unit> {
        if (accessToken.isBlank()) return AuthResult.Failure(AuthFailureKind.UNAUTHORIZED)
        return request(
            request = authorizedRequest("${configuration.supabaseUrl}/auth/v1/user", accessToken).get().build(),
            parse = { AuthResult.Success(Unit) },
        )
    }

    override suspend fun listFactors(accessToken: String): AuthResult<List<AuthFactor>> {
        if (accessToken.isBlank()) return AuthResult.Failure(AuthFailureKind.UNAUTHORIZED)
        return request(
            // Supabase's current client implementation derives MFA factors from the authenticated
            // user response. GET /auth/v1/factors is not a list endpoint and returns HTTP 405.
            request = authorizedRequest("${configuration.supabaseUrl}/auth/v1/user", accessToken).get().build(),
            parse = { body ->
                val response = json.decodeFromString<UserFactorsResponse>(body)
                AuthResult.Success(response.factors.map { it.toDomain() })
            },
        )
    }

    override suspend fun challengeTotp(
        accessToken: String,
        factorId: String,
    ): AuthResult<AuthChallenge> {
        if (accessToken.isBlank()) return AuthResult.Failure(AuthFailureKind.UNAUTHORIZED)
        if (factorId.isBlank()) return AuthResult.Failure(AuthFailureKind.MFA_REQUIRED)
        return request(
            request = authorizedRequest(
                "${configuration.supabaseUrl}/auth/v1/factors/$factorId/challenge",
                accessToken,
            ).post(EMPTY_JSON.toRequestBody(JSON_MEDIA_TYPE)).build(),
            parse = { body -> AuthResult.Success(AuthChallenge(json.decodeFromString<ChallengeResponse>(body).id)) },
        )
    }

    override suspend fun verifyTotp(
        accessToken: String,
        factorId: String,
        challengeId: String,
        code: CharArray,
    ): AuthResult<AuthSession> {
        if (accessToken.isBlank()) return AuthResult.Failure(AuthFailureKind.UNAUTHORIZED)
        if (factorId.isBlank() || challengeId.isBlank() || code.isEmpty()) {
            return AuthResult.Failure(AuthFailureKind.INVALID_MFA_CODE)
        }
        val requestBody = json.encodeToString(
            VerifyChallengeRequest(challengeId = challengeId, code = code.concatToString()),
        )
        return request(
            request = authorizedRequest(
                "${configuration.supabaseUrl}/auth/v1/factors/$factorId/verify",
                accessToken,
            ).post(requestBody.toRequestBody(JSON_MEDIA_TYPE)).build(),
            parse = { body -> tokenResponse(body) },
            invalidInputKind = AuthFailureKind.INVALID_MFA_CODE,
        )
    }

    override suspend fun signOut(accessToken: String): AuthResult<Unit> {
        if (accessToken.isBlank()) return AuthResult.Success(Unit)
        return request(
            request = authorizedRequest("${configuration.supabaseUrl}/auth/v1/logout", accessToken)
                .post(EMPTY_JSON.toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            parse = { AuthResult.Success(Unit) },
        )
    }

    private suspend fun tokenRequest(
        grantType: String,
        body: String,
        invalidInputKind: AuthFailureKind,
    ): AuthResult<AuthSession> = request(
        request = baseRequest("${configuration.supabaseUrl}/auth/v1/token?grant_type=$grantType")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build(),
        parse = { responseBody -> tokenResponse(responseBody) },
        invalidInputKind = invalidInputKind,
    )

    private fun tokenResponse(body: String): AuthResult<AuthSession> = runCatching {
        val response = json.decodeFromString<TokenResponse>(body)
        val expiresAt = response.expiresAt
            ?: response.expiresIn?.let { nowEpochSeconds() + it }
            ?: error("Missing session expiry")
        if (response.accessToken.isBlank() || response.refreshToken.isBlank() || response.user.id.isBlank()) {
            error("Incomplete auth session")
        }
        AuthResult.Success(
            AuthSession(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresAtEpochSeconds = expiresAt,
                userId = response.user.id,
                assuranceLevel = jwtClaimsParser.assuranceLevel(response.accessToken),
            ),
        )
    }.getOrElse {
        AuthResult.Failure(AuthFailureKind.MALFORMED_RESPONSE)
    }

    private suspend fun <T> request(
        request: Request,
        invalidInputKind: AuthFailureKind = AuthFailureKind.UNAUTHORIZED,
        parse: (String) -> AuthResult<T>,
    ): AuthResult<T> {
        if (!configuration.isConfigured) return notConfigured()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val status = response.code
                    val body = response.body.string()
                    when {
                        response.isSuccessful -> safeParse(status, body, parse)
                        status == 400 || status == 422 -> AuthResult.Failure(invalidInputKind, statusCode = status)
                        status == 401 || status == 403 -> AuthResult.Failure(AuthFailureKind.UNAUTHORIZED, statusCode = status)
                        status == 429 -> AuthResult.Failure(AuthFailureKind.RATE_LIMITED, retryable = true, statusCode = status)
                        status in 500..599 -> AuthResult.Failure(AuthFailureKind.SERVER, retryable = true, statusCode = status)
                        else -> AuthResult.Failure(AuthFailureKind.SERVER, statusCode = status)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                AuthResult.Failure(AuthFailureKind.NETWORK, retryable = true)
            } catch (_: Exception) {
                AuthResult.Failure(AuthFailureKind.SERVER, retryable = true)
            }
        }
    }

    private fun <T> safeParse(
        status: Int,
        body: String,
        parse: (String) -> AuthResult<T>,
    ): AuthResult<T> = try {
        when (val parsed = parse(body)) {
            is AuthResult.Success -> parsed
            is AuthResult.Failure -> if (parsed.statusCode == null) parsed.copy(statusCode = status) else parsed
        }
    } catch (_: Exception) {
        AuthResult.Failure(AuthFailureKind.MALFORMED_RESPONSE, statusCode = status)
    }

    private fun baseRequest(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("apikey", configuration.supabasePublishableKey)
        .header("Accept", "application/json")

    private fun authorizedRequest(url: String, accessToken: String): Request.Builder = baseRequest(url)
        .header("Authorization", "Bearer $accessToken")

    private fun notConfigured(): AuthResult.Failure = AuthResult.Failure(
        kind = AuthFailureKind.BUILD_NOT_CONFIGURED,
        message = configuration.missingFields.joinToString(),
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val EMPTY_JSON = "{}"
    }
}

@Serializable
private data class PasswordGrantRequest(val email: String, val password: String)

@Serializable
private data class RefreshGrantRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
private data class VerifyChallengeRequest(
    @SerialName("challenge_id") val challengeId: String,
    val code: String,
)

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val user: TokenUser,
)

@Serializable
private data class TokenUser(val id: String)

@Serializable
private data class UserFactorsResponse(val factors: List<FactorResponse> = emptyList())

@Serializable
private data class FactorResponse(
    val id: String,
    @SerialName("factor_type") val factorType: String,
    val status: String,
    @SerialName("friendly_name") val friendlyName: String? = null,
) {
    fun toDomain(): AuthFactor = AuthFactor(
        id = id,
        type = factorType,
        status = status,
        friendlyName = friendlyName,
    )
}

@Serializable
private data class ChallengeResponse(val id: String)
