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
        Money2026Screen(
            state = MoneyUiState(),
            onOpenCard = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "money_overview_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun MoneyOverviewCompactLargeFontScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        Money2026Screen(
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
        Savings2026Screen(
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
        Loans2026Screen(
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
        LoanEditor2026Screen(
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
        Lending2026Screen(
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
        LendingEditor2026Screen(
            item = syntheticLendingItems().first(),
            onAction = {},
            onBack = {},
        )
    }
}
