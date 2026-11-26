package app.myfinhub.android.feature.receipt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptOcrInstrumentedTest {
    @Test
    fun bundledGreekAndEnglishModelsRecognizeARealSyntheticBitmapOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(1400, 700, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 66f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            listOf("ΔΟΚΙΜΗ STORE", "26/11/2026", "TOTAL 12.34 EUR").forEachIndexed { index, line ->
                canvas.drawText(line, 100f, 130f + index * 130f, paint)
            }
            val proposal = recognizeReceiptBitmap(context, bitmap)
            assertEquals(ReceiptCurrency.EUR, proposal.currency)
            assertEquals(1234L, proposal.totalCents)
        } finally {
            bitmap.recycle()
        }
    }
}
