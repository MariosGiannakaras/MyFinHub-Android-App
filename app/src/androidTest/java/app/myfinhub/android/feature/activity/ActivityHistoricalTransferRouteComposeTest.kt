package app.myfinhub.android.feature.activity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** Actual production Activity ledger must show historical transfer identities. */
class ActivityHistoricalTransferRouteComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun archivedUnresolvedAccountIdsShowRealTransferRouteNotPlaceholder() {
        val transfer = ActivityItem(
            id = "old-transfer", dateLabel = "20 Οκτ", rawDate = "2026-10-20",
            kind = ActivityKind.TRANSFER, title = "Εσωτερική μεταφορά",
            subtitle = "Δική μου κίνηση", amount = 125.0,
            accountLabel = "Μετρητά → Μισθοδοσία", category = null,
            fromAccountId = "deleted-cash", toAccountId = "deleted-payroll",
        )
        rule.setContent { MyFinHubTheme {
            ActivityLedgerScreen(
                state = ActivityUiState(
                    items = listOf(transfer),
                    accountOptions = emptyList(),
                ),
                onAction = {},
                onOpenDetail = {},
                onOpenQuickEntry = {},
            )
        } }
        rule.onNodeWithText("Από Μετρητά → Προς Μισθοδοσία")
            .assertIsDisplayed()
    }
}
