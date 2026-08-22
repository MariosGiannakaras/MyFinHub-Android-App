package app.myfinhub.android.app

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(
    name = "bootstrap_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun BootstrapCompactLightScreenshot() {
    BootstrapScreenshotFixture(darkTheme = false)
}

@PreviewTest
@Preview(
    name = "bootstrap_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun BootstrapCompactLargeFontScreenshot() {
    BootstrapScreenshotFixture(darkTheme = false)
}

@PreviewTest
@Preview(
    name = "bootstrap_expanded_dark",
    widthDp = 1280,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
fun BootstrapExpandedDarkScreenshot() {
    BootstrapScreenshotFixture(darkTheme = true)
}

@Composable
private fun BootstrapScreenshotFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        Surface {
            BootstrapContent(
                state = BootstrapUiState(),
                contentPadding = PaddingValues(0.dp),
                onAcknowledge = {},
            )
        }
    }
}
