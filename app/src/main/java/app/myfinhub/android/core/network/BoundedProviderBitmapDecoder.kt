package app.myfinhub.android.core.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * A second safety boundary after trusted HTTPS bytes: never inflate a remote
 * bank mark to an unbounded bitmap. Caller must supply bytes returned by
 * fetchTrustedProviderRaster, not an arbitrary URL or Base64 data URI.
 */
internal fun decodeBoundedProviderBitmap(
    approvedRasterBytes: ByteArray,
    targetDimensionPx: Int = 512,
): Bitmap? {
    if (approvedRasterBytes.isEmpty() ||
        approvedRasterBytes.size > 2 * 1024 * 1024 ||
        targetDimensionPx !in 16..1024
    ) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(
            approvedRasterBytes, 0, approvedRasterBytes.size, bounds,
        )
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width !in 1..8192 || height !in 1..8192 ||
            width.toLong() * height.toLong() > 16_777_216L
        ) return@runCatching null
        var sample = 1
        while (width / sample > targetDimensionPx ||
            height / sample > targetDimensionPx) {
            sample *= 2
        }
        val settings = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(
            approvedRasterBytes, 0, approvedRasterBytes.size, settings,
        )?.takeIf { it.width <= targetDimensionPx && it.height <= targetDimensionPx }
    }.getOrNull()
}
