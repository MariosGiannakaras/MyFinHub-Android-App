package app.myfinhub.android.feature.receipt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.oned.EAN13Writer
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptQrInstrumentedTest {
    private fun fiscalQr(sequence: String = "00000001"): String =
        "https://www1.aade.gr/tameiakes/myweb/q1.php?SIG=" +
            "TEST0000001" + sequence + "0123456789ABCDEF0123456789ABCD" + "2611261037" + "18.35"

    private fun qrBitmap(payload: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size) { index ->
            if (matrix[index % size, index / size]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    @Test
    fun fullReceiptImageUsesActualQrDecodePlusBundledOcr() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val receipt = Bitmap.createBitmap(1400, 1320, Bitmap.Config.ARGB_8888)
        val qr = qrBitmap(fiscalQr(), 500)
        try {
            val canvas = Canvas(receipt)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 66f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            listOf("TEST STORE", "26/11/2026", "TOTAL 19.35 EUR").forEachIndexed { index, line ->
                canvas.drawText(line, 100f, 130f + index * 130f, paint)
            }
            canvas.drawBitmap(qr, 100f, 640f, null)
            val result = recognizeReceiptBitmap(context, receipt)
            assertEquals(1835L, result.totalCents)
            assertEquals(LocalDate.of(2026, 11, 26), result.date)
            assertEquals(ReceiptQrStatus.AADE, result.qrReview?.status)
            // The synthetic printed OCR total disagrees; QR priority is visible, not silent.
            assertEquals(1935L, result.qrReview?.ocrTotalCents)
            assertTrue(requireNotNull(result.qrReview).requiresConfirmation)
        } finally {
            qr.recycle()
            receipt.recycle()
        }
    }

    @Test
    fun multipleFiscalCodesAreNotSilentlyChosenAndProductBarcodeIsNotAReceipt() {
        val image = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
        val first = qrBitmap(fiscalQr(), 450)
        val second = qrBitmap(fiscalQr("00000002"), 450)
        try {
            val canvas = Canvas(image)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(first, 50f, 75f, null)
            canvas.drawBitmap(second, 700f, 75f, null)
            assertEquals(ReceiptQrStatus.AMBIGUOUS, readReceiptQr(image).status)
        } finally {
            first.recycle()
            second.recycle()
            image.recycle()
        }
        val size = 600
        val height = 180
        val matrix = EAN13Writer().encode("5901234123457", BarcodeFormat.EAN_13, size, height)
        val product = Bitmap.createBitmap(IntArray(size * height) {
            if (matrix[it % size, it / size]) Color.BLACK else Color.WHITE
        }, size, height, Bitmap.Config.ARGB_8888)
        try {
            assertEquals(ReceiptQrStatus.NONE, readReceiptQr(product).status)
        } finally {
            product.recycle()
        }
    }
}
