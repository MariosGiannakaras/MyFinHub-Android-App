package app.myfinhub.android.core.network

import java.io.ByteArrayOutputStream
import java.util.Locale
import okhttp3.CacheControl
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request

private const val MAX_PROVIDER_RASTER_BYTES = 2 * 1024 * 1024
private val rasterMimeTypes = setOf("image/png", "image/jpeg", "image/webp")

/** Public, owner-verified Supabase images only; never attach an owner bearer token. */
private val isolatedProviderAssetHttpClient = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .cookieJar(CookieJar.NO_COOKIES)
    .cache(null)
    .build()

/**
 * A fail-closed bounded transport, separate from Compose or bitmap decoding.
 * This API accepts only #265 origin-verified assets, revalidates the namespace
 * at the network boundary, and supports raster formats only (SVG deferred).
 *
 * Never log the URL, response body, auth headers or image contents.
 */
internal fun fetchTrustedProviderRaster(
    asset: TrustedProviderAssetReference,
    providerId: String,
    trustedSupabaseOrigin: String,
    client: OkHttpClient = isolatedProviderAssetHttpClient,
): ByteArray? {
    if (asset.mimeType !in rasterMimeTypes ||
        asset.sizeBytes != null && asset.sizeBytes !in 0..MAX_PROVIDER_RASTER_BYTES.toLong() ||
        trustedProviderAssetUrl(asset.url, providerId, trustedSupabaseOrigin) == null ||
        client.followRedirects || client.followSslRedirects ||
        client.cache != null || client.cookieJar != CookieJar.NO_COOKIES
    ) return null
    val request = Request.Builder()
        .url(asset.url)
        .get()
        .cacheControl(CacheControl.FORCE_NETWORK)
        .header("Accept", asset.mimeType)
        .build()
    return runCatching {
        client.newCall(request).execute().use { response ->
            if (response.code != 200 || response.isRedirect) return@use null
            val body = response.body ?: return@use null
            val contentType = body.contentType()?.toString()
                ?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
            if (contentType != asset.mimeType) return@use null
            val declaredLength = body.contentLength()
            if (declaredLength > MAX_PROVIDER_RASTER_BYTES) return@use null
            val buffer = ByteArray(8 * 1024)
            val data = ByteArrayOutputStream()
            body.byteStream().use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (data.size() > MAX_PROVIDER_RASTER_BYTES - read) return@use null
                    data.write(buffer, 0, read)
                }
            }
            val bytes = data.toByteArray()
            val verifiedSignature = when (asset.mimeType) {
                "image/png" -> bytes.size >= 8 &&
                    bytes.copyOfRange(0, 8).contentEquals(
                        byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
                    )
                "image/jpeg" -> bytes.size >= 3 &&
                    bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
                    bytes[2] == 0xff.toByte()
                "image/webp" -> bytes.size >= 12 &&
                    String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                    String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
                else -> false
            }
            bytes.takeIf { verifiedSignature }
        }
    }.getOrNull()
}
