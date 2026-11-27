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

class SharedAppLockApiContractTest {
    private val owner = AuthSession("synthetic-owner", "synthetic-refresh", 99999, "owner", AssuranceLevel.AAL2)
    private val syntheticTicket = "a".repeat(43)
    private val status = "\"enabled\":true,\"idleMinutes\":5,\"retryAfterMs\":0"
    private fun api(server: MockWebServer) = SharedAppLockApi(
        AppConfiguration(server.url("/").toString().removeSuffix("/"),
            "https://project.supabase.test", "synthetic-key"), OkHttpClient(),
    )
    private fun MockWebServer.enqueueJson(body: String, code: Int = 200) =
        enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test fun statusIsBearerOnlyAndDoesNotTreatMalformedStatusAsDisabled() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = api(server)
            server.enqueueJson("""{"ok":true,"enabled":false,"idleMinutes":5,"retryAfterMs":0}""")
            assertEquals(SharedAppLockResult.Success(SharedAppLockStatus(false, 5, 0)), api.status(owner))
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/auth/app-lock", request.url.encodedPath)
            assertEquals("Bearer synthetic-owner", request.headers["Authorization"])
            assertNull(request.headers["Cookie"])
            assertNull(request.headers["X-MyFinHub-App-Unlock"])
            for (bad in listOf("{}", """{"ok":true,"enabled":"false","idleMinutes":5,"retryAfterMs":0}""",
                """{"ok":true,"enabled":false,"idleMinutes":2,"retryAfterMs":0}""",
                """{"ok":true,"enabled":false,"idleMinutes":5,"retryAfterMs":1}""",
                """{"ok":true,$status,"unlockToken":"$syntheticTicket"}""", " ".repeat(8193))) {
                server.enqueueJson(bad)
                assertEquals(SharedAppLockFailure.MALFORMED_RESPONSE, (api.status(owner) as SharedAppLockResult.Failure).kind)
            }
        }
    }

    @Test fun verifyOnlyIssuesRedactedTicketForExactOwnerBearerAndAal2() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueueJson("""{"ok":true,$status,"unlockToken":"$syntheticTicket"}""")
            val result = api(server).verify(owner, "1234".toCharArray())
            val verified = (result as SharedAppLockResult.Success<SharedAppLockVerification>).value as SharedAppLockVerification.Verified
            assertEquals(syntheticTicket, verified.ticket.valueFor(owner))
            assertNull(verified.ticket.valueFor(owner.copy(userId = "other")))
            assertNull(verified.ticket.valueFor(owner.copy(accessToken = "rotated")))
            assertNull(verified.ticket.valueFor(owner.copy(assuranceLevel = AssuranceLevel.AAL1)))
            assertFalse(result.toString().contains(syntheticTicket))
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/auth/app-lock", request.url.encodedPath)
            assertEquals("Bearer synthetic-owner", request.headers["Authorization"])
            assertEquals("""{"action":"verify","pin":"1234"}""", request.body!!.utf8())
            assertNull(request.headers["Cookie"])
        }
    }

    @Test fun HTTP200RejectionsAreNeverAnUnlockAndInvalidTicketFailsClosed() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = api(server)
            server.enqueueJson("""{"ok":false,"enabled":true,"idleMinutes":5,"retryAfterMs":0,"error":"APP_LOCK_WRONG_PIN"}""")
            assertEquals(SharedAppLockResult.Success(SharedAppLockVerification.Rejected(
                SharedAppLockStatus(true, 5, 0), SharedAppLockVerification.Rejection.WRONG_PIN)), api.verify(owner, "1234".toCharArray()))
            server.enqueueJson("""{"ok":false,"enabled":true,"idleMinutes":15,"retryAfterMs":30000,"error":"APP_LOCK_RATE_LIMITED"}""")
            assertEquals(SharedAppLockResult.Success(SharedAppLockVerification.Rejected(
                SharedAppLockStatus(true, 15, 30000), SharedAppLockVerification.Rejection.RATE_LIMITED)), api.verify(owner, "1234".toCharArray()))
            server.enqueueJson("""{"ok":false,"enabled":false,"idleMinutes":5,"retryAfterMs":0,"error":"APP_LOCK_NOT_CONFIGURED"}""")
            assertEquals(SharedAppLockResult.Success(SharedAppLockVerification.Rejected(
                SharedAppLockStatus(false, 5, 0), SharedAppLockVerification.Rejection.NOT_CONFIGURED)), api.verify(owner, "1234".toCharArray()))
            for (bad in listOf("""{"ok":true,$status}""", """{"ok":true,$status,"unlockToken":"bad"}""",
                """{"ok":false,$status,"error":"APP_LOCK_WRONG_PIN","unlockToken":"$syntheticTicket"}""",
                """{"ok":true,"enabled":false,"idleMinutes":5,"retryAfterMs":0,"unlockToken":"$syntheticTicket"}""")) {
                server.enqueueJson(bad)
                assertEquals(SharedAppLockFailure.MALFORMED_RESPONSE, (api.verify(owner, "1234".toCharArray()) as SharedAppLockResult.Failure).kind)
            }
        }
    }

    @Test fun leaseActionsRequireExactTicketAndNeverFollowRedirectsOrRetry() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = api(server)
            val ticket = requireNotNull(SharedAppUnlockTicket.fromVerifiedResponse(syntheticTicket, owner))
            assertEquals(SharedAppLockFailure.WRONG_SESSION, (api.touch(owner.copy(accessToken = "new"), ticket) as SharedAppLockResult.Failure).kind)
            assertEquals(0, server.requestCount)
            server.enqueueJson("""{"ok":true}""")
            assertEquals(SharedAppLockResult.Success(Unit), api.touch(owner, ticket))
            assertEquals("""{"action":"touch","unlockToken":"$syntheticTicket"}""", server.takeRequest().body!!.utf8())
            server.enqueueJson("""{"code":"APP_LOCK_REQUIRED","message":"private"}""", 423)
            assertEquals(SharedAppLockResult.Failure(SharedAppLockFailure.LOCK_REQUIRED, 423), api.touch(owner, ticket))
            server.takeRequest()
            server.enqueueJson("""{"ok":true}""")
            assertEquals(SharedAppLockResult.Success(Unit), api.lock(owner, ticket))
            assertEquals("""{"action":"lock","unlockToken":"$syntheticTicket"}""", server.takeRequest().body!!.utf8())
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/unsafe").toString()).build())
            assertEquals(SharedAppLockResult.Failure(SharedAppLockFailure.UNAVAILABLE, 302), api.verify(owner, "1234".toCharArray()))
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun recoveryRequiresFreshCredentialFieldsAndNeverYieldsATicket() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = api(server)
            val password = "synthetic-password".toCharArray()
            val code = "123456".toCharArray()
            val pin = "2345".toCharArray()
            server.enqueueJson("""{"ok":true,$status}""")
            assertEquals(SharedAppLockResult.Success(SharedAppLockStatus(true, 5, 0)),
                api.recover(owner, " OWNER@example.test ", password, code, pin))
            val request = server.takeRequest()
            assertEquals("Bearer synthetic-owner", request.headers["Authorization"])
            assertEquals("""{"action":"recover","email":"owner@example.test","password":"synthetic-password","code":"123456","newPin":"2345"}""", request.body!!.utf8())
            server.enqueueJson("""{"ok":true,$status,"unlockToken":"$syntheticTicket"}""")
            assertEquals(SharedAppLockFailure.MALFORMED_RESPONSE,
                (api.recover(owner, "owner@example.test", password, code, pin) as SharedAppLockResult.Failure).kind)
            assertEquals(SharedAppLockFailure.INVALID_INPUT,
                (api.recover(owner, "owner@example.test", password, "１２３４５６".toCharArray(), pin) as SharedAppLockResult.Failure).kind)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun invalidInputUnconfiguredAndAal1AreRejectedBeforeNetworkAndErrorsStayRedacted() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = api(server)
            for (bad in listOf("123", "12345", "１２３４", "12 4")) {
                assertEquals(SharedAppLockFailure.INVALID_INPUT, (api.verify(owner, bad.toCharArray()) as SharedAppLockResult.Failure).kind)
            }
            assertEquals(SharedAppLockFailure.MFA_REQUIRED,
                (api.status(owner.copy(assuranceLevel = AssuranceLevel.AAL1)) as SharedAppLockResult.Failure).kind)
            assertEquals(SharedAppLockFailure.AUTH_REQUIRED,
                (api.status(owner.copy(accessToken = "")) as SharedAppLockResult.Failure).kind)
            val unconfigured = SharedAppLockApi(AppConfiguration("", "", ""), OkHttpClient())
            assertEquals(SharedAppLockFailure.BUILD_NOT_CONFIGURED, (unconfigured.status(owner) as SharedAppLockResult.Failure).kind)
            assertEquals(0, server.requestCount)
            for ((code, expected) in listOf(401 to SharedAppLockFailure.AUTH_REQUIRED, 403 to SharedAppLockFailure.FORBIDDEN,
                423 to SharedAppLockFailure.LOCK_REQUIRED, 429 to SharedAppLockFailure.RATE_LIMITED, 503 to SharedAppLockFailure.UNAVAILABLE)) {
                server.enqueueJson("""{"code":"unknown","message":"synthetic-sensitive-body"}""", code)
                val result = api.status(owner)
                assertEquals(SharedAppLockResult.Failure(expected, code), result)
                assertFalse(result.toString().contains("synthetic-sensitive-body"))
            }
        }
    }
}
