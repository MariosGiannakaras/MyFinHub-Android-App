package app.myfinhub.android.core.auth

import app.myfinhub.android.core.config.AppConfiguration
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAuthGatewayTest {
    @Test
    fun listFactors_readsFactorsFromAuthenticatedUserEndpoint() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body(
                        """{
                          "id":"owner",
                          "factors":[
                            {
                              "id":"factor-totp-1",
                              "factor_type":"totp",
                              "status":"verified",
                              "friendly_name":"Owner authenticator"
                            },
                            {
                              "id":"factor-phone-1",
                              "factor_type":"phone",
                              "status":"unverified"
                            }
                          ]
                        }""".trimIndent(),
                    )
                    .build(),
            )
            val gateway = SupabaseAuthGateway(configuration(server), OkHttpClient())

            val result = gateway.listFactors("synthetic-access-token")
            val request = server.takeRequest()

            assertTrue(result is AuthResult.Success)
            val factors = (result as AuthResult.Success<List<AuthFactor>>).value
            assertEquals(2, factors.size)
            assertEquals("factor-totp-1", factors[0].id)
            assertTrue(factors[0].isVerifiedTotp)
            assertEquals("Owner authenticator", factors[0].friendlyName)
            assertEquals("GET", request.method)
            assertEquals("/auth/v1/user", request.url.encodedPath)
            assertEquals("Bearer synthetic-access-token", request.headers["Authorization"])
            assertEquals("synthetic-publishable-key", request.headers["apikey"])
        }
    }

    @Test
    fun listFactors_doesNotTreatMissingFactorsAsServerFailure() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"id":"owner"}""")
                    .build(),
            )
            val gateway = SupabaseAuthGateway(configuration(server), OkHttpClient())

            val result = gateway.listFactors("synthetic-access-token")

            assertTrue(result is AuthResult.Success)
            assertTrue((result as AuthResult.Success<List<AuthFactor>>).value.isEmpty())
        }
    }

    private fun configuration(server: MockWebServer): AppConfiguration {
        val baseUrl = server.url("/").toString().removeSuffix("/")
        return AppConfiguration(
            myFinHubApiBaseUrl = baseUrl,
            supabaseUrl = baseUrl,
            supabasePublishableKey = "synthetic-publishable-key",
        )
    }
}
