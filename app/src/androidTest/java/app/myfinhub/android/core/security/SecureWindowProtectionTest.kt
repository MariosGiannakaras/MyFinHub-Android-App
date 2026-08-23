package app.myfinhub.android.core.security

import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.myfinhub.android.AuthTestActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SecureWindowProtectionTest {
    @get:Rule
    val rule = createAndroidComposeRule<AuthTestActivity>()

    @Test
    fun flagSecure_isScopedToActiveSecretContent() {
        val active = mutableStateOf(true)
        rule.setContent {
            SecureWindowProtection(active = active.value)
        }
        rule.waitForIdle()

        assertTrue(rule.activity.window.hasSecureFlag())

        rule.runOnUiThread { active.value = false }
        rule.waitForIdle()

        assertFalse(rule.activity.window.hasSecureFlag())
    }
}

private fun android.view.Window.hasSecureFlag(): Boolean =
    attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
