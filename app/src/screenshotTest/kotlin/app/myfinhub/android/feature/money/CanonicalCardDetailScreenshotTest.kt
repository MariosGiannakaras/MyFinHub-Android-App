package app.myfinhub.android.feature.money

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private const val CARD_DETAIL_PREVIEW_ID = "card-preview"

private val canonicalCardDetailPreviewCard = MoneyCard(
    id = CARD_DETAIL_PREVIEW_ID,
    nickname = "Κύρια πιστωτική",
    last4 = "0000",
    kind = "Πιστωτική",
    currentBalance = 420.0,
    limit = 2_000.0,
    vaultState = VaultState.AVAILABLE,
)

@PreviewTest
@Preview(
    name = "canonical_card_detail_hidden_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun CanonicalCardDetailHiddenLightScreenshot() {
    CanonicalCardDetailScreenshotFixture(
        darkTheme = false,
        secretState = CardSecretUiState.Hidden(CARD_DETAIL_PREVIEW_ID),
    )
}

@PreviewTest
@Preview(
    name = "canonical_card_detail_hidden_dark",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun CanonicalCardDetailHiddenDarkScreenshot() {
    CanonicalCardDetailScreenshotFixture(
        darkTheme = true,
        secretState = CardSecretUiState.Hidden(CARD_DETAIL_PREVIEW_ID),
    )
}

@PreviewTest
@Preview(
    name = "canonical_card_detail_revealed_sanitized_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun CanonicalCardDetailRevealedSanitizedLargeFontScreenshot() {
    CanonicalCardDetailScreenshotFixture(
        darkTheme = false,
        secretState = CardSecretUiState.Revealed(
            cardId = CARD_DETAIL_PREVIEW_ID,
            pan = null,
            expiry = null,
            cvv = "•••",
        ),
    )
}

@Composable
private fun CanonicalCardDetailScreenshotFixture(
    darkTheme: Boolean,
    secretState: CardSecretUiState,
) {
    MyFinHubTheme(darkTheme = darkTheme) {
        CanonicalCardDetailScreen(
            card = canonicalCardDetailPreviewCard,
            secretState = secretState,
            onBack = {},
        )
    }
}
