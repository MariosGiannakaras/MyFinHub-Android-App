package app.myfinhub.android.core.network

import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Public images never reuse the authenticated finance API client. */
class CertifiedPublicAssetHttpClientTest {
    @Test fun certifiedArtworkTransportIsAnonymousWithoutDiskPersistenceOrRedirects() {
        val client=certifiedAssetAnonymousClient
        assertSame(CookieJar.NO_COOKIES,client.cookieJar)
        assertSame(Authenticator.NONE,client.authenticator)
        assertSame(Authenticator.NONE,client.proxyAuthenticator)
        assertNull(client.cache)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertTrue(client.interceptors.isEmpty())
        assertTrue(client.networkInterceptors.isEmpty())
        assertEquals(10_000,client.connectTimeoutMillis)
        assertEquals(10_000,client.readTimeoutMillis)
        assertEquals(15_000,client.callTimeoutMillis)
    }
    @Test fun bothCertifiedImagePathsShareExactlyOneAnonymousTransport() {
        assertSame(certifiedAssetAnonymousClient,certifiedAssetAnonymousClient)
        val request=okhttp3.Request.Builder()
            .url("https://example.invalid/no-real-request.png")
            .header("Accept","image/png")
            .header("Cache-Control","no-store")
            .build()
        assertNull(request.header("Authorization"))
        assertNull(request.header("Cookie"))
        assertEquals("no-store",request.header("Cache-Control"))
    }
}
