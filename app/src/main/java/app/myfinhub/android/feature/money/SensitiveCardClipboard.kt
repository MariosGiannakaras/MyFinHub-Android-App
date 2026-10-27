package app.myfinhub.android.feature.money

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
 * Owns short-lived sensitive clipboard copies independently of a Compose screen coroutine.
 * A screen close, AAL2 loss or Activity ON_STOP clears immediately; expiry still runs if the
 * composable is disposed before the deadline. Other apps' clipboard updates are never cleared.
 */
internal class SensitiveCardClipboard(
    context: Context,
    private val expiryMillis: Long = 30_000L,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private val context = context.applicationContext
    private var currentCopy: Pair<String, String>? = null

    fun copy(value: String): Boolean {
        val marker = "MyFinHub:sensitive:${UUID.randomUUID()}"
        if (!setSensitiveClipboard(context, marker, value)) return false
        currentCopy = marker to value
        if (!handler.postDelayed({
                clearClipboardOnlyIfUnchanged(context, marker, value)
                if (currentCopy?.first == marker) currentCopy = null
            }, expiryMillis)
        ) {
            clear()
            return false
        }
        return true
    }

    fun clear() {
        val snapshot = currentCopy
        currentCopy = null
        if (snapshot != null) clearClipboardOnlyIfUnchanged(context, snapshot.first, snapshot.second)
    }
}

private fun setSensitiveClipboard(context: Context, marker: String, value: String): Boolean = runCatching {
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

private fun clearClipboardOnlyIfUnchanged(context: Context, marker: String, value: String) {
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
