package app.myfinhub.android.core.update

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateUpdateClientTest {
    private val session = AuthSession(
        accessToken = "owner-aal2-token",
        refreshToken = "refresh",
        expiresAtEpochSeconds = 99_999,
        userId = "owner",
        assuranceLevel = AssuranceLevel.AAL2,
    )

    @Test
    fun noRelease_mapsToUpToDate_andUsesBearerWithoutCookie() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("""{"available":false}""").build())
            val client = PrivateUpdateClient(configuration(apiBaseUrl = serverBase(server)), OkHttpClient())

            val result = client.check(session)
            val request = server.takeRequest()

            assertEquals(UpdateCheckResult.UpToDate, result)
            assertEquals("/api/android-update", request.url.encodedPath)
            assertEquals("Bearer owner-aal2-token", request.headers["Authorization"])
            assertFalse(request.headers.names().contains("Cookie"))
        }
    }

    @Test
    fun newerValidRelease_mapsToAvailable() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body(envelope(versionCode = 2)).build())
            val client = PrivateUpdateClient(configuration(apiBaseUrl = serverBase(server)), OkHttpClient())

            val result = client.check(session)

            assertTrue(result is UpdateCheckResult.Available)
            assertEquals(2L, (result as UpdateCheckResult.Available).release.versionCode)
        }
    }

    @Test
    fun currentOrOlderRelease_neverOffersDowngrade() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body(envelope(versionCode = 1)).build())
            val client = PrivateUpdateClient(configuration(apiBaseUrl = serverBase(server)), OkHttpClient())

            assertEquals(UpdateCheckResult.UpToDate, client.check(session))
        }
    }

    @Test
    fun malformedMetadata_failsClosed() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder().code(200).body(envelope(versionCode = 2, sha256 = "not-a-digest")).build(),
            )
            val client = PrivateUpdateClient(configuration(apiBaseUrl = serverBase(server)), OkHttpClient())

            val result = client.check(session)

            assertTrue(result is UpdateCheckResult.Failure)
            assertEquals(UpdateFailureKind.MALFORMED_METADATA, (result as UpdateCheckResult.Failure).kind)
        }
    }

    @Test
    fun aal1Session_isRejectedBeforeNetwork() = runBlocking {
        val aal1 = session.copy(assuranceLevel = AssuranceLevel.AAL1)
        val client = PrivateUpdateClient(configuration(apiBaseUrl = "https://api.example.test"), OkHttpClient())

        val result = client.check(aal1)

        assertTrue(result is UpdateCheckResult.Failure)
        assertEquals(UpdateFailureKind.MFA_REQUIRED, (result as UpdateCheckResult.Failure).kind)
    }

    @Test
    fun serverFailure_isRetryable() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(503).body("{}").build())
            val client = PrivateUpdateClient(configuration(apiBaseUrl = serverBase(server)), OkHttpClient())

            val result = client.check(session) as UpdateCheckResult.Failure

            assertEquals(UpdateFailureKind.SERVER, result.kind)
            assertTrue(result.retryable)
        }
    }

    @Test
    fun downloadStreamsPrivateApkWithBearerAndApiKey_andValidatesDigest() = runBlocking {
        val bytes = "synthetic-apk-payload".toByteArray()
        val requestSeen = AtomicReference<Request>()
        val http = responseClient(bytes, requestSeen)
        val release = release(sizeBytes = bytes.size.toLong(), sha256 = sha256(bytes))
        val directory = tempDirectory()
        val progress = mutableListOf<Float>()
        try {
            val client = PrivateUpdateClient(configuration(), http)

            val result = client.download(session, release, directory, progress::add)

            assertTrue(result is UpdateDownloadResult.Success)
            val file = (result as UpdateDownloadResult.Success).file
            assertEquals(bytes.toList(), file.readBytes().toList())
            assertTrue(progress.isNotEmpty())
            assertEquals(1f, progress.last(), 0.0001f)
            assertEquals("Bearer owner-aal2-token", requestSeen.get().header("Authorization"))
            assertEquals("public-test-key", requestSeen.get().header("apikey"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun digestMismatchDeletesPartialDownload() = runBlocking {
        val bytes = "tampered-apk".toByteArray()
        val directory = tempDirectory()
        try {
            val client = PrivateUpdateClient(configuration(), responseClient(bytes))
            val release = release(sizeBytes = bytes.size.toLong(), sha256 = "a".repeat(64))

            val result = client.download(session, release, directory) {}

            assertTrue(result is UpdateDownloadResult.Failure)
            assertEquals(UpdateFailureKind.DOWNLOAD_DIGEST_MISMATCH, (result as UpdateDownloadResult.Failure).kind)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun wrongStorageHost_isRejectedWithoutIssuingRequest() = runBlocking {
        val calls = AtomicReference(0)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls.set(calls.get() + 1)
            syntheticResponse(chain.request(), "unexpected".toByteArray())
        }.build()
        val release = release(downloadUrl = "https://attacker.example/android-releases/MyFinHub.apk")
        val directory = tempDirectory()
        try {
            val result = PrivateUpdateClient(configuration(), http).download(session, release, directory) {}

            assertTrue(result is UpdateDownloadResult.Failure)
            assertEquals(UpdateFailureKind.INSECURE_DOWNLOAD, (result as UpdateDownloadResult.Failure).kind)
            assertEquals(0, calls.get())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun configuration(
        apiBaseUrl: String = "https://api.example.test",
    ) = AppConfiguration(
        myFinHubApiBaseUrl = apiBaseUrl,
        supabaseUrl = "https://storage.example.test",
        supabasePublishableKey = "public-test-key",
    )

    private fun release(
        versionCode: Long = 2,
        downloadUrl: String = "https://storage.example.test/storage/v1/object/authenticated/android-releases/0.2.0/MyFinHub.apk",
        sizeBytes: Long = 20,
        sha256: String = "a".repeat(64),
    ) = UpdateRelease(
        versionCode = versionCode,
        versionName = "0.2.0",
        downloadUrl = downloadUrl,
        sha256 = sha256,
        sizeBytes = sizeBytes,
        mandatory = false,
        notes = "Private update",
        publishedAt = "2026-09-03T12:00:00Z",
    )

    private fun envelope(versionCode: Long, sha256: String = "a".repeat(64)) = """{
      "available":true,
      "release":{
        "versionCode":$versionCode,
        "versionName":"0.2.0",
        "downloadUrl":"https://storage.example.test/storage/v1/object/authenticated/android-releases/0.2.0/MyFinHub.apk",
        "sha256":"$sha256",
        "sizeBytes":1024,
        "mandatory":false,
        "notes":"Private update",
        "publishedAt":"2026-09-03T12:00:00Z"
      }
    }""".trimIndent()

    private fun responseClient(
        bytes: ByteArray,
        requestSeen: AtomicReference<Request>? = null,
    ): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        requestSeen?.set(chain.request())
        syntheticResponse(chain.request(), bytes)
    }.build()

    private fun syntheticResponse(request: Request, bytes: ByteArray): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(bytes.toResponseBody("application/vnd.android.package-archive".toMediaType()))
        .build()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun serverBase(server: MockWebServer): String = server.url("/").toString().removeSuffix("/")

    private fun tempDirectory(): File = kotlin.io.path.createTempDirectory("myfinhub-update-test").toFile()
}
