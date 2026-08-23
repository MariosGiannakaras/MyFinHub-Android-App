package app.myfinhub.android.core.data

import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.network.ApiFailureKind
import app.myfinhub.android.core.network.ApiResult
import app.myfinhub.android.core.network.MyFinHubApi
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

/** In-memory canonical state holder. The server remains the source of truth. */
class FinanceRepository(
    private val api: MyFinHubApi,
) {
    private val mutableState = MutableStateFlow<FinanceSyncState>(FinanceSyncState.Empty)
    val state: StateFlow<FinanceSyncState> = mutableState.asStateFlow()

    suspend fun load(session: AuthSession) {
        mutableState.value = FinanceSyncState.Loading
        mutableState.value = when (val result = api.loadFinanceData(session)) {
            is ApiResult.Success -> FinanceSyncState.Ready(result.value)
            is ApiResult.Failure -> FinanceSyncState.Error(result)
        }
    }

    suspend fun save(session: AuthSession, document: CanonicalFinanceDocument) {
        val current = mutableState.value as? FinanceSyncState.Ready ?: return
        val expectedRevision = current.envelope.revision

        when (val result = api.saveMutableState(session, document, expectedRevision)) {
            is ApiResult.Success -> {
                mutableState.value = FinanceSyncState.Ready(
                    CanonicalFinanceEnvelope(
                        document = document,
                        revision = result.value.revision,
                        lastSavedAt = result.value.lastSavedAt,
                    ),
                )
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
}
