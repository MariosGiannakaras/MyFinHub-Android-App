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

    /**
     * Guards the in-memory finance envelope against stale asynchronous responses.
     * Logout/owner change may call clear() while a read or write is suspended.
     * Only the latest operation started in this repository may publish a result.
     * Never store bearer credentials or finance records in this synchronization gate.
     */
    private val stateGate = Any()
    private var stateEpoch: Long = 0L
    private var activeWriteEpoch: Long? = null

    suspend fun load(session: AuthSession) {
        val epoch = synchronized(stateGate) {
            stateEpoch += 1
            mutableState.value = FinanceSyncState.Loading
            stateEpoch
        }
        val nextState = when (val result = loadWithRetry(session)) {
            is ApiResult.Success -> {
                if (CanonicalDataIntegrity.validateEnvelope(result.value) == null) {
                    FinanceSyncState.Ready(result.value)
                } else {
                    FinanceSyncState.Error(ApiResult.Failure(ApiFailureKind.MALFORMED_RESPONSE))
                }
            }
            is ApiResult.Failure -> FinanceSyncState.Error(result)
        }
        synchronized(stateGate) {
            // A newer load, save, clear or owner transition takes precedence.
            if (stateEpoch == epoch) mutableState.value = nextState
        }
    }

    suspend fun save(session: AuthSession, document: CanonicalFinanceDocument) {
        // No two write requests may reuse the same optimistic server revision.
        // An overlapping save returns without sending, rather than blind replay.
        val started = synchronized(stateGate) {
            val ready = mutableState.value as? FinanceSyncState.Ready
            if (ready == null || activeWriteEpoch != null) null else {
                stateEpoch += 1
                activeWriteEpoch = stateEpoch
                stateEpoch to ready.envelope
            }
        } ?: return
        val (epoch, baseline) = started
        try {
            val nextState: FinanceSyncState =
                if (CanonicalDataIntegrity.validateDocument(document) != null) {
                    FinanceSyncState.Error(
                        failure = ApiResult.Failure(ApiFailureKind.INVALID_DATA),
                        recoverableEnvelope = baseline,
                    )
                } else {
                    // Exactly one transport attempt. Ambiguous responses must be reconciled by
                    // loading the server; never retry a possibly accepted money write.
                    when (val result = safeApiCall {
                        api.saveMutableState(session, document, baseline.revision)
                    }) {
                        is ApiResult.Success -> {
                            val envelope = CanonicalFinanceEnvelope(
                                document = document,
                                revision = result.value.revision,
                                lastSavedAt = result.value.lastSavedAt,
                            )
                            if (CanonicalDataIntegrity.validateEnvelope(envelope) == null) {
                                FinanceSyncState.Ready(envelope)
                            } else {
                                FinanceSyncState.Error(
                                    failure = ApiResult.Failure(ApiFailureKind.MALFORMED_RESPONSE),
                                    recoverableEnvelope = baseline,
                                )
                            }
                        }
                        is ApiResult.Failure -> {
                            if (result.kind == ApiFailureKind.REVISION_CONFLICT) {
                                FinanceSyncState.Conflict(
                                    localDocument = document,
                                    expectedRevision = baseline.revision,
                                )
                            } else {
                                FinanceSyncState.Error(result, recoverableEnvelope = baseline)
                            }
                        }
                    }
                }
            synchronized(stateGate) {
                // Ignore an old account's or old generation's response after clear/reload.
                if (stateEpoch == epoch && activeWriteEpoch == epoch) {
                    mutableState.value = nextState
                }
            }
        } finally {
            synchronized(stateGate) {
                if (activeWriteEpoch == epoch) activeWriteEpoch = null
            }
        }
    }

    fun clear() {
        synchronized(stateGate) {
            stateEpoch += 1
            activeWriteEpoch = null
            mutableState.value = FinanceSyncState.Empty
        }
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
