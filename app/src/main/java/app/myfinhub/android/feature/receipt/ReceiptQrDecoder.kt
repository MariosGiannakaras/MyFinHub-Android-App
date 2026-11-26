package app.myfinhub.android.feature.receipt

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import java.nio.ByteBuffer

private const val MAX_QR_PIXELS = 4_000_000

/** Only QR symbols are decoded, not EAN/UPC product barcodes. No URL is executed. */
private fun decodeReceiptQrs(source: LuminanceSource): ReceiptQrRead {
    val hints = mapOf(DecodeHintType.TRY_HARDER to true)
    val payloads = mutableListOf<String>()
    for (candidate in listOf(source, source.invert())) {
        val results = try {
            QRCodeMultiReader().decodeMultiple(BinaryBitmap(HybridBinarizer(candidate)), hints)
        } catch (_: NotFoundException) {
            emptyArray()
        }
        results.forEach { payloads += it.text }
    }
    return classifyReceiptQrs(payloads)
}

internal fun readReceiptQr(bitmap: Bitmap): ReceiptQrRead {
    val width = bitmap.width
    val height = bitmap.height
    require(width > 0 && height > 0 && width.toLong() * height <= MAX_QR_PIXELS)
    val pixels = IntArray(width * height)
    return try {
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        decodeReceiptQrs(RGBLuminanceSource(width, height, pixels))
    } finally {
        pixels.fill(0)
    }
}

/** Copy only the cropped luma plane, honoring row/pixel strides and buffer offset. */
internal fun readReceiptQr(frame: ImageProxy): ReceiptQrRead {
    val crop = frame.cropRect
    val width = crop.width()
    val height = crop.height()
    if (width <= 0 || height <= 0 || width.toLong() * height > MAX_QR_PIXELS ||
        crop.left < 0 || crop.top < 0 || crop.right > frame.width || crop.bottom > frame.height) {
        return ReceiptQrRead()
    }
    val plane = frame.planes.firstOrNull() ?: return ReceiptQrRead()
    return readReceiptQrLuma(plane.buffer, plane.rowStride, plane.pixelStride, crop.left, crop.top, width, height)
}

internal fun readReceiptQrLuma(
    bytes: ByteBuffer,
    rowStride: Int,
    pixelStride: Int,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
): ReceiptQrRead {
    val base = bytes.position().toLong()
    if (rowStride <= 0 || pixelStride <= 0 || left < 0 || top < 0 ||
        width <= 0 || height <= 0 || width.toLong() * height > MAX_QR_PIXELS) return ReceiptQrRead()
    if ((left.toLong() + width - 1) * pixelStride >= rowStride) return ReceiptQrRead()
    val last = base + (top.toLong() + height - 1) * rowStride + (left.toLong() + width - 1) * pixelStride
    if (last >= bytes.limit()) return ReceiptQrRead()
    val luma = ByteArray(width * height)
    return try {
        for (row in 0 until height) {
            val offset = base + (top.toLong() + row) * rowStride + left.toLong() * pixelStride
            for (column in 0 until width) {
                luma[row * width + column] = bytes.get((offset + column.toLong() * pixelStride).toInt())
            }
        }
        decodeReceiptQrs(PlanarYUVLuminanceSource(luma, width, height, 0, 0, width, height, false))
    } finally {
        luma.fill(0)
    }
}
