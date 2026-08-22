package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class BootstrapScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun nativeBootstrapShell_isVisible() {
        composeRule.onNodeWithText("Native Android client").assertIsDisplayed()
        composeRule.onNodeWithText("Kotlin + Jetpack Compose · no WebView").assertIsDisplayed()
    }
}
