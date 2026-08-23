package app.myfinhub.android.core.network

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpMyFinHubApiMockWebServerTest {
    private val session = AuthSession(
        accessToken = "synthetic-bearer",
        refreshToken = "synthetic-refresh",
        expiresAtEpochSeconds = 99_999,
        userId = "owner",
        assuranceLevel = AssuranceLevel.AAL2,
    )

    @Test
    fun getData_usesBearerAndNeverCookie_overRealHttpStack() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body(
                        """{
                          "data":{
                            "app":"RheomIQ",
                            "schemaVersion":3,
                            "updatedAt":"2026-08-23T00:00:00Z",
                            "seed":{"accounts":[],"snapshots":[]},
                            "state":{"events":[]}
                          },
                          "revision":"41",
                          "lastSavedAt":"2026-08-23T00:00:01Z"
                        }""".trimIndent(),
                    )
                    .build(),
            )
            val api = OkHttpMyFinHubApi(configuration(server), OkHttpClient())

            val result = api.loadFinanceData(session)
            val request = server.takeRequest()

            assertTrue(result is ApiResult.Success)
            assertEquals("GET", request.method)
            assertEquals("/api/data", request.url.encodedPath)
            assertEquals("Bearer synthetic-bearer", request.headers["Authorization"])
            assertFalse(request.headers.names().contains("Cookie"))
        }
    }

    @Test
    fun putData_sendsIfMatchAndMaps409_overRealHttpStack() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(409)
                    .body("""{"code":"REVISION_CONFLICT"}""")
                    .build(),
            )
            val api = OkHttpMyFinHubApi(configuration(server), OkHttpClient())
            val document = CanonicalFinanceDocument(
                Json.parseToJsonElement(
                    """{
                      "app":"RheomIQ",
                      "schemaVersion":3,
                      "updatedAt":"2026-08-23T00:00:00Z",
                      "futureRoot":"keep",
                      "seed":{"accounts":[]},
                      "state":{"events":[],"futureState":{"keep":true}}
                    }""".trimIndent(),
                ).jsonObject,
            )

            val result = api.saveMutableState(session, document, "40")
            val request = server.takeRequest()
            val sent = Json.parseToJsonElement(request.body!!.utf8()).jsonObject

            assertTrue(result is ApiResult.Failure)
            assertEquals(ApiFailureKind.REVISION_CONFLICT, (result as ApiResult.Failure).kind)
            assertEquals("PUT", request.method)
            assertEquals("/api/data", request.url.encodedPath)
            assertEquals("Bearer synthetic-bearer", request.headers["Authorization"])
            assertEquals("40", request.headers["If-Match"])
            assertFalse(request.headers.names().contains("Cookie"))
            assertTrue(sent.containsKey("state"))
            assertTrue(sent["state"]!!.jsonObject.containsKey("futureState"))
            assertFalse(sent.containsKey("seed"))
        }
    }

    private fun configuration(server: MockWebServer) = AppConfiguration(
        myFinHubApiBaseUrl = server.url("/").toString().removeSuffix("/"),
        supabaseUrl = "https://project.supabase.test",
        supabasePublishableKey = "synthetic-key",
    )
}
