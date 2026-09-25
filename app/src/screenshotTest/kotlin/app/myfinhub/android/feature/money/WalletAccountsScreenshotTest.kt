package app.myfinhub.android.feature.money

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "wallet_accounts_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun WalletAccountsLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        CanonicalWalletScreen(
            state = syntheticMoneyUiState(),
            onOpenAccount = {},
            onOpenNetPosition = {},
            onOpenCard = {},
            onAddCard = {},
            onOpenLoans = {},
            onOpenLending = {},
            amountsVisibleOverride = true,
        )
    }
}

@PreviewTest
@Preview(name = "wallet_accounts_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun WalletAccountsDarkScreenshot() {
    MyFinHubTheme(darkTheme = true) {
        CanonicalWalletScreen(
            state = syntheticMoneyUiState(),
            onOpenAccount = {},
            onOpenNetPosition = {},
            onOpenCard = {},
            onAddCard = {},
            onOpenLoans = {},
            onOpenLending = {},
            amountsVisibleOverride = true,
        )
    }
}

@PreviewTest
@Preview(name = "wallet_accounts_large_font", widthDp = 412, heightDp = 915, fontScale = 1.5f, showBackground = true)
@Composable
fun WalletAccountsLargeFontScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        CanonicalWalletScreen(
            state = syntheticMoneyUiState(),
            onOpenAccount = {},
            onOpenNetPosition = {},
            onOpenCard = {},
            onAddCard = {},
            onOpenLoans = {},
            onOpenLending = {},
            amountsVisibleOverride = true,
        )
    }
}

@PreviewTest
@Preview(name = "wallet_cards_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun WalletCardsLightScreenshot() = WalletCardsFixture(darkTheme = false)

@PreviewTest
@Preview(name = "wallet_cards_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun WalletCardsDarkScreenshot() = WalletCardsFixture(darkTheme = true)

@PreviewTest
@Preview(name = "wallet_cards_large_font", widthDp = 412, heightDp = 915, fontScale = 1.5f, showBackground = true)
@Composable
fun WalletCardsLargeFontScreenshot() = WalletCardsFixture(darkTheme = false)

@Composable
private fun WalletCardsFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        CanonicalWalletScreen(
            state = syntheticMoneyUiState(),
            initiallyShowCards = true,
            cardSecretState = CardSecretUiState.Revealed(
                cardId = "card-1",
                pan = "4242424242424242",
                expiry = "12/30",
                cvv = "418",
            ),
            onOpenAccount = {},
            onOpenNetPosition = {},
            onOpenCard = {},
            onAddCard = {},
            onOpenLoans = {},
            onOpenLending = {},
            amountsVisibleOverride = true,
        )
    }
}

@PreviewTest
@Preview(name = "net_position_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun NetPositionLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        CanonicalNetPositionScreen(
            state = syntheticMoneyUiState().copy(asOfDate = "2026-09-13"),
            onBack = {},
            amountsVisibleOverride = true,
        )
    }
}
