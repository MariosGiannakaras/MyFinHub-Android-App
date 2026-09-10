package app.myfinhub.android.feature.money

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(
    name = "canonical_card_create_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun CanonicalCardCreateLightScreenshot() {
    CanonicalCardCreateFixture(darkTheme = false)
}

@PreviewTest
@Preview(
    name = "canonical_card_create_dark",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun CanonicalCardCreateDarkScreenshot() {
    CanonicalCardCreateFixture(darkTheme = true)
}

@PreviewTest
@Preview(
    name = "canonical_card_create_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun CanonicalCardCreateLargeFontScreenshot() {
    CanonicalCardCreateFixture(darkTheme = false)
}

@Composable
private fun CanonicalCardCreateFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        CanonicalCardCreateScreen(
            cards = emptyList(),
            onCreate = {},
            onBack = {},
        )
    }
}
