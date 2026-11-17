package app.myfinhub.android.feature.activity

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

class ActivityPendingSourceComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun persistedPendingCardPurchaseDisplaysCardAndAccountBesidePendingOperation() {
        val card = ActivityItem(
            id = "pending-card", rawDate = "2026-10-19",
            dateLabel = "19 Οκτ", kind = ActivityKind.EXPENSE,
            title = "Πληρωμή", subtitle = "Αγορά",
            amount = -15.0, accountLabel = "Μισθοδοσία",
            category = "Αγορές",
            canonicalKind = "card_purchase", typeLabel = "Αγορά με κάρτα",
            cardLabel = "Visa 4242", canonicalSourceOrdinal = 3,
            pendingSync = true, pendingLabel = "Εκκρεμεί επεξεργασία",
        )
        rule.setContent { MyFinHubTheme {
            ActivityLedgerScreen(
                state = ActivityUiState(items = listOf(card)),
                onAction = {}, onOpenDetail = {}, onOpenQuickEntry = {},
            )
        } }
        rule.onNodeWithText(
            "Εκκρεμεί επεξεργασία\nΑγορά με κάρτα · Visa 4242 · Μισθοδοσία",
        ).assertExists()
    }
}
