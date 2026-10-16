package app.myfinhub.android.core.network

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
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

/** Real deployed Web PATCH /api/auth/account, with no finance state, cookie or offline queue. */
class WebAccountSecurityContractTest {
    private val owner = AuthSession("secure-test-token","refresh",99999,"owner",AssuranceLevel.AAL2)
    private fun api(server: MockWebServer) = OkHttpMyFinHubApi(
        AppConfiguration(server.url("/").toString().removeSuffix("/"),
            "https://supabase.synthetic.test","synthetic-publishable"),
        OkHttpClient(),
    )

    @Test fun changingEmailRequiresServerProofOfSubmittedEmail() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body(
                """{"ok":true,"email":"old@example.org","pendingEmail":"new@example.org"}"""
            ).build())
            val result=api(server).changeAccountSecurity(owner,
                WebAccountSecurityChange.Email(" NEW@Example.org "))
            assertTrue(result is ApiResult.Success)
            assertTrue((result as ApiResult.Success<WebAccountSecurityReceipt>)
                .value.emailPendingConfirmation)
            val request=server.takeRequest()
            assertEquals("PATCH",request.method)
            assertEquals("/api/auth/account",request.url.encodedPath)
            assertEquals("Bearer secure-test-token",request.headers["Authorization"])
            assertFalse(request.headers.names().contains("Cookie"))
            val sent=Json.parseToJsonElement(request.body!!.utf8()).jsonObject
            assertEquals("email",sent["action"]!!.toString().trim('"'))
            assertEquals("new@example.org",sent["email"]!!.toString().trim('"'))
            assertEquals(1,server.requestCount)
        }
    }

    @Test fun passwordWritesUseWebContractWithoutRetainingSecretInReceipt() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("""{"ok":true}""").build())
            val change=WebAccountSecurityChange.Password(
                "Current1!Password","Replacement2!Password")
            val result=api(server).changeAccountSecurity(owner,change)
            assertTrue(result is ApiResult.Success)
            val request=server.takeRequest()
            val body=Json.parseToJsonElement(request.body!!.utf8()).jsonObject
            assertEquals("password",body["action"]!!.toString().trim('"'))
            assertEquals("Current1!Password",body["currentPassword"]!!.toString().trim('"'))
            assertEquals("Replacement2!Password",body["newPassword"]!!.toString().trim('"'))
            assertFalse(change.toString().contains("Replacement"))
            assertFalse((result as ApiResult.Success<WebAccountSecurityReceipt>)
                .value.toString().contains("Password"))
            assertEquals("PATCH",request.method)
            assertEquals(1,server.requestCount)
        }
    }

    @Test fun aal1BadPasswordAndMalformedReceiptNeverSendOrAcceptChanges() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client=api(server)
            val aal1=client.changeAccountSecurity(owner.copy(assuranceLevel=AssuranceLevel.AAL1),
                WebAccountSecurityChange.Email("new@example.org"))
            assertEquals(ApiFailureKind.MFA_REQUIRED,(aal1 as ApiResult.Failure).kind)
            val invalid=client.changeAccountSecurity(owner,
                WebAccountSecurityChange.Password("current","no"))
            assertEquals(ApiFailureKind.INVALID_DATA,(invalid as ApiResult.Failure).kind)
            val unchanged=client.changeAccountSecurity(owner,
                WebAccountSecurityChange.Email("no-address"))
            assertEquals(ApiFailureKind.INVALID_DATA,(unchanged as ApiResult.Failure).kind)
            assertEquals(0,server.requestCount)
            server.enqueue(MockResponse.Builder().code(200).body(
                """{"ok":true,"email":"someoneelse@example.org","pendingEmail":null}""").build())
            val malformed=client.changeAccountSecurity(owner,
                WebAccountSecurityChange.Email("new@example.org"))
            assertEquals(ApiFailureKind.MALFORMED_RESPONSE,(malformed as ApiResult.Failure).kind)
            assertEquals(1,server.requestCount)
        }
    }

    @Test fun unicodePasswordPolicyMatchesWebAndDoesNotAcceptWhitespaceAloneAsSymbol() {
        assertTrue(validAccountSecurityPassword("Old123!Password","Νέος123!Password"))
        assertFalse(validAccountSecurityPassword("Old123!Password","New123 Password"))
        assertFalse(validAccountSecurityPassword("Current123!Secure","Current123!Secure"))
        assertEquals("new@example.org",normalizedAccountSecurityEmail(" NEW@EXAMPLE.ORG "))
        assertEquals(null,normalizedAccountSecurityEmail("no @domain"))
    }
}
