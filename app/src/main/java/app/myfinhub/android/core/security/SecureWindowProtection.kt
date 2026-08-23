package app.myfinhub.android.core.security

import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/** Applies screenshot/non-secure-display protection only while sensitive card values are visible. */
@Composable
fun SecureWindowProtection(active: Boolean) {
    val activity = LocalActivity.current

    DisposableEffect(activity, active) {
        if (!active || activity == null) {
            return@DisposableEffect onDispose { }
        }

        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(false)
        }

        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.setRecentsScreenshotEnabled(true)
            }
        }
    }
}
