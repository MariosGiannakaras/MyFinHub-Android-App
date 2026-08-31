package app.myfinhub.android.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "home_attention_detail_compact", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun HomeAttentionDetailCompactScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        HomeAttentionDetailScreen(
            item = syntheticHomeUiState().attentionItems.first(),
            onMarkReviewed = {},
            onBack = {},
        )
    }
}
