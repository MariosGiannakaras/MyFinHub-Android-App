package app.myfinhub.android.feature.money

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

// Branch-scoped reference refresh trigger; removed after accepted PNGs are committed.
private val canonicalMoneyPreviewCard = MoneyCard(
    id = "card-credit",
    nickname = "Κύρια πιστωτική",
    last4 = "1881",
    kind = "Πιστωτική",
    currentBalance = 420.0,
    limit = 2_000.0,
    vaultState = VaultState.AVAILABLE,
    network = "VISA",
    bankId = "piraeus",
    canonicalKind = "credit",
)

private fun canonicalMoneyScreenshotState() = MoneyUiState(
    accounts = listOf(
        MoneyAccount("piraeus-payroll", "Πειραιώς Μισθοδοσίας", 1_155.0, "Τράπεζα", "Τράπεζα Πειραιώς"),
        MoneyAccount("piraeus-savings", "Πειραιώς Αποταμίευση", 540.0, "Αποταμίευση", "Τράπεζα Πειραιώς"),
    ),
    cards = listOf(canonicalMoneyPreviewCard),
    savingsCurrent = 540.0,
    loanOutstanding = 4_240.0,
    lendingReceivable = 310.0,
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
    name = "canonical_money_compact_large_font",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun CanonicalMoneyCompactLargeFontScreenshot() {
    CanonicalMoneyScreenshotFixture(darkTheme = false)
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

@PreviewTest
@Preview(
    name = "canonical_card_delete_dialog_light",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun CanonicalCardDeleteDialogLightScreenshot() {
    MyFinHubTheme(darkTheme = false) {
        CanonicalCardDeleteDialog(
            card = canonicalMoneyPreviewCard,
            onConfirm = {},
            onDismiss = {},
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
