package app.myfinhub.android.core.network

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDeviceSessionReadOnlyContractTest {
    private val owner = AuthSession(
        accessToken = "synthetic-owner",
        refreshToken = "refresh",
        expiresAtEpochSeconds = 99999,
        userId = "owner", assuranceLevel = AssuranceLevel.AAL2,
    )
    private fun api(server: MockWebServer) = OkHttpMyFinHubApi(
        AppConfiguration(server.url("/").toString().removeSuffix("/"),
            "https://project.supabase.test", "synthetic-key"), OkHttpClient(),
    )
    private val payload = """{"count":1,"devices":[{"sessionId":"5b90719d-65c1-4abc-8299-3e07b192ac00",
        "platform":"android","label":"Galaxy S24 Ultra","appVersion":"1.0.0",
        "firstSeenAt":"2026-10-01T09:00:00Z","lastSeenAt":"2026-10-16T09:00:00Z","current":true}]}"""

    @Test fun ownerCanListWithoutCookiesOrSessionMutation() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body(payload).build())
            val result = api(server).loadDeviceSessions(owner)
            assertTrue(result is ApiResult.Success)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/auth/devices", request.url.encodedPath)
            assertEquals("Bearer synthetic-owner",request.headers["Authorization"])
            assertFalse("Cookie" in request.headers.names())
            val rows = (result as ApiResult.Success<List<DeviceSessionRecord>>).value
            assertEquals(1,rows.size)
            assertEquals(true,rows.single().current)
            assertEquals("Galaxy S24 Ultra",rows.single().label)
        }
    }

    @Test fun remoteRevocationPostsExactWebActionAndUsesFreshServerResponse() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body(payload).build())
            val id = "5b90719d-65c1-4abc-8299-3e07b192ac11"
            val result = api(server).revokeOtherDeviceSession(owner, id)
            assertTrue(result is ApiResult.Success)
            val request = server.takeRequest()
            assertEquals("POST",request.method)
            assertEquals("/api/auth/devices",request.url.encodedPath)
            assertEquals("Bearer synthetic-owner",request.headers["Authorization"])
            assertFalse("Cookie" in request.headers.names())
            assertEquals("""{"action":"revoke","sessionId":"$id"}""",request.body!!.utf8())
        }
    }

    @Test fun invalidSessionIdAndUnverifiedOwnerCannotPost() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = api(server)
            val id = "5b90719d-65c1-4abc-8299-3e07b192ac11"
            assertEquals(ApiFailureKind.MFA_REQUIRED,
                (client.revokeOtherDeviceSession(owner.copy(assuranceLevel=AssuranceLevel.AAL1),id)
                    as ApiResult.Failure).kind)
            assertEquals(ApiFailureKind.INVALID_DATA,
                (client.revokeOtherDeviceSession(owner,"invalid") as ApiResult.Failure).kind)
            assertEquals(0,server.requestCount)
        }
    }

    @Test fun rejectsAal1BeforeHttpAndMalformedCountOrId() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val rejected=api(server).loadDeviceSessions(owner.copy(assuranceLevel = AssuranceLevel.AAL1))
            assertEquals(ApiFailureKind.MFA_REQUIRED,(rejected as ApiResult.Failure).kind)
            assertEquals(0,server.requestCount)
        }
        assertNull(parseDeviceSessions("""{"count":2,"devices":[]}"""))
        assertNull(parseDeviceSessions(payload.replace(
            "5b90719d-65c1-4abc-8299-3e07b192ac00", "untrusted")))
        assertNull(parseDeviceSessions("{}"))
        assertEquals(1,parseDeviceSessions(payload)?.size)
    }
}
