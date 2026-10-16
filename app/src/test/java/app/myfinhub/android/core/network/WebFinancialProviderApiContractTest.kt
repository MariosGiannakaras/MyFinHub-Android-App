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

/** Existing Web owner+AAL2 dynamic financial-provider GET, never cookies or client secrets. */
class WebFinancialProviderApiContractTest {
    private val owner = AuthSession(
        accessToken = "synthetic-owner-access",
        refreshToken = "synthetic-refresh",
        expiresAtEpochSeconds = 99_999,
        userId = "owner",
        assuranceLevel = AssuranceLevel.AAL2,
    )
    private fun api(server: MockWebServer) = OkHttpMyFinHubApi(
        AppConfiguration(
            myFinHubApiBaseUrl = server.url("/").toString().removeSuffix("/"),
            supabaseUrl = "https://project.supabase.test",
            supabasePublishableKey = "synthetic-key",
        ), OkHttpClient(),
    )

    @Test fun ownerUsesExistingReadOnlyProviderEndpointWithBearerNotCookies() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("""
              {"providers":[
                {"id":"new-bank","displayName":"Νέα Τράπεζα","shortName":"Νέα",
                  "providerKind":"bank","sortOrder":9,"logoAssetKey":null}
              ]}
            """.trimIndent()).build())
            val result=api(server).loadFinancialProviders(owner)
            val request=server.takeRequest()
            assertTrue(result is ApiResult.Success)
            assertEquals("GET",request.method)
            assertEquals("/api/account-metadata",request.url.encodedPath)
            assertEquals("resource=financial-providers",request.url.encodedQuery)
            assertEquals("Bearer synthetic-owner-access",request.headers["Authorization"])
            assertFalse("Cookie" in request.headers.names())
            val providers=(result as ApiResult.Success<List<FinancialProviderRecord>>).value
            assertEquals(listOf("new-bank"),providers.map { it.id })
            assertEquals("Νέα Τράπεζα",providers.single().displayName)
        }
    }

    @Test fun insufficientAssuranceRefusesHttpBeforeAnyNetworkRequest() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val result=api(server).loadFinancialProviders(
                owner.copy(assuranceLevel = AssuranceLevel.AAL1))
            assertEquals(ApiFailureKind.MFA_REQUIRED,(result as ApiResult.Failure).kind)
            assertEquals(0,server.requestCount)
        }
    }

    @Test fun malformedProviderPayloadAndServerMfaFailClosed() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("{}").build())
            server.enqueue(MockResponse.Builder().code(403).body("private server detail").build())
            val api=api(server)
            val invalid=api.loadFinancialProviders(owner) as ApiResult.Failure
            val forbidden=api.loadFinancialProviders(owner) as ApiResult.Failure
            assertEquals(ApiFailureKind.MALFORMED_RESPONSE,invalid.kind)
            assertEquals(200,invalid.statusCode)
            assertEquals(ApiFailureKind.MFA_REQUIRED,forbidden.kind)
            assertEquals(403,forbidden.statusCode)
            assertFalse(forbidden.toString().contains("private server detail"))
        }
    }
}
