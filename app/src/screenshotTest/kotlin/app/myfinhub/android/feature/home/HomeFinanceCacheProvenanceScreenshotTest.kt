package app.myfinhub.android.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

/** New offline-only status sample; render and visually review before retaining a golden. */
@PreviewTest
@Preview(name = "home_unverified_cache_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun HomeUnverifiedCacheLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        ProductionHomeScreen(
            state = syntheticHomeUiState(),
            onAction = {},
            onOpenAttention = {},
            onOpenQuickEntry = {},
            financeCacheMode = true,
            pendingFinanceChanges = 2,
            amountsVisibleOverride = true,
        )
    }
}

@PreviewTest
@Preview(name = "home_unverified_cache_large_font", widthDp = 412, heightDp = 915, fontScale = 1.5f, showBackground = true)
@Composable
fun HomeUnverifiedCacheLargeFontScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        ProductionHomeScreen(
            state = syntheticHomeUiState(),
            onAction = {},
            onOpenAttention = {},
            onOpenQuickEntry = {},
            financeCacheMode = true,
            pendingFinanceChanges = 2,
            amountsVisibleOverride = true,
        )
    }
}
