package app.myfinhub.android.feature.money

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private fun canonicalMoneyScreenshotState() = MoneyUiState(
    accounts = listOf(
        MoneyAccount("acc-main", "Κύριος λογαριασμός", 1_155.0, "Τράπεζα"),
        MoneyAccount("acc-save", "Αποταμίευση", 540.0, "Αποταμίευση"),
    ),
    cards = emptyList(),
    savingsCurrent = 540.0,
    loanOutstanding = 4_240.0,
    lendingReceivable = 310.0,
    frontendMessage = "Εκκρεμείς διαγραφές καρτών:\nΠιστωτική ••••1881 · Εκκρεμεί διαγραφή · Προς συγχρονισμό",
)

@PreviewTest
@Preview(
    name = "canonical_money_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun CanonicalMoneyCompactLightScreenshot() {
    CanonicalMoneyScreenshotFixture(darkTheme = false)
}

@PreviewTest
@Preview(
    name = "canonical_money_compact_dark",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun CanonicalMoneyCompactDarkScreenshot() {
    CanonicalMoneyScreenshotFixture(darkTheme = true)
}

@PreviewTest
@Preview(
    name = "canonical_loans_compact_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun CanonicalLoansCompactLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        CanonicalLoansScreen(
            state = canonicalMoneyScreenshotState(),
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "canonical_savings_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun CanonicalSavingsLargeFontScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        CanonicalSavingsScreen(
            state = canonicalMoneyScreenshotState(),
            onBack = {},
        )
    }
}

@Composable
private fun CanonicalMoneyScreenshotFixture(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        CanonicalMoneyScreen(
            state = canonicalMoneyScreenshotState(),
            onOpenCard = {},
            onOpenSavings = {},
            onOpenLoans = {},
            onOpenLending = {},
        )
    }
}
