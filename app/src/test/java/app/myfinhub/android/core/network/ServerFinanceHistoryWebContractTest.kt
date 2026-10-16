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

/** Exact Web /api/history GET, owner AAL2 and strictly read-only response parsing. */
class ServerFinanceHistoryWebContractTest {
    private val owner = AuthSession(
        accessToken="synthetic-history-bearer",refreshToken="synthetic",
        expiresAtEpochSeconds=99999,userId="owner",
        assuranceLevel=AssuranceLevel.AAL2,
    )
    private fun config(server: MockWebServer) = AppConfiguration(
        myFinHubApiBaseUrl=server.url("/").toString().removeSuffix("/"),
        supabaseUrl="https://supabase.synthetic.test",supabasePublishableKey="synthetic-publishable",
    )
    private val envelope = """
        {"available":true,"generation":"17","financeRevision":"28","currentPointId":"9",
         "canUndo":true,"canRedo":false,"undoDepth":2,"redoDepth":0,
         "points":[
           {"id":"8","parentId":null,"label":"Αλλαγή budget",
            "createdAt":"2026-10-16T09:00:00Z","current":false},
           {"id":"9","parentId":"8","label":"Συναλλαγή",
            "createdAt":"2026-10-16T10:00:00Z","current":true}
         ]}
    """.trimIndent()

    @Test fun exactBearerGetReturnsSafeSummaryWithoutAnyPostOrFinanceState() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body(envelope).build())
            val result=OkHttpMyFinHubApi(config(server),OkHttpClient()).loadServerFinanceHistory(owner)
            assertTrue(result is ApiResult.Success)
            val request=server.takeRequest()
            assertEquals("GET",request.method)
            assertEquals("/api/history",request.url.encodedPath)
            assertEquals("Bearer synthetic-history-bearer",request.headers["Authorization"])
            assertFalse(request.headers.names().contains("Cookie"))
            val summary=(result as ApiResult.Success<ServerFinanceHistorySummary>).value
            assertEquals(17L,summary.generation.toLong())
            assertEquals(2,summary.undoDepth)
            assertEquals(listOf("8","9"),summary.points.map { it.id })
            assertFalse(summary.toString().contains("Συναλλαγή"))
            assertFalse(summary.points.last().toString().contains("Συναλλαγή"))
            assertEquals(1,server.requestCount)
        }
    }

    @Test fun invalidAalAndMalformedWebResponseFailClosedWithoutCopyingHistory() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client=OkHttpMyFinHubApi(config(server),OkHttpClient())
            val denied=client.loadServerFinanceHistory(owner.copy(assuranceLevel=AssuranceLevel.AAL1))
            assertEquals(ApiFailureKind.MFA_REQUIRED,(denied as ApiResult.Failure).kind)
            assertEquals(0,server.requestCount)
            server.enqueue(MockResponse.Builder().code(200).body(
                """{"available":true,"generation":"2","financeRevision":"3","canUndo":true,
                   "canRedo":false,"undoDepth":0,"redoDepth":0,
                   "points":[{"id":"1","label":"bad","createdAt":"not-a-date","current":true}]}"""
            ).build())
            val broken=client.loadServerFinanceHistory(owner)
            assertEquals(ApiFailureKind.MALFORMED_RESPONSE,(broken as ApiResult.Failure).kind)
            assertEquals(1,server.requestCount)
        }
    }
}
