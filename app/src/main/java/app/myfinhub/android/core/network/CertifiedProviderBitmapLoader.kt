package app.myfinhub.android.core.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.myfinhub.android.BuildConfig
import java.io.ByteArrayOutputStream
import okhttp3.Request

/** Anonymous GET to an already owner-certified PUBLIC provider asset.
 * No bearer token, cookies, redirects, disk cache or unbounded downloads.
 * Invalid replies return null and retain the neutral fallback mark.
 */
internal fun loadCertifiedProviderBitmap(
    providerId: String,
    asset: TrustedProviderAssetReference,
): Bitmap? {
    val url=trustedProviderAssetUrl(asset.url,providerId,BuildConfig.SUPABASE_URL)
        ?: return null
    if (asset.mimeType !in setOf("image/png","image/jpeg","image/webp")) return null
    val request=runCatching {
        Request.Builder().url(url)
            .header("Accept",asset.mimeType)
            .header("Cache-Control","no-store")
            .build()
    }.getOrNull() ?: return null
    return runCatching {
        certifiedAssetAnonymousClient.newCall(request).execute().use responseBlock@ { response ->
            if (response.code != 200) return@responseBlock null
            val body=response.body ?: return@responseBlock null
            if (body.contentLength() > 2_097_152L) return@responseBlock null
            val buffer=ByteArray(8192)
            val output=ByteArrayOutputStream()
            body.byteStream().use { input ->
                while (true) {
                    val n=input.read(buffer)
                    if (n < 0) break
                    if (output.size()+n > 2_097_152) return@responseBlock null
                    output.write(buffer,0,n)
                }
            }
            val raw=output.toByteArray()
            if (!certifiedProviderImageBytesValid(asset,body.contentType()?.toString(),raw))
                return@responseBlock null
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(raw,0,raw.size,bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                bounds.outWidth > 4096 || bounds.outHeight > 4096) return@responseBlock null
            val sample=BitmapFactory.Options().apply {
                var scale=1
                while (maxOf(bounds.outWidth,bounds.outHeight)/scale > 512) scale*=2
                inSampleSize=scale
                inPreferredConfig=Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(raw,0,raw.size,sample)
        }
    }.getOrNull()
}
