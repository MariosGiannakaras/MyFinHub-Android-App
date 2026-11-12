package app.myfinhub.android.core.security

import android.app.Activity
import android.os.Build
import android.view.WindowManager

/**
 * Disable Recents thumbnails before the first sensitive frame can be composed.
 * Android 13+ keeps user-initiated screenshots of non-secret views available;
 * the separate card-vault FLAG_SECURE leases remain in force while revealed.
 */
internal fun protectRecentTasksSnapshot(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activity.setRecentsScreenshotEnabled(false)
    } else {
        // No per-task snapshot API before 33. Prefer privacy to screenshots.
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
