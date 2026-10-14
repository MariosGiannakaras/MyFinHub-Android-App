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
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountMetadataApiContractTest {
    private val owner = AuthSession(
        accessToken = "synthetic-bearer",
        refreshToken = "synthetic-refresh",
        expiresAtEpochSeconds = 99_999,
        userId = "owner",
        assuranceLevel = AssuranceLevel.AAL2,
    )

    @Test
    fun bearerGetReadsValidAccountMetadataWithoutCookiesAndRedactsDiagnostics() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("""
              {"records":[
                {"accountId":"payroll","iban":"GR1601101250000000012300695","revision":1,"updatedAt":"2026-10-14T01:00:00Z"},
                {"accountId":"bad-iban","iban":"GR0000000000000000000000000","revision":1,"updatedAt":"2026-10-14T01:00:00Z"},
                {"accountId":"bad-revision","iban":null,"revision":0,"updatedAt":"2026-10-14T01:00:00Z"}
              ]}
            """.trimIndent()).build())
            val api = OkHttpMyFinHubApi(configuration(server), OkHttpClient())
            val result = api.loadAccountMetadata(owner)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/account-metadata", request.url.encodedPath)
            assertEquals("Bearer synthetic-bearer", request.headers["Authorization"])
            assertFalse(request.headers.names().contains("Cookie"))
            assertTrue(result is ApiResult.Success)
            val records = (result as ApiResult.Success).value
            assertEquals(setOf("payroll"), records.keys)
            assertEquals("GR1601101250000000012300695", records["payroll"]?.iban)
            assertFalse(records["payroll"].toString().contains("GR160"))
            assertEquals("GR16 •••• •••• •••• 0695", compactIban(records["payroll"]?.iban))
        }
    }

    @Test
    fun aal1CannotLoadAccountMetadata() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = OkHttpMyFinHubApi(configuration(server), OkHttpClient())
            val result = api.loadAccountMetadata(owner.copy(assuranceLevel = AssuranceLevel.AAL1))
            assertTrue(result is ApiResult.Failure)
            assertEquals(ApiFailureKind.MFA_REQUIRED, (result as ApiResult.Failure).kind)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun rootPayloadMustDeclareRecords() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("{}").build())
            val api = OkHttpMyFinHubApi(configuration(server), OkHttpClient())
            val result = api.loadAccountMetadata(owner)
            assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (result as ApiResult.Failure).kind)
        }
    }

    @Test
    fun emptyAndInvalidIbanBehaveLikeWeb() {
        assertTrue(isValidIban(null))
        assertTrue(isValidIban(" "))
        assertTrue(isValidIban("GR16 0110 1250 0000 0001 2300 695"))
        assertFalse(isValidIban("GR16 0110 1250 0000 0001 2300 694"))
        assertEquals(null, compactIban("not-an-iban"))
    }

    private fun configuration(server: MockWebServer) = AppConfiguration(
        myFinHubApiBaseUrl = server.url("/").toString().removeSuffix("/"),
        supabaseUrl = "https://project.supabase.test",
        supabasePublishableKey = "synthetic-key",
    )
}
