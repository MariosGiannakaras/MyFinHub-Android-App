package app.myfinhub.android.feature.money

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(
    name = "money_overview_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun MoneyOverviewCompactLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        MoneyScreen(
            state = MoneyUiState(),
            onOpenCard = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "money_savings_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun MoneySavingsCompactLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        SavingsScreen(
            state = MoneyUiState(),
            onAction = {},
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "money_loans_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun MoneyLoansCompactLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        LoansScreen(
            state = MoneyUiState(),
            onOpenLoan = {},
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "money_loan_editor_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun MoneyLoanEditorCompactLargeFontScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        LoanEditorScreen(
            loan = syntheticLoans().first(),
            onAction = {},
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "money_lending_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun MoneyLendingCompactLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        LendingScreen(
            state = MoneyUiState(),
            onOpenItem = {},
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "money_lending_editor_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun MoneyLendingEditorCompactLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        LendingEditorScreen(
            item = syntheticLendingItems().first(),
            onAction = {},
            onBack = {},
        )
    }
}
