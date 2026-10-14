package app.myfinhub.android.feature.money

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.myfinhub.android.core.data.CanonicalCreditStatementView
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private val creditWorkspaceScreenshotCard = MoneyCard(
    id = "credit-preview", nickname = "Visa Classic",
    last4 = "1234", kind = "Πιστωτική", currentBalance = 640.50,
    limit = 1500.0, vaultState = VaultState.LOCKED,
    network = "VISA", bankId = "piraeus", canonicalKind = "credit",
    activity = listOf(
        MoneyCardActivity("purchase-demo", "13/10/2026", "Σούπερ μάρκετ", -58.90, MoneyCardActivityKind.PURCHASE),
        MoneyCardActivity("payment-demo", "10/10/2026", "Πληρωμή κάρτας", 120.00, MoneyCardActivityKind.PAYMENT),
    ),
)

private val creditWorkspaceScreenshotStatements = listOf(
    CanonicalCreditStatementView(
        id = "credit-preview:2026-10-25", cardId = "credit-preview",
        openDate = "2026-09-26", closeDate = "2026-10-25",
        dueDate = "2026-11-05", boundaryRule = "next-cycle",
        purchaseIds = listOf("purchase-demo"), paymentIds = emptyList(),
        purchaseTotal = 240.50, paymentTotal = 0.0, remaining = 240.50,
        status = "open",
    ),
    CanonicalCreditStatementView(
        id = "credit-preview:2026-09-25", cardId = "credit-preview",
        openDate = "2026-08-26", closeDate = "2026-09-25",
        dueDate = "2026-10-05", boundaryRule = "next-cycle",
        purchaseIds = listOf("prior-purchase"), paymentIds = listOf("payment-demo"),
        purchaseTotal = 520.0, paymentTotal = 120.0, remaining = 400.0,
        status = "due",
    ),
)

@PreviewTest
@Preview(name = "inline_credit_workspace_light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun InlineCreditWorkspaceLightScreenshot() {
    CreditWorkspaceScreenshot(darkTheme = false)
}

@PreviewTest
@Preview(name = "inline_credit_workspace_dark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun InlineCreditWorkspaceDarkScreenshot() {
    CreditWorkspaceScreenshot(darkTheme = true)
}

@PreviewTest
@Preview(name = "inline_credit_workspace_large_type", widthDp = 412, heightDp = 915, fontScale = 1.5f, showBackground = true)
@Composable
fun InlineCreditWorkspaceLargeTypeScreenshot() {
    CreditWorkspaceScreenshot(darkTheme = false)
}

@Composable
private fun CreditWorkspaceScreenshot(darkTheme: Boolean) {
    MyFinHubTheme(darkTheme = darkTheme) {
        InlineCreditCardWorkspace(
            card = creditWorkspaceScreenshotCard,
            statements = creditWorkspaceScreenshotStatements,
            amountsVisible = true,
            onPayCard = {},
            onAddPurchase = {},
            onOpenActivity = {},
            onOpenDetails = {},
        )
    }
}
