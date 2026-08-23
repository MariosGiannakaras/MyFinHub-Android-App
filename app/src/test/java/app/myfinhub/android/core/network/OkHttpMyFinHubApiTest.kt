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
    fun saveMutableState_sendsIfMatchAndOnlyMutableStateEnvelope() = runBlocking {
        val interceptor = RecordingInterceptor(
            code = 200,
            body = """{"revision":"18","lastSavedAt":"2026-08-22T00:01:00.000Z"}""",
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
        assertEquals("17", interceptor.request?.header("If-Match"))
        val sent = interceptor.requestBodyJson()
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
        val interceptor = RecordingInterceptor(200, """{"lastSavedAt":"x"}""")
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())
        val document = CanonicalFinanceDocument(
            Json.parseToJsonElement("""{"updatedAt":"2026-08-22T00:00:00Z","state":{},"seed":{}}""").jsonObject,
        )

        val result = api.saveMutableState(session, document, "7") as ApiResult.Failure

        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, result.kind)
    }

    @Test
    fun aal1Session_failsBeforeNetwork() = runBlocking {
        val interceptor = RecordingInterceptor(200, "{}")
        val api = OkHttpMyFinHubApi(config, OkHttpClient.Builder().addInterceptor(interceptor).build())

        val result = api.loadFinanceData(session.copy(assuranceLevel = AssuranceLevel.AAL1)) as ApiResult.Failure

        assertEquals(ApiFailureKind.MFA_REQUIRED, result.kind)
        assertEquals(null, interceptor.request)
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
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("synthetic")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    fun requestBodyJson() = Json.parseToJsonElement(
        Buffer().also { buffer -> request?.body?.writeTo(buffer) }.readUtf8(),
    ).jsonObject
}
