package app.myfinhub.android.core.data

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.network.ApiFailureKind
import app.myfinhub.android.core.network.ApiResult
import app.myfinhub.android.core.network.BootstrapSummary
import app.myfinhub.android.core.network.DataSource
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
            load = ApiResult.Success(CanonicalFinanceEnvelope(document, "7", "saved")),
            save = ApiResult.Failure(ApiFailureKind.REVISION_CONFLICT),
        )
        val repository = FinanceRepository(api)
        repository.load(session)

        repository.save(session, document)

        val state = repository.state.value as FinanceSyncState.Conflict
        assertEquals("7", state.expectedRevision)
        assertEquals(document, state.localDocument)
    }

    @Test
    fun successfulWrite_advancesRevisionWithoutCreatingLocalDatabase() = runBlocking {
        val api = FakeFinanceApi(
            load = ApiResult.Success(CanonicalFinanceEnvelope(document, "7", "saved")),
            save = ApiResult.Success(CanonicalWriteReceipt("8", "saved-again")),
        )
        val repository = FinanceRepository(api)
        repository.load(session)

        repository.save(session, document)

        val state = repository.state.value as FinanceSyncState.Ready
        assertEquals("8", state.envelope.revision)
        repository.clear()
        assertTrue(repository.state.value is FinanceSyncState.Empty)
    }
}

private class FakeFinanceApi(
    private val load: ApiResult<CanonicalFinanceEnvelope>,
    private val save: ApiResult<CanonicalWriteReceipt>,
) : MyFinHubApi {
    override suspend fun loadBootstrapSummary() = ApiResult.Success(BootstrapSummary(DataSource.SYNTHETIC, null))
    override suspend fun loadFinanceData(session: AuthSession) = load
    override suspend fun saveMutableState(
        session: AuthSession,
        document: CanonicalFinanceDocument,
        expectedRevision: String,
    ) = save
}
