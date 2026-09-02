package app.myfinhub.android.core.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val userId: String,
    val assuranceLevel: AssuranceLevel,
) {
    fun expiresWithin(nowEpochSeconds: Long, windowSeconds: Long = 60): Boolean =
        expiresAtEpochSeconds <= nowEpochSeconds + windowSeconds
}

@Serializable
enum class AssuranceLevel {
    AAL1,
    AAL2,
    UNKNOWN,
}

@Serializable
data class AuthFactor(
    val id: String,
    val type: String,
    val status: String,
    val friendlyName: String? = null,
) {
    val isVerifiedTotp: Boolean
        get() = type == "totp" && status == "verified"
}

data class AuthChallenge(
    val id: String,
)

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>

    data class Failure(
        val kind: AuthFailureKind,
        val message: String? = null,
        val retryable: Boolean = false,
        val statusCode: Int? = null,
    ) : AuthResult<Nothing>
}

enum class AuthFailureKind {
    BUILD_NOT_CONFIGURED,
    INVALID_CREDENTIALS,
    MFA_REQUIRED,
    INVALID_MFA_CODE,
    SESSION_EXPIRED,
    UNAUTHORIZED,
    RATE_LIMITED,
    NETWORK,
    SERVER,
    MALFORMED_RESPONSE,
}
