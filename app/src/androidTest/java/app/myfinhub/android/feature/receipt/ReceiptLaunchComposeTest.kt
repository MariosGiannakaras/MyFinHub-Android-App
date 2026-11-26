package app.myfinhub.android.feature.receipt

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myfinhub.android.AuthTestActivity
import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.auth.AuthShellUiState
import app.myfinhub.android.feature.quickentry.ProductionQuickEntryScreen
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercise the production host's actual registered ActivityResult launchers in a
 * FragmentActivity, not a fake LocalReceiptImportController. Intercept only the
 * external activity boundary; no camera, user photo, or finance data is accessed.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptLaunchComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<AuthTestActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun showHost() {
        val owner = AuthSession("synthetic-access", "synthetic-refresh", Long.MAX_VALUE, "test-owner", AssuranceLevel.AAL2)
        composeRule.setContent {
            MyFinHubTheme {
                ReceiptImportHost(
                    auth = AuthShellUiState.Ready(owner),
                    authorized = true,
                    activityItems = emptyList(),
                    currentOwnerMayEdit = { it == owner.userId },
                ) {
                    ProductionQuickEntryScreen(QuickEntryUiState(), {}, {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun startReceipt(tag: String) {
        composeRule.onNodeWithTag("quick_entry_receipt").performClick()
        composeRule.onNodeWithTag(tag).performClick()
    }

    @Test
    fun cameraActuallyReachesExternalLaunchWithScopedOutputAndCancellationCleansUp() {
        showHost()
        val captured = AtomicReference<Intent?>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != MediaStore.ACTION_IMAGE_CAPTURE) return null
                captured.set(Intent(intent))
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            startReceipt("receipt_camera")
            composeRule.waitForIdle()
            val intent = requireNotNull(captured.get()) { "Camera never reached the Android activity launch" }
            val uri = requireNotNull(intent.clipData?.getItemAt(0)?.uri)
            assertEquals("content", uri.scheme)
            assertEquals(instrumentation.targetContext.packageName + ".receipt", uri.authority)
            assertTrue(uri.path.orEmpty().startsWith("/receipt_camera/receipt-"))
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            assertEquals(flags, intent.flags and flags)
            assertEquals(1, intent.clipData?.itemCount)
            composeRule.onNodeWithTag("receipt_error_title").assertDoesNotExist()
            composeRule.onNodeWithTag("quick_entry_receipt").assertIsDisplayed()
            assertTrue(File(instrumentation.targetContext.cacheDir, "receipt-capture").listFiles().orEmpty().isEmpty())
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun photoPickerActuallyLaunchesAndReturnsImageThroughRealOcrToReview() {
        showHost()
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "receipt-capture").apply { check(isDirectory || mkdirs()) }
        val fixture = File.createTempFile("synthetic-receipt-", ".png", directory)
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
            fixture.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".receipt", fixture)
        val captured = AtomicReference<Intent?>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                val action = intent.action
                if (action !in setOf(MediaStore.ACTION_PICK_IMAGES, Intent.ACTION_OPEN_DOCUMENT,
                        "androidx.activity.result.contract.action.SYSTEM_FALLBACK_PICK_IMAGES")) return null
                captured.set(Intent(intent))
                return Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri))
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            startReceipt("receipt_gallery")
            assertNotNull("Photo picker never reached the Android activity launch", captured.get())
            composeRule.waitUntil(timeoutMillis = 45_000) {
                composeRule.onAllNodesWithTag("receipt_review_amount").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("receipt_review_amount").assertTextContains("12.34")
            composeRule.onNodeWithTag("receipt_error_title").assertDoesNotExist()
        } finally {
            instrumentation.removeMonitor(monitor)
            fixture.delete()
        }
    }
}
