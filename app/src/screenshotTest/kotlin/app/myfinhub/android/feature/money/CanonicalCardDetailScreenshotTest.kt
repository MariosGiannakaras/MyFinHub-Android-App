package app.myfinhub.android.feature.money

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private const val CARD_DETAIL_PREVIEW_ID = "card-preview"

private val canonicalCardDetailPreviewCard = MoneyCard(
    id = CARD_DETAIL_PREVIEW_ID,
    nickname = "Κύρια πιστωτική ταξιδιών",
    last4 = "0000",
    kind = "Πιστωτική",
    currentBalance = 420.0,
    limit = 2_000.0,
    vaultState = VaultState.AVAILABLE,
    network = "Mastercard",
    bankId = "revolut",
    canonicalKind = "credit",
)

@PreviewTest
@Preview(name = "canonical_card_detail_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun CanonicalCardDetailLightScreenshot() = CardDetailFixture(darkTheme = false)

@PreviewTest
@Preview(name = "canonical_card_detail_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun CanonicalCardDetailDarkScreenshot() = CardDetailFixture(darkTheme = true)

@PreviewTest
@Preview(
    name = "canonical_card_detail_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun CanonicalCardDetailLargeFontScreenshot() = CardDetailFixture(darkTheme = false)

@PreviewTest
@Preview(name = "canonical_card_secure_hidden_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun CanonicalCardSecureHiddenLightScreenshot() = CardSecureFixture(darkTheme = false)

@PreviewTest
@Preview(name = "canonical_card_secure_hidden_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun CanonicalCardSecureHiddenDarkScreenshot() = CardSecureFixture(darkTheme = true)

@PreviewTest
@Preview(
    name = "canonical_card_secure_hidden_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun CanonicalCardSecureHiddenLargeFontScreenshot() = CardSecureFixture(darkTheme = false)

@Composable
private fun CardDetailFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        CanonicalCardDetailScreen(card = canonicalCardDetailPreviewCard, onBack = {})
    }
}

@Composable
private fun CardSecureFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        CanonicalCardSecureDetailsScreen(
            cardId = CARD_DETAIL_PREVIEW_ID,
            card = canonicalCardDetailPreviewCard,
            secretState = CardSecretUiState.Revealed(
                cardId = CARD_DETAIL_PREVIEW_ID,
                pan = "5555444433330000",
                expiry = "09/31",
                cvv = "731",
            ),
            onReveal = {},
            onSaveServerSecrets = { pan, expiry -> pan.fill('\u0000'); expiry.fill('\u0000') },
            onSaveCvv = { it.fill('\u0000') },
            onBack = {},
        )
    }
}
