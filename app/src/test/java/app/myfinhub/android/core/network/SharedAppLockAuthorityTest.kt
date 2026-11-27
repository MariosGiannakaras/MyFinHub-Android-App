package app.myfinhub.android.core.network

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedAppLockAuthorityTest {
    private val session = AuthSession("synthetic-bearer", "synthetic-refresh", 99999, "owner", AssuranceLevel.AAL2)
    private val token = "a".repeat(43)
    private var now = 1000L
    private val authority = SharedAppLockAuthority { now }

    private fun verified(value: String = token) = SharedAppLockVerification.Verified(
        SharedAppLockStatus(true, 1, 0),
        requireNotNull(SharedAppUnlockTicket.fromVerifiedResponse(value, session)),
    )

    private fun api(server: MockWebServer) = OkHttpMyFinHubApi(
        AppConfiguration(server.url("/").toString().removeSuffix("/"),
            "https://project.supabase.test", "synthetic-key"),
        OkHttpClient(), authority,
    )
    private val data = """{"data":{},"revision":"1","lastSavedAt":"synthetic"}"""
    private fun response(body: String, status: Int = 200) =
        MockResponse.Builder().code(status).body(body).build()

    @Test fun unknownOrWrongSessionCannotPresentOrSendProtectedRequests() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = api(server)
            assertFalse(authority.canPresent(session))
            assertFalse(authority.publishOfflineDisabled(session))
            assertEquals(ApiFailureKind.APP_LOCK_REQUIRED,
                (api.loadFinanceData(session) as ApiResult.Failure).kind)
            assertEquals(0, server.requestCount)
            assertTrue(authority.publishVerified(session, verified(), now))
            assertFalse(authority.canPresent(session.copy(accessToken = "new-bearer")))
            assertFalse(authority.canPresent(session.copy(userId = "other-owner")))
            assertFalse(authority.canPresent(session.copy(assuranceLevel = AssuranceLevel.AAL1)))
            assertEquals(ApiFailureKind.APP_LOCK_REQUIRED,
                (api.loadFinanceData(session.copy(accessToken = "new-bearer")) as ApiResult.Failure).kind)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun previouslyConfirmedDisabledOnlyPermitsExactProcessLocalOfflineAccess() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            authority.publishDisabled(session)
            server.enqueue(response(data))
            assertTrue(api(server).loadFinanceData(session) is ApiResult.Success)
            assertNull(server.takeRequest().headers["X-MyFinHub-App-Unlock"])
            authority.clear(rememberDisabled = true)
            assertFalse(authority.canPresent(session))
            assertFalse(authority.publishOfflineDisabled(session.copy(accessToken = "rotated")))
            assertTrue(authority.publishOfflineDisabled(session))
            assertTrue(authority.canPresent(session))
            assertNull(authority.requestGrant(session))
            assertEquals(ApiFailureKind.APP_LOCK_REQUIRED,
                (api(server).loadFinanceData(session) as ApiResult.Failure).kind)
            assertEquals(1, server.requestCount)
            authority.clear()
            assertFalse(authority.publishOfflineDisabled(session))
        }
    }

    @Test fun ticketIsSentOnAllProtectedRequestPathsWithoutCookieAnd423RevokesIt() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            assertTrue(authority.publishVerified(session, verified(), now))
            val api = api(server)
            server.enqueue(response(data))
            server.enqueue(response("""{"records":[]}"""))
            server.enqueue(response("""{"count":0,"devices":[]}"""))
            server.enqueue(response("""{"pan":null,"expiry":null,"cvv":null}"""))
            server.enqueue(response("""{"code":"APP_LOCK_REQUIRED","message":"private-body"}""", 423))
            assertTrue(api.loadFinanceData(session) is ApiResult.Success)
            assertTrue(api.loadAccountMetadata(session) is ApiResult.Success)
            assertTrue(api.loadDeviceSessions(session) is ApiResult.Success)
            api.loadCardSecrets(session, "card-1")
            val denied = api.loadFinanceData(session) as ApiResult.Failure
            assertEquals(ApiFailureKind.APP_LOCK_REQUIRED, denied.kind)
            assertEquals("APP_LOCK_REQUIRED", denied.serverCode)
            assertFalse(denied.retryable)
            assertFalse(denied.toString().contains("private-body"))
            assertFalse(authority.canPresent(session))
            assertFalse(authority.publishOfflineDisabled(session))
            for (path in listOf("/api/data","/api/account-metadata","/api/auth/devices","/api/card-secrets","/api/data")) {
                val request = server.takeRequest()
                assertEquals(path, request.url.encodedPath)
                assertEquals(token, request.headers["X-MyFinHub-App-Unlock"])
                assertEquals("Bearer synthetic-bearer", request.headers["Authorization"])
                assertNull(request.headers["Cookie"])
            }
            assertEquals(ApiFailureKind.APP_LOCK_REQUIRED, (api.loadFinanceData(session) as ApiResult.Failure).kind)
            assertEquals(5, server.requestCount)
        }
    }

    @Test fun staleSuccessfulOrRejectedRequestCannotPublishOrRevokeNewGrant() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = api(server)
            assertTrue(authority.publishVerified(session, verified(), now))
            for (status in listOf(200, 423)) {
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        authority.clear()
                        authority.publishVerified(session, verified("b".repeat(43)), now)
                        return if (status == 200) response(data) else response("""{"code":"APP_LOCK_REQUIRED"}""", 423)
                    }
                }
                assertEquals(ApiFailureKind.APP_LOCK_REQUIRED, (api.loadFinanceData(session) as ApiResult.Failure).kind)
                assertTrue(authority.canPresent(session))
                assertEquals("b".repeat(43), authority.requestGrant(session)?.headerValue())
            }
        }
    }

    @Test fun historyPreconditionCannotTransferAnOldMutationIntoANewUnlock() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            assertTrue(authority.publishVerified(session, verified(), now))
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    authority.clear()
                    authority.publishVerified(session, verified("c".repeat(43)), now)
                    return response("""{"available":true,"generation":"2","revision":"1"}""")
                }
            }
            val document = CanonicalFinanceDocument(Json.parseToJsonElement("{}").jsonObject)
            val result = api(server).saveMutableState(session, document, "1")
            assertEquals(ApiFailureKind.APP_LOCK_REQUIRED, (result as ApiResult.Failure).kind)
            assertEquals(1, server.requestCount)
            assertEquals("/api/history", server.takeRequest().url.encodedPath)
            assertTrue(authority.canPresent(session))
        }
    }

    @Test fun activityTouchExtendsOnlyLiveExactGrantAndIdleExpiresWithoutNetwork() {
        assertTrue(authority.publishVerified(session, verified(), now))
        val first = requireNotNull(authority.requestGrant(session))
        assertFalse(first.toString().contains(token))
        assertNull(authority.beginTouch(session))
        now += 15_000
        assertEquals(first, authority.beginTouch(session))
        assertTrue(authority.renew(first, now))
        now += 59_999
        assertTrue(authority.canPresent(session))
        now += 1
        assertTrue(authority.expireIfNeeded(session))
        assertFalse(authority.canPresent(session))
        assertFalse(authority.renew(first, now))
        assertTrue(authority.publishVerified(session, verified(), now))
        authority.reject(first)
        assertTrue(authority.canPresent(session))
    }

    @Test fun bearerStatusVerifyFinanceTouchAndRevokeUseTheSameMockStack() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val lockApi = SharedAppLockApi(
                AppConfiguration(server.url("/").toString().removeSuffix("/"),
                    "https://project.supabase.test", "synthetic-key"), OkHttpClient(),
            )
            server.enqueue(response("""{"ok":true,"enabled":true,"idleMinutes":1,"retryAfterMs":0}"""))
            server.enqueue(response("""{"ok":true,"enabled":true,"idleMinutes":1,"retryAfterMs":0,"unlockToken":"$token"}"""))
            server.enqueue(response(data))
            server.enqueue(response("""{"ok":true}"""))
            server.enqueue(response("""{"ok":true}"""))
            assertEquals(SharedAppLockResult.Success(SharedAppLockStatus(true, 1, 0)), lockApi.status(session))
            assertFalse(authority.canPresent(session))
            val verifiedResult = lockApi.verify(session, "1234".toCharArray())
            val result = (verifiedResult as SharedAppLockResult.Success<SharedAppLockVerification>).value
                as SharedAppLockVerification.Verified
            assertTrue(authority.publishVerified(session, result, now))
            assertTrue(api(server).loadFinanceData(session) is ApiResult.Success)
            now += 15000
            val grant = requireNotNull(authority.beginTouch(session))
            assertEquals(SharedAppLockResult.Success(Unit), lockApi.touch(session, result.ticket))
            assertTrue(authority.renew(grant, now))
            val revoked = requireNotNull(authority.clear())
            assertFalse(authority.canPresent(session))
            assertEquals(SharedAppLockResult.Success(Unit), lockApi.lock(session, revoked))
            assertEquals(ApiFailureKind.APP_LOCK_REQUIRED, (api(server).loadFinanceData(session) as ApiResult.Failure).kind)
            assertEquals(5, server.requestCount)
            assertEquals("/api/auth/app-lock", server.takeRequest().url.encodedPath)
            assertEquals("/api/auth/app-lock", server.takeRequest().url.encodedPath)
            assertEquals(token, server.takeRequest().headers["X-MyFinHub-App-Unlock"])
            assertEquals("""{"action":"touch","unlockToken":"$token"}""", server.takeRequest().body!!.utf8())
            assertEquals("""{"action":"lock","unlockToken":"$token"}""", server.takeRequest().body!!.utf8())
        }
    }

    @Test fun unavailableLockEnforcementFailsClosedWithoutClassifyingAsAuthLogout() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            authority.publishDisabled(session)
            server.enqueue(response("""{"code":"APP_LOCK_UNAVAILABLE"}""", 503))
            val failure = api(server).loadFinanceData(session) as ApiResult.Failure
            assertEquals(ApiFailureKind.APP_LOCK_REQUIRED, failure.kind)
            assertEquals("APP_LOCK_UNAVAILABLE", failure.serverCode)
            assertFalse(failure.retryable)
            assertFalse(authority.canPresent(session))
            assertFalse(authority.publishOfflineDisabled(session))
        }
    }
}
