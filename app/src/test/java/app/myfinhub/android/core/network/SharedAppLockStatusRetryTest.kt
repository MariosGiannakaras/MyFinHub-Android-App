package app.myfinhub.android.core.network

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedAppLockStatusRetryTest {
    private val configuration = AppConfiguration("https://api.example.test", "https://auth.example.test", "synthetic-key")
    private val session = AuthSession("synthetic-bearer", "synthetic-refresh", 99999, "owner", AssuranceLevel.AAL2)

    private fun response(request: Request, code: Int, body: String = """{"ok":true,"enabled":false,"idleMinutes":5,"retryAfterMs":0}"""): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("synthetic")
            .body(body.toResponseBody("application/json".toMediaType())).build()

    @Test
    fun statusRetriesTransportFailureOnceWithIdenticalBearer() = runBlocking {
        val requests = mutableListOf<Request>()
        val api = SharedAppLockApi(configuration, OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            if (requests.size == 1) throw IOException("Synthetic closed idle socket")
            response(chain.request(), 200)
        }.build())
        val result = api.status(session)
        assertTrue(result is SharedAppLockResult.Success && !result.value.enabled)
        assertEquals(2, requests.size)
        requests.forEach {
            assertEquals("GET", it.method)
            assertEquals("/api/auth/app-lock", it.url.encodedPath)
            assertEquals("Bearer synthetic-bearer", it.header("Authorization"))
        }
    }

    @Test
    fun statusFailsClosedAfterTwoTransportFailuresAndDoesNotRetryHttpDenial() = runBlocking {
        var networkCalls = 0
        val network = SharedAppLockApi(configuration, OkHttpClient.Builder().addInterceptor {
            networkCalls++
            throw IOException("Synthetic transport failure")
        }.build())
        assertEquals(SharedAppLockResult.Failure(SharedAppLockFailure.NETWORK), network.status(session))
        assertEquals(2, networkCalls)
        var deniedCalls = 0
        val denied = SharedAppLockApi(configuration, OkHttpClient.Builder().addInterceptor { chain ->
            deniedCalls++
            response(chain.request(), 401, "{}")
        }.build())
        assertEquals(SharedAppLockResult.Failure(SharedAppLockFailure.AUTH_REQUIRED, 401), denied.status(session))
        assertEquals(1, deniedCalls)
    }

    @Test
    fun pinVerificationNeverRetriesTransportFailure() = runBlocking {
        var calls = 0
        val api = SharedAppLockApi(configuration, OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            assertEquals("POST", chain.request().method)
            throw IOException("Synthetic transport failure")
        }.build())
        assertEquals(SharedAppLockResult.Failure(SharedAppLockFailure.NETWORK), api.verify(session, "1234".toCharArray()))
        assertEquals(1, calls)
    }
}
