package app.myfinhub.android.core.data

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.network.ApiFailureKind
import app.myfinhub.android.core.network.ApiResult
import app.myfinhub.android.core.network.MyFinHubApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceRepositoryTest {
    private val session = AuthSession("token", "refresh", 999_999, "owner", AssuranceLevel.AAL2)
    private val document = CanonicalFinanceDocument(
        Json.parseToJsonElement(
            """{"updatedAt":"2026-08-22T00:00:00Z","seed":{},"state":{"events":[]}}""",
        ).jsonObject,
    )

    @Test
    fun revisionConflict_preservesEditedDocumentInsteadOfOverwriting() = runBlocking {
        val api = FakeFinanceApi(
            load = { ApiResult.Success(CanonicalFinanceEnvelope(document, "7", "saved")) },
            save = { ApiResult.Failure(ApiFailureKind.REVISION_CONFLICT) },
        )
        val repository = repository(api)
        repository.load(session)

        repository.save(session, document)

        val state = repository.state.value as FinanceSyncState.Conflict
        assertEquals("7", state.expectedRevision)
        assertEquals(document, state.localDocument)
    }

    @Test
    fun successfulWrite_advancesRevisionWithoutCreatingLocalDatabase() = runBlocking {
        val api = FakeFinanceApi(
            load = { ApiResult.Success(CanonicalFinanceEnvelope(document, "7", "saved")) },
            save = { ApiResult.Success(CanonicalWriteReceipt("8", "saved-again")) },
        )
        val repository = repository(api)
        repository.load(session)

        repository.save(session, document)

        val state = repository.state.value as FinanceSyncState.Ready
        assertEquals("8", state.envelope.revision)
        repository.clear()
        assertTrue(repository.state.value is FinanceSyncState.Empty)
    }

    @Test
    fun unexpectedApiException_isContainedAsRetryableServerFailure() = runBlocking {
        val api = FakeFinanceApi(
            load = { error("synthetic transport bug") },
            save = { ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE) },
        )
        val repository = repository(api)

        repository.load(session)

        val state = repository.state.value as FinanceSyncState.Error
        assertEquals(ApiFailureKind.SERVER, state.failure.kind)
        assertTrue(state.failure.retryable)
        assertEquals(2, api.loadCalls)
    }

    @Test
    fun transientReadFailure_retriesOnceAndRecovers() = runBlocking {
        var first = true
        val api = FakeFinanceApi(
            load = {
                if (first) {
                    first = false
                    ApiResult.Failure(ApiFailureKind.NETWORK, retryable = true)
                } else {
                    ApiResult.Success(CanonicalFinanceEnvelope(document, "7", "saved"))
                }
            },
            save = { ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE) },
        )
        val repository = repository(api)

        repository.load(session)

        assertTrue(repository.state.value is FinanceSyncState.Ready)
        assertEquals(2, api.loadCalls)
    }

    @Test
    fun writeTransportFailure_isNeverAutomaticallyRetried() = runBlocking {
        val api = FakeFinanceApi(
            load = { ApiResult.Success(CanonicalFinanceEnvelope(document, "7", "saved")) },
            save = { ApiResult.Failure(ApiFailureKind.NETWORK, retryable = true) },
        )
        val repository = repository(api)
        repository.load(session)

        repository.save(session, document)

        val state = repository.state.value as FinanceSyncState.Error
        assertEquals(ApiFailureKind.NETWORK, state.failure.kind)
        assertEquals(1, api.saveCalls)
    }

    @Test
    fun malformedCanonicalSuccess_isRejectedBeforeBecomingReady() = runBlocking {
        val malformed = CanonicalFinanceDocument(
            Json.parseToJsonElement(
                """{"seed":{},"state":{"events":[{"id":"dup","date":"2026-09-01","amount":1},{"id":"dup","date":"2026-09-02","amount":2}]}}""",
            ).jsonObject,
        )
        val api = FakeFinanceApi(
            load = { ApiResult.Success(CanonicalFinanceEnvelope(malformed, "7", "saved")) },
            save = { ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE) },
        )
        val repository = repository(api)

        repository.load(session)

        val state = repository.state.value as FinanceSyncState.Error
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, state.failure.kind)
    }

    private fun repository(api: FakeFinanceApi) = FinanceRepository(
        api = api,
        retryPolicy = FinanceRetryPolicy(maxReadAttempts = 2, readRetryDelayMillis = 0),
        retryDelay = {},
    )
}

private class FakeFinanceApi(
    private val load: suspend () -> ApiResult<CanonicalFinanceEnvelope>,
    private val save: suspend () -> ApiResult<CanonicalWriteReceipt>,
) : MyFinHubApi {
    var loadCalls: Int = 0
        private set
    var saveCalls: Int = 0
        private set

    override suspend fun loadFinanceData(session: AuthSession): ApiResult<CanonicalFinanceEnvelope> {
        loadCalls += 1
        return load()
    }

    override suspend fun saveMutableState(
        session: AuthSession,
        document: CanonicalFinanceDocument,
        expectedRevision: String,
    ): ApiResult<CanonicalWriteReceipt> {
        saveCalls += 1
        return save()
    }
}
