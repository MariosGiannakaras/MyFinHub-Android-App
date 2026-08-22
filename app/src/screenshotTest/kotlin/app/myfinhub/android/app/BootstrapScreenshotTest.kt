package app.myfinhub.android.app

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.home.syntheticHomeUiState
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(
    name = "home_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun HomeCompactLightScreenshot() {
    HomeAppScreenshotFixture(darkTheme = false)
}

@PreviewTest
@Preview(
    name = "home_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun HomeCompactLargeFontScreenshot() {
    HomeAppScreenshotFixture(darkTheme = false)
}

@PreviewTest
@Preview(
    name = "home_expanded_dark",
    widthDp = 1280,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
fun HomeExpandedDarkScreenshot() {
    HomeAppScreenshotFixture(darkTheme = true)
}

@Composable
private fun HomeAppScreenshotFixture(darkTheme: Boolean) {
    val navigationEventDispatcherOwner = rememberNavigationEventDispatcherOwner(parent = null)

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides navigationEventDispatcherOwner,
    ) {
        MyFinHubTheme(darkTheme = darkTheme) {
            MyFinHubAppContent(
                homeState = syntheticHomeUiState(),
                onHomeAction = {},
            )
        }
    }
}
