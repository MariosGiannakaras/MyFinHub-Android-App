package app.myfinhub.android.feature.receipt

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live QR-only mode. CameraX is bound only whilst this authorized dialog exists.
 * No frame or scanned URL is stored or uploaded; a result is still just a draft.
 */
@SuppressLint("MissingPermission") // Checked immediately before binding; host requests on explicit tap.
@Composable
internal fun ReceiptQrScanner(
    onRead: (ReceiptQrRead) -> Unit,
    onFailure: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val readCallback by rememberUpdatedState(onRead)
    val failureCallback by rememberUpdatedState(onFailure)
    var status by remember { mutableStateOf(ReceiptQrStatus.NONE) }
    val view = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    DisposableEffect(context, lifecycleOwner, view) {
        val active = AtomicBoolean(true)
        val delivered = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        var provider: ProcessCameraProvider? = null
        var lastFrame = 0L // accessed only on the single analyzer executor

        fun fail() {
            if (active.get() && delivered.compareAndSet(false, true)) {
                main.execute { if (active.get()) failureCallback() }
            }
        }

        analysis.setAnalyzer(executor) { frame ->
            try {
                val now = SystemClock.elapsedRealtime()
                if (active.get() && !delivered.get() && now - lastFrame >= 300L) {
                    lastFrame = now
                    val result = readReceiptQr(frame)
                    if (result.fiscal != null) {
                        if (delivered.compareAndSet(false, true)) {
                            main.execute { if (active.get()) readCallback(result) }
                        }
                    } else if (result.status != ReceiptQrStatus.NONE) {
                        main.execute { if (active.get() && !delivered.get()) status = result.status }
                    }
                }
            } catch (_: Exception) {
                fail()
            } finally {
                frame.close()
            }
        }
        try {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (active.get()) {
                    try {
                        check(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED)
                        val current = future.get()
                        provider = current
                        current.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    } catch (_: Exception) {
                        fail()
                    }
                }
            }, main)
        } catch (_: Exception) {
            fail()
        }
        onDispose {
            active.set(false)
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            executor.shutdown()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Σάρωση QR απόδειξης") },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AndroidView(
                    factory = { view },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).aspectRatio(1f).testTag("receipt_live_qr_preview"),
                )
                Text(when (status) {
                    ReceiptQrStatus.AMBIGUOUS -> "Εντοπίστηκαν διαφορετικοί κωδικοί. Στόχευσε μόνο μία απόδειξη."
                    ReceiptQrStatus.UNSUPPORTED -> "Ο κωδικός δεν περιέχει υποστηριζόμενα στοιχεία ΑΑΔΕ. Δοκίμασε τη φωτογραφία απόδειξης."
                    else -> "Στόχευσε το τετράγωνο QR Code στο κάτω μέρος της απόδειξης. Η σάρωση γίνεται στη συσκευή."
                })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Πίσω") } },
    )
}
