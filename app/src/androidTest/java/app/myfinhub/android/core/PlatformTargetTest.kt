package app.myfinhub.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformTargetTest {
    @Test
    fun installedApplicationTargetsAndroid17() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(37, app.applicationInfo.targetSdkVersion)
    }
}
