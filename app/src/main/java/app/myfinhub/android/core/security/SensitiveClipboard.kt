package app.myfinhub.android.core.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import java.util.UUID

/**
 * Clipboard policy is isolated from Android's focus-restricted ClipboardManager reads.
 * Hosted Android emulator instrumentation cannot always read back primaryClip even
 * immediately after writing; use an in-memory port to test ownership/lifecycle, while
 * production always uses the Android system clipboard port below.
 */
internal interface SensitiveClipboardPort {
    fun copy(marker: String, value: String): Boolean
    fun clearIfUnchanged(marker: String, value: String)
}

/**
 * Expiry runs on the Android main looper, independent of Compose coroutine cancellation.
 * Only the exact unchanged app-owned copy can be removed on expiry, navigation, AAL loss
 * or backgrounding; the clipboard of another app or a newer copy must remain untouched.
 */
internal class SensitiveCardClipboard(
    context: Context,
    private val expiryMillis: Long = 30_000L,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val port: SensitiveClipboardPort = AndroidSensitiveClipboardPort(context.applicationContext),
) {
    private var currentCopy: Pair<String, String>? = null

    fun copy(value: String): Boolean {
        val marker = "MyFinHub:sensitive:${UUID.randomUUID()}"
        if (!port.copy(marker, value)) return false
        currentCopy = marker to value
        if (!handler.postDelayed({
                port.clearIfUnchanged(marker, value)
                if (currentCopy?.first == marker) currentCopy = null
            }, expiryMillis)) {
            clear()
            return false
        }
        return true
    }

    fun clear() {
        val snapshot = currentCopy
        currentCopy = null
        if (snapshot != null) port.clearIfUnchanged(snapshot.first, snapshot.second)
    }
}

private class AndroidSensitiveClipboardPort(
    private val context: Context,
) : SensitiveClipboardPort {
    override fun copy(marker: String, value: String): Boolean = runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(marker, value)
        clip.description.extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            } else {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)
    }.isSuccess

    override fun clearIfUnchanged(marker: String, value: String) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val current = clipboard.primaryClip ?: return
            val unchanged = current.description.label?.toString() == marker &&
                current.itemCount > 0 &&
                current.getItemAt(0).coerceToText(context).toString() == value
            if (!unchanged) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}
