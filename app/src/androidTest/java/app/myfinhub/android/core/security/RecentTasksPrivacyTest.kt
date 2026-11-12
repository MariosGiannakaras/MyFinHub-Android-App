package app.myfinhub.android.core.security

import android.app.Activity
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentTasksPrivacyTest {
    @Test
    fun api33PlusDisablesTaskSnapshotsBeforeRenderingContent() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val host = RecordingActivity()
        protectRecentTasksSnapshot(host)
        assertEquals(false, host.recentsScreenshotEnabled)
    }

    private class RecordingActivity : Activity() {
        var recentsScreenshotEnabled: Boolean? = null
            private set

        override fun setRecentsScreenshotEnabled(enabled: Boolean) {
            recentsScreenshotEnabled = enabled
        }
    }
}
