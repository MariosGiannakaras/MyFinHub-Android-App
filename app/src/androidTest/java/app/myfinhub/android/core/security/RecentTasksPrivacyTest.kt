package app.myfinhub.android.core.security

import android.app.Activity
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentTasksPrivacyTest {
    @Test
    fun api33PlusDisablesTaskSnapshotsBeforeRenderingContent() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        // Android Activity constructs an internal Handler: instantiate on
        // the UI looper, not on the instrumentation runner's worker thread.
        // Override the platform call to assert the exact API33+ contract.
        var enabled: Boolean? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val host = RecordingActivity()
            protectRecentTasksSnapshot(host)
            enabled = host.recentsScreenshotEnabled
        }
        assertEquals(false, enabled)
    }

    private class RecordingActivity : Activity() {
        var recentsScreenshotEnabled: Boolean? = null
            private set

        override fun setRecentsScreenshotEnabled(enabled: Boolean) {
            recentsScreenshotEnabled = enabled
        }
    }
}
