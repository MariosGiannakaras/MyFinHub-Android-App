package app.myfinhub.android.feature.receipt

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.provider.MediaStore
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.platform.app.InstrumentationRegistry
import app.myfinhub.android.AuthTestActivity
import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.security.PinAttemptStatus
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.auth.AuthShellUiState
import app.myfinhub.android.feature.quickentry.ProductionQuickEntryScreen
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import app.myfinhub.android.feature.bankalert.BankMerchantAliasStore
import app.myfinhub.android.feature.bankalert.BankSmsInboxStore
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDateTime
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
    @get:Rule(order = 0) val cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<AuthTestActivity>()

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
        if (tag == "bank_alert_import") {
            composeRule.onNodeWithTag("bank_sms_inbox").performClick()
            composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        } else composeRule.onNodeWithTag(tag).performClick()
    }

    @Test
    fun userSelectedBankTextReachesReviewAndRequiresExplicitDebitConfirmation() {
        showHost()
        startReceipt("bank_alert_import")
        composeRule.onNodeWithTag("bank_alert_text").performTextReplacement("PIRAEUS OTP 123456")
        composeRule.onNodeWithTag("bank_alert_parse").performClick()
        composeRule.onNodeWithTag("bank_alert_rejected").assertIsDisplayed()
        composeRule.onNodeWithTag("bank_alert_text").performTextReplacement(
            "Πειραιώς; Αγορά; κάρτα ****1234; 26/11/2026 14:35; 4,50 EUR; Έμπορος: TEST COFFEE; Χώρα: GR",
        )
        composeRule.onNodeWithTag("bank_alert_parse").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("bank_alert_summary").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("bank_alert_summary").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("receipt_review_amount").performScrollTo().assertTextContains("4.50")
        composeRule.onNodeWithTag("receipt_review_apply").assertIsNotEnabled()
        composeRule.onNodeWithTag("bank_alert_confirmation").performScrollTo().performClick()
        composeRule.onNodeWithTag("receipt_review_apply").assertIsEnabled()
    }

    @Test
    fun localMerchantNicknameIsSavedExplicitlyAndPrefillsTheNextObservedSms() {
        val store = BankMerchantAliasStore(instrumentation.targetContext)
        val descriptor = "SYNTHETIC ALIAS SHOP"
        val message = """
            ALERT-ΑΓΟΡΑ ΜΕ ΚΑΡΤΑ
            18/10/26 08:17
            ΛΟΓ/ΜΟΣ:TEST ACCOUNT
            ΠΟΣΟ=-13 EUR (ΧΡ)
            ΛΟΓ.ΥΠΟΛ=111.22
            ΔΙΑΘ.ΥΠΟΛ=100.50
            $descriptor
        """.trimIndent()
        runBlocking { store.remove("test-owner", descriptor) }
        try {
            showHost()
            fun paste() {
                startReceipt("bank_alert_import")
                composeRule.onNodeWithTag("bank_alert_text").performTextReplacement(message)
                composeRule.onNodeWithTag("bank_alert_parse").performClick()
                composeRule.waitUntil(timeoutMillis = 10_000) {
                    composeRule.onAllNodesWithTag("receipt_review_merchant").fetchSemanticsNodes().isNotEmpty()
                }
            }
            paste()
            composeRule.onNodeWithTag("receipt_review_merchant").performScrollTo().assertTextContains(descriptor)
                .performTextReplacement("My example store")
            composeRule.onNodeWithTag("bank_alert_save_alias").performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("bank_alert_alias_status").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("bank_alert_alias_status").performScrollTo().assertTextContains("Αποθηκεύτηκε μόνο στη συσκευή.")
            composeRule.onNodeWithTag("receipt_review_apply").assertIsNotEnabled()
            composeRule.onNodeWithTag("receipt_review_cancel").performClick()
            paste()
            composeRule.onNodeWithTag("receipt_review_merchant").performScrollTo().assertTextContains("My example store")
            composeRule.onNodeWithTag("receipt_review_apply").assertIsNotEnabled()
        } finally { runBlocking { store.remove("test-owner", descriptor) } }
    }

    @Test
    fun directAliasDirectorySavesAndReopensDeviceOnlyNickname() {
        val store = BankMerchantAliasStore(instrumentation.targetContext)
        val descriptor = "SYNTHETIC DIRECTORY SHOP"
        val nickname = "My test nickname"
        runBlocking { store.remove("test-owner", descriptor) }
        try {
            showHost()
            startReceipt("bank_alert_aliases")
            composeRule.onNodeWithTag("bank_alias_original").performScrollTo().performTextReplacement(descriptor)
            composeRule.onNodeWithTag("bank_alias_nickname").performScrollTo().performTextReplacement(nickname)
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runCatching { composeRule.onNodeWithTag("bank_alias_save").assertIsEnabled() }.isSuccess
            }
            composeRule.onNodeWithTag("bank_alias_save").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("bank_alias_status").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("bank_alias_status").performScrollTo().assertTextContains("Αποθηκεύτηκε μόνο στη συσκευή.")
            composeRule.onNodeWithText("Κλείσιμο").performClick()
            composeRule.onNodeWithTag("bank_alert_aliases").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runCatching { composeRule.onNodeWithText(nickname).assertExists() }.isSuccess
            }
            composeRule.onNodeWithText(nickname).performScrollTo().assertIsDisplayed()
            assertEquals(nickname, runBlocking { store.list("test-owner") }.single { it.bankName == descriptor }.nickname)
        } finally { runBlocking { store.remove("test-owner", descriptor) } }
    }

    @Test
    fun automaticInboxOpensAfterNotificationAndCancellationKeepsPending() {
        val store = BankSmsInboxStore(instrumentation.targetContext)
        val owner = AuthSession("synthetic-access", "synthetic-refresh", Long.MAX_VALUE, "sms-test-owner", AssuranceLevel.AAL2)
        val message = """
            ALERT-ΑΓΟΡΑ ΜΕ ΚΑΡΤΑ
            18/10/26 08:17
            ΛΟΓ/ΜΟΣ:TEST ACCOUNT
            ΠΟΣΟ=-13 EUR (ΧΡ)
            ΛΟΓ.ΥΠΟΛ=111.22
            ΔΙΑΘ.ΥΠΟΛ=100.50
            SYNTHETIC SMS STORE
        """.trimIndent()
        val auth = mutableStateOf<AuthShellUiState>(AuthShellUiState.Locked(owner, false, PinAttemptStatus(true, 5)))
        val requested = mutableStateOf(true)
        store.enable(owner.userId)
        assertTrue(store.receive(requireNotNull(store.captureConsent()), "Piraeus", System.currentTimeMillis(), message))
        val row = store.list(owner.userId).first()
        try {
            composeRule.setContent {
                MyFinHubTheme {
                    ReceiptImportHost(auth.value, auth.value is AuthShellUiState.Ready, emptyList(),
                        { it == owner.userId && auth.value is AuthShellUiState.Ready }, bankInboxRequested = requested.value,
                        onBankInboxOpened = { requested.value = false }) {
                        ProductionQuickEntryScreen(QuickEntryUiState(), {}, {})
                    }
                }
            }
            composeRule.onNodeWithTag("bank_sms_capture_status").assertDoesNotExist()
            composeRule.runOnIdle { auth.value = AuthShellUiState.Ready(owner) }
            val tag = "bank_sms_review_${row.id}"
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(tag).performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("bank_alert_summary").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("receipt_review_amount").performScrollTo().assertTextContains("13.00")
            composeRule.onNodeWithTag("receipt_review_apply").assertIsNotEnabled()
            composeRule.onNodeWithTag("receipt_review_cancel").performClick()
            assertTrue(store.list(owner.userId).any { it.id == row.id })
        } finally { store.discard(owner.userId, row.id); store.disable() }
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

    @Test
    fun qrScannerCannotPublishAfterLockAndNewRequestIsIndependent() {
        val owner = AuthSession("synthetic-access", "synthetic-refresh", Long.MAX_VALUE, "test-owner", AssuranceLevel.AAL2)
        val auth = mutableStateOf<AuthShellUiState>(AuthShellUiState.Ready(owner))
        val callback = AtomicReference<((ReceiptQrRead) -> Unit)?>(null)
        val result = ReceiptQrRead(ReceiptQrStatus.AADE,
            FiscalReceiptQr(1835, LocalDateTime.of(2026, 11, 26, 10, 37), "synthetic"))
        composeRule.setContent {
            MyFinHubTheme {
                ReceiptImportHost(
                    auth = auth.value,
                    authorized = auth.value is AuthShellUiState.Ready,
                    activityItems = emptyList(),
                    currentOwnerMayEdit = { it == owner.userId && auth.value is AuthShellUiState.Ready },
                    // Camera hardware is tested separately; this test exercises the production
                    // request/authority/review boundary using a deterministic decoded result.
                    qrScanner = { read, _, _ ->
                        callback.set(read)
                        Text("Scanner", Modifier.testTag("synthetic_live_scanner"))
                    },
                ) {
                    ProductionQuickEntryScreen(QuickEntryUiState(), {}, {})
                }
            }
        }
        startReceipt("receipt_scan_qr")
        composeRule.onNodeWithTag("synthetic_live_scanner").assertIsDisplayed()
        val stale = requireNotNull(callback.get())
        composeRule.runOnIdle {
            auth.value = AuthShellUiState.Locked(owner, false, PinAttemptStatus(true, 5))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("synthetic_live_scanner").assertDoesNotExist()
        composeRule.runOnIdle { stale(result) }
        composeRule.runOnIdle { auth.value = AuthShellUiState.Ready(owner) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("receipt_qr_source").assertDoesNotExist()
        startReceipt("receipt_scan_qr")
        composeRule.onNodeWithTag("synthetic_live_scanner").assertIsDisplayed()
        composeRule.runOnIdle { stale(result) }
        composeRule.onNodeWithTag("receipt_qr_source").assertDoesNotExist()
        composeRule.runOnIdle { requireNotNull(callback.get())(result) }
        composeRule.onNodeWithTag("receipt_review_amount").assertTextContains("18.35")
    }
}
