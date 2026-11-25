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
    val state = syntheticMoneyUiState()
    MyFinHubTheme(darkTheme = darkTheme) {
        CanonicalWalletScreen(
            state = state.copy(cards = state.cards.map { card ->
                if (card.id == "card-1") card.copy(canonicalKind = "debit") else card
            }),
            cardsOnly = true,
            companionMode = true,
            cardSecretState = CardSecretUiState.Hidden(),
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
