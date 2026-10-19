package app.myfinhub.android.core.network

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoundedProviderBitmapDecoderTest {
    private fun png(w: Int, h: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
            bitmap.recycle()
            it.toByteArray()
        }
    }
    @Test fun decodeRealProviderRasterWithinRequestedPixelBudget() {
        val decoded=decodeBoundedProviderBitmap(png(1200,900),128)
        assertNotNull(decoded)
        assertTrue(decoded!!.width <= 128 && decoded.height <= 128)
        decoded.recycle()
    }
    @Test fun rejectsCorruptUnboundedAndOversizedSourceSafely() {
        assertNull(decodeBoundedProviderBitmap(byteArrayOf(1,2,3)))
        assertNull(decodeBoundedProviderBitmap(ByteArray(2_097_153)))
        assertNull(decodeBoundedProviderBitmap(png(10,10),8))
        assertNull(decodeBoundedProviderBitmap(png(10,10),2048))
    }
}
