package app.myfinhub.android.feature.money

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/** Resolve a themed Compose ContextWrapper back to its host Activity. */
internal tailrec fun Context.findSecureCardHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findSecureCardHostActivity()
    else -> null
}

/** Shared, main-thread-only reference-counted FLAG_SECURE window leases. */
internal object SensitiveCardWindowProtection {
    private data class Lease(val originallySecure: Boolean, var holders: Int = 0)
    private val active = WeakHashMap<Window, Lease>()

    fun acquire(window: Window): () -> Unit {
        val lease = active[window] ?: Lease(
            originallySecure = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
        ).also { active[window] = it }
        lease.holders += 1
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        var closed = false
        return {
            if (!closed) {
                closed = true
                lease.holders -= 1
                if (lease.holders == 0) {
                    active.remove(window)
                    if (!lease.originallySecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }
}

/** Screen capture is blocked only while full owner+AAL2 card secrets are visible. */
@Composable
internal fun ProtectSensitiveCardWindow(revealed: Boolean) {
    val context = LocalContext.current
    val window = remember(context) { context.findSecureCardHostActivity()?.window }
    DisposableEffect(window, revealed) {
        val release = if (revealed && window != null) {
            SensitiveCardWindowProtection.acquire(window)
        } else null
        onDispose { release?.invoke() }
    }
}
