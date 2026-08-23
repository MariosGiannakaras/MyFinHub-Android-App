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
                    .body(financeEnvelope("41"))
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
            val document = documentWithUnknownFields()

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

    @Test
    fun backup_usesBearerNoCookieAndParsesPath() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"path":"supabase://rheomiq_backups/77"}""")
                    .build(),
            )
            val api = OkHttpMyFinHubApi(configuration(server), OkHttpClient())

            val result = api.createBackup(session)
            val request = server.takeRequest()

            assertTrue(result is ApiResult.Success)
            assertEquals("supabase://rheomiq_backups/77", (result as ApiResult.Success).value.path)
            assertEquals("POST", request.method)
            assertEquals("/api/backup", request.url.encodedPath)
            assertEquals("Bearer synthetic-bearer", request.headers["Authorization"])
            assertFalse(request.headers.names().contains("Cookie"))
        }
    }

    @Test
    fun import_sendsFullLosslessDocumentAndExplicitReplaceConfirmation() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body(financeEnvelope("52"))
                    .build(),
            )
            val api = OkHttpMyFinHubApi(configuration(server), OkHttpClient())
            val document = documentWithUnknownFields()

            val result = api.importFinanceData(session, document)
            val request = server.takeRequest()
            val sent = Json.parseToJsonElement(request.body!!.utf8()).jsonObject

            assertTrue(result is ApiResult.Success)
            assertEquals("52", (result as ApiResult.Success).value.revision)
            assertEquals("POST", request.method)
            assertEquals("/api/import", request.url.encodedPath)
            assertEquals("replace", request.headers["X-RheomIQ-Confirm-Import"])
            assertEquals("Bearer synthetic-bearer", request.headers["Authorization"])
            assertFalse(request.headers.names().contains("Cookie"))
            assertTrue(sent.containsKey("seed"))
            assertTrue(sent.containsKey("state"))
            assertTrue(sent.containsKey("futureRoot"))
            assertTrue(sent["state"]!!.jsonObject.containsKey("futureState"))
        }
    }

    @Test
    fun cardSecretBoundaries_useOwnerBearerContractAndNeverEmitCvvField() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"pan":"4242424242424242","expiry":"12/30"}""")
                    .build(),
            )
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"saved":true,"last4":"4242"}""")
                    .build(),
            )
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"deleted":true}""")
                    .build(),
            )
            val api = OkHttpMyFinHubApi(configuration(server), OkHttpClient())

            val revealed = api.loadCardSecrets(session, "card-1") as ApiResult.Success
            val revealRequest = server.takeRequest()
            val revealBody = Json.parseToJsonElement(revealRequest.body!!.utf8()).jsonObject
            assertEquals("4242", revealed.value.pan?.takeLast(4))
            assertFalse(revealed.value.toString().contains("4242424242424242"))
            assertEquals("POST", revealRequest.method)
            assertEquals("/api/card-secrets", revealRequest.url.encodedPath)
            assertEquals(setOf("cardId"), revealBody.keys)

            val update = CardSecretUpdate(pan = "4242424242424242", expiry = "12/30")
            assertFalse(update.toString().contains("4242424242424242"))
            val saved = api.saveCardSecrets(session, "card-1", update) as ApiResult.Success
            val saveRequest = server.takeRequest()
            val saveBody = Json.parseToJsonElement(saveRequest.body!!.utf8()).jsonObject
            assertEquals("4242", saved.value.last4)
            assertEquals("PUT", saveRequest.method)
            assertEquals(setOf("cardId", "pan", "expiry"), saveBody.keys)
            assertFalse(saveBody.containsKey("cvv"))
            assertFalse(saveBody.containsKey("cvc"))

            val deleted = api.deleteCardSecrets(session, "card-1") as ApiResult.Success
            val deleteRequest = server.takeRequest()
            val deleteBody = Json.parseToJsonElement(deleteRequest.body!!.utf8()).jsonObject
            assertTrue(deleted.value.deleted)
            assertEquals("DELETE", deleteRequest.method)
            assertEquals(setOf("cardId"), deleteBody.keys)

            listOf(revealRequest, saveRequest, deleteRequest).forEach { request ->
                assertEquals("Bearer synthetic-bearer", request.headers["Authorization"])
                assertFalse(request.headers.names().contains("Cookie"))
            }
        }
    }

    @Test
    fun cardSecret404_hasDistinctFailClosedResult() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(404)
                    .body("""{"code":"CARD_SECRET_NOT_FOUND"}""")
                    .build(),
            )
            val api = OkHttpMyFinHubApi(configuration(server), OkHttpClient())

            val result = api.loadCardSecrets(session, "card-1")

            assertTrue(result is ApiResult.Failure)
            assertEquals(ApiFailureKind.CARD_SECRET_NOT_FOUND, (result as ApiResult.Failure).kind)
        }
    }

    @Test
    fun malformedNewBoundaryResponses_failClosed() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("{}").build())
            server.enqueue(MockResponse.Builder().code(200).body("{}").build())
            server.enqueue(MockResponse.Builder().code(200).body("{}").build())
            val api = OkHttpMyFinHubApi(configuration(server), OkHttpClient())

            val backup = api.createBackup(session)
            val imported = api.importFinanceData(session, documentWithUnknownFields())
            val card = api.loadCardSecrets(session, "card-1")

            assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (backup as ApiResult.Failure).kind)
            assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (imported as ApiResult.Failure).kind)
            assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (card as ApiResult.Failure).kind)
        }
    }

    private fun documentWithUnknownFields() = CanonicalFinanceDocument(
        Json.parseToJsonElement(
            """{
              "app":"RheomIQ",
              "schemaVersion":3,
              "updatedAt":"2026-08-23T00:00:00Z",
              "futureRoot":"keep",
              "seed":{"accounts":[],"snapshots":[]},
              "state":{"events":[],"futureState":{"keep":true}}
            }""".trimIndent(),
        ).jsonObject,
    )

    private fun financeEnvelope(revision: String) = """{
      "data":{
        "app":"RheomIQ",
        "schemaVersion":3,
        "updatedAt":"2026-08-23T00:00:00Z",
        "seed":{"accounts":[],"snapshots":[]},
        "state":{"events":[]}
      },
      "revision":"$revision",
      "lastSavedAt":"2026-08-23T00:00:01Z"
    }""".trimIndent()

    private fun configuration(server: MockWebServer) = AppConfiguration(
        myFinHubApiBaseUrl = server.url("/").toString().removeSuffix("/"),
        supabaseUrl = "https://project.supabase.test",
        supabasePublishableKey = "synthetic-key",
    )
}
