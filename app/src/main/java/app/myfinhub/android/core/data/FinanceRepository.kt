package app.myfinhub.android.core.data

import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.network.ApiFailureKind
import app.myfinhub.android.core.network.ApiResult
import app.myfinhub.android.core.network.MyFinHubApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface FinanceSyncState {
    data object Empty : FinanceSyncState
    data object Loading : FinanceSyncState
    data class Ready(val envelope: CanonicalFinanceEnvelope) : FinanceSyncState
    data class Conflict(
        val localDocument: CanonicalFinanceDocument,
        val expectedRevision: String,
    ) : FinanceSyncState
    data class Error(
        val failure: ApiResult.Failure,
        val recoverableEnvelope: CanonicalFinanceEnvelope? = null,
    ) : FinanceSyncState
}

data class FinanceRetryPolicy(
    val maxReadAttempts: Int = 2,
    val readRetryDelayMillis: Long = 150,
) {
    init {
        require(maxReadAttempts >= 1)
        require(readRetryDelayMillis >= 0)
    }
}

/** In-memory canonical state holder. The server remains the source of truth. */
class FinanceRepository(
    private val api: MyFinHubApi,
    private val retryPolicy: FinanceRetryPolicy = FinanceRetryPolicy(),
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutableState = MutableStateFlow<FinanceSyncState>(FinanceSyncState.Empty)
    val state: StateFlow<FinanceSyncState> = mutableState.asStateFlow()

    suspend fun load(session: AuthSession) {
        mutableState.value = FinanceSyncState.Loading
        mutableState.value = when (val result = loadWithRetry(session)) {
            is ApiResult.Success -> {
                val integrityIssue = CanonicalDataIntegrity.validateEnvelope(result.value)
                if (integrityIssue == null) {
                    FinanceSyncState.Ready(result.value)
                } else {
                    FinanceSyncState.Error(
                        ApiResult.Failure(ApiFailureKind.MALFORMED_RESPONSE),
                    )
                }
            }
            is ApiResult.Failure -> FinanceSyncState.Error(result)
        }
    }

    suspend fun save(session: AuthSession, document: CanonicalFinanceDocument) {
        val current = mutableState.value as? FinanceSyncState.Ready ?: return
        val expectedRevision = current.envelope.revision
        if (CanonicalDataIntegrity.validateDocument(document) != null) {
            mutableState.value = FinanceSyncState.Error(
                failure = ApiResult.Failure(ApiFailureKind.INVALID_DATA),
                recoverableEnvelope = current.envelope,
            )
            return
        }

        // Writes deliberately execute once. Ambiguous transport failures are reconciled by reloading
        // the server revision before replaying the stable mutation intent at the ViewModel boundary.
        when (val result = safeApiCall { api.saveMutableState(session, document, expectedRevision) }) {
            is ApiResult.Success -> {
                val envelope = CanonicalFinanceEnvelope(
                    document = document,
                    revision = result.value.revision,
                    lastSavedAt = result.value.lastSavedAt,
                )
                mutableState.value = if (CanonicalDataIntegrity.validateEnvelope(envelope) == null) {
                    FinanceSyncState.Ready(envelope)
                } else {
                    FinanceSyncState.Error(
                        failure = ApiResult.Failure(ApiFailureKind.MALFORMED_RESPONSE),
                        recoverableEnvelope = current.envelope,
                    )
                }
            }
            is ApiResult.Failure -> {
                mutableState.value = if (result.kind == ApiFailureKind.REVISION_CONFLICT) {
                    FinanceSyncState.Conflict(
                        localDocument = document,
                        expectedRevision = expectedRevision,
                    )
                } else {
                    FinanceSyncState.Error(result, recoverableEnvelope = current.envelope)
                }
            }
        }
    }

    fun clear() {
        mutableState.value = FinanceSyncState.Empty
    }

    private suspend fun loadWithRetry(session: AuthSession): ApiResult<CanonicalFinanceEnvelope> {
        var attempt = 1
        while (true) {
            val result = safeApiCall { api.loadFinanceData(session) }
            if (result !is ApiResult.Failure || !result.isSafeReadRetry() || attempt >= retryPolicy.maxReadAttempts) {
                return result
            }
            if (retryPolicy.readRetryDelayMillis > 0) retryDelay(retryPolicy.readRetryDelayMillis)
            attempt += 1
        }
    }

    private fun ApiResult.Failure.isSafeReadRetry(): Boolean = retryable &&
        (kind == ApiFailureKind.NETWORK || kind == ApiFailureKind.SERVER)

    private suspend fun <T> safeApiCall(block: suspend () -> ApiResult<T>): ApiResult<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ApiResult.Failure(ApiFailureKind.SERVER, retryable = true)
    }
}
