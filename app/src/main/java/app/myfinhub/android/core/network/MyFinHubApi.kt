package app.myfinhub.android.core.network

import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.CanonicalFinanceEnvelope
import app.myfinhub.android.core.data.CanonicalWriteReceipt

interface MyFinHubApi {
    suspend fun loadBootstrapSummary(): ApiResult<BootstrapSummary>
    suspend fun loadFinanceData(session: AuthSession): ApiResult<CanonicalFinanceEnvelope>
    suspend fun saveMutableState(
        session: AuthSession,
        document: CanonicalFinanceDocument,
        expectedRevision: String,
    ): ApiResult<CanonicalWriteReceipt>
}

data class BootstrapSummary(
    val source: DataSource,
    val revision: Long?,
)

enum class DataSource {
    SYNTHETIC,
    CANONICAL_API,
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(
        val kind: ApiFailureKind,
        val retryable: Boolean = false,
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
    override suspend fun loadBootstrapSummary(): ApiResult<BootstrapSummary> = ApiResult.Success(
        BootstrapSummary(
            source = DataSource.SYNTHETIC,
            revision = null,
        ),
    )

    override suspend fun loadFinanceData(session: AuthSession): ApiResult<CanonicalFinanceEnvelope> =
        ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    override suspend fun saveMutableState(
        session: AuthSession,
        document: CanonicalFinanceDocument,
        expectedRevision: String,
    ): ApiResult<CanonicalWriteReceipt> = ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)
}
