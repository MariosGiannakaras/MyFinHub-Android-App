package app.myfinhub.android.feature.money

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private fun s7DebtState() = MoneyUiState(
    loanOutstanding = 4_240.0,
    lendingReceivable = 310.0,
    aggregateCreditOutstanding = 875.40,
)

@Composable
private fun DebtFixture(dark: Boolean) {
    MyFinHubTheme(dark) {
        CanonicalWalletScreen(
            state = s7DebtState(),
            initiallyShowDebts = true,
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
@Preview(name = "s7_debts_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun S7DebtsLight() = DebtFixture(false)

@PreviewTest
@Preview(name = "s7_debts_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun S7DebtsDark() = DebtFixture(true)

@PreviewTest
@Preview(name = "s7_debts_large", widthDp = 412, heightDp = 1100, fontScale = 1.5f, showBackground = true)
@Composable
fun S7DebtsLarge() = DebtFixture(false)
