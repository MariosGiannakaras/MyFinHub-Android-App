package app.myfinhub.android.feature.quickentry

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "production_quick_entry_compact_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionQuickEntryCompactLightScreenshot() {
    ProductionQuickEntryFixture(darkTheme = false)
}

@PreviewTest
@Preview(name = "production_quick_entry_compact_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProductionQuickEntryCompactDarkScreenshot() {
    ProductionQuickEntryFixture(darkTheme = true)
}

@PreviewTest
@Preview(
    name = "production_quick_entry_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun ProductionQuickEntryCompactLargeFontScreenshot() {
    ProductionQuickEntryFixture(darkTheme = false)
}

@Composable
private fun ProductionQuickEntryFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        ProductionQuickEntryScreen(
            state = QuickEntryUiState(
                amountText = "42.60",
                dateText = "2026-09-04",
                dirty = true,
            ),
            onAction = {},
            onBack = {},
        )
    }
}
