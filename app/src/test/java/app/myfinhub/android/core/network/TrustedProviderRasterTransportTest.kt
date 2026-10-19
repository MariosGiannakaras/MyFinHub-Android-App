package app.myfinhub.android.core.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** No actual remote requests; assertions exercise the network boundary. */
class TrustedProviderRasterTransportTest {
    private val origin = "https://owner-instance.supabase.co"
    private val url = "$origin/storage/v1/object/public/financial-provider-assets/providers/alpha/logo.png?v=2026-10-19"
    private val png = byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,1,2,3)
    private fun asset(mime: String = "image/png", link: String=url, length: Long?=128) =
        TrustedProviderAssetReference("logo","logo","light",link,mime,length)
    private fun client(code: Int=200, bytes: ByteArray=png, mime: String="image/png",
                       redirect: String?=null): OkHttpClient =
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .addInterceptor(Interceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("Synthetic")
                    .apply { if(redirect!=null)header("Location",redirect) }
                    .body(bytes.toResponseBody(mime.toMediaType()))
                    .build()
            }).build()

    @Test fun acceptsOnlyBoundedRasterWithExactTrustedProviderOrigin() {
        assertArrayEquals(png,fetchTrustedProviderRaster(asset(),"alpha",origin,client()))
    }
    @Test fun foreignHostAndWrongProviderNamespaceFailBeforeTransport() {
        assertNull(fetchTrustedProviderRaster(
            asset(link=url.replace(origin,"https://evil.example")),"alpha",origin,client()))
        assertNull(fetchTrustedProviderRaster(asset(),"other-provider",origin,client()))
    }
    @Test fun redirectsAreNeverFollowedIncludingLocationOnTrustedHost() {
        assertNull(fetchTrustedProviderRaster(asset(),"alpha",origin,
            client(302,redirect=url)))
        assertNull(fetchTrustedProviderRaster(asset(),"alpha",origin,
            client(307,redirect="https://evil.example/logo.png")))
    }
    @Test fun rejectsDeclaredOrActualOversizedContent() {
        assertNull(fetchTrustedProviderRaster(
            asset(length=2_097_153),"alpha",origin,client()))
        val oversized=ByteArray(2_097_153).apply { png.copyInto(this) }
        assertNull(fetchTrustedProviderRaster(
            asset(length=null),"alpha",origin,client(bytes=oversized)))
    }
    @Test fun rejectsMimeMismatchNonImageContentAndSvgEvenWithRasterBytes() {
        assertNull(fetchTrustedProviderRaster(asset(),"alpha",origin,
            client(mime="text/html")))
        assertNull(fetchTrustedProviderRaster(asset(),"alpha",origin,
            client(bytes="not an image".toByteArray())))
        assertNull(fetchTrustedProviderRaster(asset(mime="image/svg+xml"),"alpha",
            origin,client()))
    }
    @Test fun rejectsClientsThatCanRedirectOrStoreCookiesAndCredentials() {
        val redirecting=client().newBuilder().followRedirects(true).build()
        assertNull(fetchTrustedProviderRaster(asset(),"alpha",origin,redirecting))
        val badCookieClient=client().newBuilder().cookieJar(object : okhttp3.CookieJar {
            override fun loadForRequest(url: okhttp3.HttpUrl) = emptyList<okhttp3.Cookie>()
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {}
        }).build()
        assertNull(fetchTrustedProviderRaster(asset(),"alpha",origin,badCookieClient))
        assertTrue(fetchTrustedProviderRaster(asset(),"alpha",origin,client())!!.isNotEmpty())
    }
}
