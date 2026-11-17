package app.myfinhub.android.core.network

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpMyFinHubApiTest {
    private val config = AppConfiguration(
        myFinHubApiBaseUrl = "https://api.example.test",
        supabaseUrl = "https://project.supabase.test",
        supabasePublishableKey = "synthetic-key",
    )
    private val session = AuthSession(
        accessToken = "synthetic-bearer",
        refreshToken = "synthetic-refresh",
        expiresAtEpochSeconds = 99_999,
        userId = "owner",
        assuranceLevel = AssuranceLevel.AAL2,
    )

    @Test
    fun loadFinanceData_sendsBearerAndPreservesUnknownCanonicalFields() = runBlocking {
        val interceptor = RecordingInterceptor(
            code = 200,
            body = """{
                "data": {
                    "app": "RheomIQ",
                    "schemaVersion": 3,
                    "updatedAt": "2026-08-22T00:00:00.000Z",
                    "futureField": {"kept": true},
                    "seed": {"accounts": [], "snapshots": []},
                    "state": {"events": [], "futureState": 42}
                },
                "revision": "17",
                "lastSavedAt": "2026-08-22T00:00:01.000Z"
            }""".trimIndent(),
        )
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())

        val result = api.loadFinanceData(session) as ApiResult.Success

        assertEquals("17", result.value.revision)
        assertTrue(result.value.document.raw.containsKey("futureField"))
        assertTrue(result.value.document.state.containsKey("futureState"))
        assertEquals("Bearer synthetic-bearer", interceptor.request?.header("Authorization"))
        assertFalse(interceptor.request?.headers?.names()?.contains("Cookie") ?: true)
    }

    @Test
    fun saveMutableState_sendsDualPreconditionsAndOnlyMutableStateEnvelope() = runBlocking {
        val interceptor = SequencedRecordingInterceptor(
            listOf(
                SyntheticResponse(
                    200,
                    """{"available":true,"generation":"41","financeRevision":"17"}""",
                ),
                SyntheticResponse(
                    200,
                    """{"revision":"18","lastSavedAt":"2026-08-22T00:01:00.000Z"}""",
                ),
            ),
        )
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())
        val raw = Json.parseToJsonElement(
            """{
                "app":"RheomIQ",
                "schemaVersion":3,
                "updatedAt":"2026-08-22T00:00:30.000Z",
                "seed":{"accounts":[]},
                "state":{"events":[],"unknownMutable":{"preserve":true}}
            }""".trimIndent(),
        ).jsonObject

        val result = api.saveMutableState(session, CanonicalFinanceDocument(raw), "17") as ApiResult.Success

        assertEquals("18", result.value.revision)
        assertEquals(2, interceptor.requests.size)
        val historyRequest = interceptor.requests[0]
        val saveRequest = interceptor.requests[1]
        assertEquals("/api/history", historyRequest.url.encodedPath)
        assertEquals("GET", historyRequest.method)
        assertEquals("/api/data", saveRequest.url.encodedPath)
        assertEquals("PUT", saveRequest.method)
        assertEquals("17", saveRequest.header("If-Match"))
        assertEquals("41", saveRequest.header("x-rheomiq-history-generation"))
        assertEquals("Bearer synthetic-bearer", saveRequest.header("Authorization"))
        val sent = saveRequest.requestBodyJson()
        assertTrue(sent.containsKey("state"))
        assertTrue(sent.containsKey("updatedAt"))
        assertFalse(sent.containsKey("seed"))
        assertTrue(sent["state"]!!.jsonObject.containsKey("unknownMutable"))
    }

    @Test
    fun conflict_isExplicitAndNeverReportedAsSuccess() = runBlocking {
        val interceptor = RecordingInterceptor(409, """{"code":"REVISION_CONFLICT"}""")
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())
        val document = CanonicalFinanceDocument(
            Json.parseToJsonElement("""{"updatedAt":"2026-08-22T00:00:00Z","state":{},"seed":{}}""").jsonObject,
        )

        val result = api.saveMutableState(session, document, "7") as ApiResult.Failure

        assertEquals(ApiFailureKind.REVISION_CONFLICT, result.kind)
    }


    @Test
    fun cardSecretCleanup409_preservesSafeCodeAndDoesNotPretendRevisionConflict() = runBlocking {
        val requestId = "123e4567-e89b-42d3-a456-426614174000"
        val interceptor = SequencedRecordingInterceptor(
            listOf(
                SyntheticResponse(200, """{"available":true,"generation":"41","financeRevision":"7"}"""),
                SyntheticResponse(409,
                    """{"code":"CARD_CLEANUP_INTENT_REQUIRED","requestId":"$requestId","message":"private PAN CVV"}"""),
            ),
        )
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())
        val document = CanonicalFinanceDocument(
            Json.parseToJsonElement("""{"updatedAt":"2026-08-22T00:00:00Z","state":{},"seed":{}}""").jsonObject,
        )

        val result = api.saveMutableState(session, document, "7") as ApiResult.Failure

        assertEquals(ApiFailureKind.CARD_CLEANUP_REQUIRED, result.kind)
        assertEquals("CARD_CLEANUP_INTENT_REQUIRED", result.serverCode)
        assertEquals(requestId, result.requestId)
        assertEquals(409, result.statusCode)
        assertFalse(result.toString().contains("private PAN CVV"))
        assertEquals(2, interceptor.requests.size)
    }

    @Test
    fun revisionConflict409_keepsSafeCorrelationWithoutUntrustedMessage() = runBlocking {
        val requestId = "123e4567-e89b-42d3-a456-426614174000"
        val interceptor = RecordingInterceptor(409,
            """{"code":"REVISION_CONFLICT","requestId":"$requestId","message":"sensitive"}""")
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())

        val result = api.loadFinanceData(session) as ApiResult.Failure

        assertEquals(ApiFailureKind.REVISION_CONFLICT, result.kind)
        assertEquals("REVISION_CONFLICT", result.serverCode)
        assertEquals(requestId, result.requestId)
        assertFalse(result.toString().contains("sensitive"))
    }

    @Test
    fun unauthorizedResponse_failsClosedAsAuthRequired() = runBlocking {
        val interceptor = RecordingInterceptor(401, """{"error":"invalid bearer"}""")
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())

        val result = api.loadFinanceData(session) as ApiResult.Failure

        assertEquals(ApiFailureKind.AUTH_REQUIRED, result.kind)
        assertEquals("Bearer synthetic-bearer", interceptor.request?.header("Authorization"))
        assertFalse(interceptor.request?.headers?.names()?.contains("Cookie") ?: true)
    }

    @Test
    fun malformedSuccessfulEnvelope_failsClosed() = runBlocking {
        val interceptor = RecordingInterceptor(200, """{"data":{"state":{}},"lastSavedAt":"x"}""")
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())

        val result = api.loadFinanceData(session) as ApiResult.Failure

        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, result.kind)
    }

    @Test
    fun malformedSuccessfulWriteReceipt_failsClosed() = runBlocking {
        val interceptor = SequencedRecordingInterceptor(
            listOf(
                SyntheticResponse(
                    200,
                    """{"available":true,"generation":"12","financeRevision":"7"}""",
                ),
                SyntheticResponse(200, """{"lastSavedAt":"x"}"""),
            ),
        )
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())
        val document = CanonicalFinanceDocument(
            Json.parseToJsonElement("""{"updatedAt":"2026-08-22T00:00:00Z","state":{},"seed":{}}""").jsonObject,
        )

        val result = api.saveMutableState(session, document, "7") as ApiResult.Failure

        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, result.kind)
        assertEquals(2, interceptor.requests.size)
    }

    @Test
    fun aal1Session_failsBeforeNetwork() = runBlocking {
        val interceptor = RecordingInterceptor(200, "{}")
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())

        val result = api.loadFinanceData(session.copy(assuranceLevel = AssuranceLevel.AAL1)) as ApiResult.Failure

        assertEquals(ApiFailureKind.MFA_REQUIRED, result.kind)
        assertEquals(null, interceptor.request)
    }
    @Test
    fun forbidden403_isNotMisreportedAsMfa_andPreservesSafeCorrelation() = runBlocking {
        val requestId = "123e4567-e89b-42d3-a456-426614174000"
        val interceptor = RecordingInterceptor(403,
            """{"code":"FORBIDDEN","requestId":"$requestId","error":"sensitive server text"}""")
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())

        val result = api.loadFinanceData(session) as ApiResult.Failure

        assertEquals(ApiFailureKind.FORBIDDEN, result.kind)
        assertEquals("FORBIDDEN", result.serverCode)
        assertEquals(requestId, result.requestId)
        assertEquals(403, result.statusCode)
    }

    @Test
    fun explicitMfa403_requiresVerificationInsteadOfTreatingOwnerAsForbidden() = runBlocking {
        val interceptor = RecordingInterceptor(403, """{"code":"MFA_REQUIRED"}""")
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())

        val result = api.loadFinanceData(session) as ApiResult.Failure

        assertEquals(ApiFailureKind.MFA_REQUIRED, result.kind)
        assertEquals("MFA_REQUIRED", result.serverCode)
    }

    @Test
    fun ambiguous403_failsClosedAsPermissionDenial() = runBlocking {
        val interceptor = RecordingInterceptor(403, """{"code":"ATTACKER_CONTROLLED","requestId":"not-a-uuid"}""")
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())

        val result = api.loadFinanceData(session) as ApiResult.Failure

        assertEquals(ApiFailureKind.FORBIDDEN, result.kind)
        assertEquals(null, result.serverCode)
        assertEquals(null, result.requestId)
    }

    @Test
    fun invalidFinanceRead_preservesOnlyAllowlistedServerCodeAndUuid() = runBlocking {
        val requestId = "123e4567-e89b-42d3-a456-426614174000"
        val interceptor = RecordingInterceptor(400,
            """{"code":"INVALID_DATA","requestId":"$requestId","error":"card 4111111111111111 CVV 123"}""")
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())

        val result = api.loadFinanceData(session) as ApiResult.Failure

        assertEquals(ApiFailureKind.INVALID_DATA, result.kind)
        assertEquals("INVALID_DATA", result.serverCode)
        assertEquals(requestId, result.requestId)
        assertEquals(400, result.statusCode)
    }

}

private class RecordingInterceptor(
    private val code: Int,
    private val body: String,
) : Interceptor {
    var request: Request? = null
        private set

    override fun intercept(chain: Interceptor.Chain): Response {
        request = chain.request()
        return syntheticHttpResponse(chain.request(), code, body)
    }
}

private data class SyntheticResponse(
    val code: Int,
    val body: String,
)

private class SequencedRecordingInterceptor(
    private val responses: List<SyntheticResponse>,
) : Interceptor {
    val requests = mutableListOf<Request>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = responses.getOrNull(requests.size)
            ?: error("Missing synthetic response for request ${requests.size + 1}")
        requests += request
        return syntheticHttpResponse(request, response.code, response.body)
    }
}

private fun syntheticHttpResponse(request: Request, code: Int, body: String): Response =
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("synthetic")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

private fun Request.requestBodyJson() = Json.parseToJsonElement(
    Buffer().also { buffer -> body?.writeTo(buffer) }.readUtf8(),
).jsonObject
