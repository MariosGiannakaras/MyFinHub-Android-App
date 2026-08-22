package app.myfinhub.android.core.auth

import app.myfinhub.android.core.config.AppConfiguration
import java.io.IOException
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
        val body = json.encodeToString(
            PasswordGrantRequest(
                email = email.trim(),
                password = password.concatToString(),
            ),
        )
        return tokenRequest("password", body, AuthFailureKind.INVALID_CREDENTIALS)
    }

    override suspend fun refreshSession(refreshToken: String): AuthResult<AuthSession> {
        if (!configuration.isConfigured) return notConfigured()
        val body = json.encodeToString(RefreshGrantRequest(refreshToken))
        return tokenRequest("refresh_token", body, AuthFailureKind.SESSION_EXPIRED)
    }

    override suspend fun listFactors(accessToken: String): AuthResult<List<AuthFactor>> = request(
        request = authorizedRequest("${configuration.supabaseUrl}/auth/v1/factors", accessToken).get().build(),
        parse = { body ->
            val response = json.decodeFromString<FactorListResponse>(body)
            AuthResult.Success(response.all.map { it.toDomain() })
        },
    )

    override suspend fun challengeTotp(
        accessToken: String,
        factorId: String,
    ): AuthResult<AuthChallenge> = request(
        request = authorizedRequest(
            "${configuration.supabaseUrl}/auth/v1/factors/$factorId/challenge",
            accessToken,
        ).post(EMPTY_JSON.toRequestBody(JSON_MEDIA_TYPE)).build(),
        parse = { body -> AuthResult.Success(AuthChallenge(json.decodeFromString<ChallengeResponse>(body).id)) },
    )

    override suspend fun verifyTotp(
        accessToken: String,
        factorId: String,
        challengeId: String,
        code: CharArray,
    ): AuthResult<AuthSession> {
        val requestBody = json.encodeToString(
            VerifyChallengeRequest(
                challengeId = challengeId,
                code = code.concatToString(),
            ),
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

    override suspend fun signOut(accessToken: String): AuthResult<Unit> = request(
        request = authorizedRequest("${configuration.supabaseUrl}/auth/v1/logout", accessToken)
            .post(EMPTY_JSON.toRequestBody(JSON_MEDIA_TYPE))
            .build(),
        parse = { AuthResult.Success(Unit) },
    )

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
                    val body = response.body.string()
                    when {
                        response.isSuccessful -> parse(body)
                        response.code == 400 || response.code == 422 ->
                            AuthResult.Failure(invalidInputKind)
                        response.code == 401 || response.code == 403 ->
                            AuthResult.Failure(AuthFailureKind.UNAUTHORIZED)
                        response.code == 429 ->
                            AuthResult.Failure(AuthFailureKind.RATE_LIMITED, retryable = true)
                        response.code in 500..599 ->
                            AuthResult.Failure(AuthFailureKind.SERVER, retryable = true)
                        else -> AuthResult.Failure(AuthFailureKind.SERVER)
                    }
                }
            } catch (_: IOException) {
                AuthResult.Failure(AuthFailureKind.NETWORK, retryable = true)
            }
        }
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
private data class PasswordGrantRequest(
    val email: String,
    val password: String,
)

@Serializable
private data class RefreshGrantRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

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
private data class FactorListResponse(
    val all: List<FactorResponse> = emptyList(),
)

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
