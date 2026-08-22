package app.myfinhub.android.core.network

/**
 * Android-facing boundary for the canonical MyFinHub API.
 *
 * Phase 1 intentionally uses only a synthetic implementation. Production URLs, bearer sessions and
 * FinanceData DTOs arrive only after the native-auth contract tracked by issue #4 is verified.
 */
interface MyFinHubApi {
    suspend fun loadBootstrapSummary(): ApiResult<BootstrapSummary>
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
        val code: String,
        val retryable: Boolean,
    ) : ApiResult<Nothing>
}

class SyntheticMyFinHubApi : MyFinHubApi {
    override suspend fun loadBootstrapSummary(): ApiResult<BootstrapSummary> = ApiResult.Success(
        BootstrapSummary(
            source = DataSource.SYNTHETIC,
            revision = null,
        ),
    )
}
