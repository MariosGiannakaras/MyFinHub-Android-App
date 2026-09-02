package app.myfinhub.android.core.network

import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.CanonicalFinanceEnvelope
import app.myfinhub.android.core.data.CanonicalWriteReceipt

interface MyFinHubApi {
    suspend fun loadFinanceData(session: AuthSession): ApiResult<CanonicalFinanceEnvelope>

    suspend fun saveMutableState(
        session: AuthSession,
        document: CanonicalFinanceDocument,
        expectedRevision: String,
    ): ApiResult<CanonicalWriteReceipt>

    suspend fun loadCardSecrets(
        session: AuthSession,
        cardId: String,
    ): ApiResult<CardSecrets> = ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    suspend fun saveCardSecrets(
        session: AuthSession,
        cardId: String,
        update: CardSecretUpdate,
    ): ApiResult<CardSecretWriteReceipt> = ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    suspend fun deleteCardSecrets(
        session: AuthSession,
        cardId: String,
    ): ApiResult<CardSecretDeleteReceipt> = ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)
}

/** Sensitive PAN/expiry response. Intentionally redacts values from toString(). */
class CardSecrets(
    val pan: String?,
    val expiry: String?,
) {
    override fun toString(): String = "CardSecrets(pan=<redacted>, expiry=<redacted>)"
}

/** Server-vault update. There is deliberately no CVV/CVC field in this API surface. */
class CardSecretUpdate(
    val pan: String? = null,
    val expiry: String? = null,
) {
    override fun toString(): String = "CardSecretUpdate(pan=<redacted>, expiry=<redacted>)"
}

data class CardSecretWriteReceipt(
    val saved: Boolean,
    val last4: String?,
)

data class CardSecretDeleteReceipt(
    val deleted: Boolean,
)

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(
        val kind: ApiFailureKind,
        val retryable: Boolean = false,
        val statusCode: Int? = null,
    ) : ApiResult<Nothing>
}

enum class ApiFailureKind {
    BUILD_NOT_CONFIGURED,
    AUTH_REQUIRED,
    MFA_REQUIRED,
    REVISION_CONFLICT,
    PRECONDITION_REQUIRED,
    INVALID_DATA,
    RATE_LIMITED,
    NETWORK,
    SERVER,
    MALFORMED_RESPONSE,
    UNSUPPORTED_IN_SYNTHETIC_MODE,
}

class SyntheticMyFinHubApi : MyFinHubApi {
    override suspend fun loadFinanceData(session: AuthSession): ApiResult<CanonicalFinanceEnvelope> =
        ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    override suspend fun saveMutableState(
        session: AuthSession,
        document: CanonicalFinanceDocument,
        expectedRevision: String,
    ): ApiResult<CanonicalWriteReceipt> = ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)
}
