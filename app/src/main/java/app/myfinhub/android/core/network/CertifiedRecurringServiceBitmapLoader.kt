package app.myfinhub.android.core.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.myfinhub.android.BuildConfig
import java.io.ByteArrayOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request

/** No session tokens or cookies ever sent to a PUBLIC image storage origin.
 * Reject redirects, MIME/content spoofing, oversized media and decode bombs.
 * SVG stays as a neutral fallback rather than an untrusted XML renderer.
 */
internal fun loadCertifiedRecurringServiceBitmap(asset: RecurringServiceAssetRecord): Bitmap? {
    val url = trustedRecurringServiceAssetUrl(asset.url,BuildConfig.SUPABASE_URL) ?: return null
    if (asset.mimeType !in setOf("image/png","image/jpeg","image/webp")) return null
    val request=runCatching {
        Request.Builder().url(url)
            .header("Accept",asset.mimeType)
            .header("Cache-Control","no-store")
            .build()
    }.getOrNull() ?: return null
    val client=OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).cache(null).build()
    return runCatching {
        client.newCall(request).execute().use reply@{ reply ->
            if (reply.code != 200) return@reply null
            val body=reply.body ?: return@reply null
            if (body.contentLength() > 2_097_152L) return@reply null
            val output=ByteArrayOutputStream()
            val buffer=ByteArray(8192)
            body.byteStream().use { stream ->
                while (true) {
                    val n=stream.read(buffer)
                    if (n < 0) break
                    if (output.size()+n > 2_097_152) return@reply null
                    output.write(buffer,0,n)
                }
            }
            val bytes=output.toByteArray()
            val certified=TrustedProviderAssetReference(
                asset.assetKey,"logo","universal",asset.url,asset.mimeType,asset.sizeBytes,
            )
            if (!certifiedProviderImageBytesValid(
                    certified,body.contentType()?.toString(),bytes,
                )) return@reply null
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096)
                return@reply null
            val scaled=BitmapFactory.Options().apply {
                var sample=1
                while (maxOf(bounds.outWidth,bounds.outHeight)/sample > 512)
                    sample*=2
                inSampleSize=sample
                inPreferredConfig=Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,scaled)
        }
    }.getOrNull()
}
