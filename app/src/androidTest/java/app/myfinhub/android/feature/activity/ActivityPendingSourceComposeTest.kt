package app.myfinhub.android.feature.activity

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ActivityPendingSourceComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun malformedPaddedActivityIdentityShowsWarningAndCannotNavigate() {
        var opens = 0
        val malformed = ActivityItem(
            id = " event-padded ", rawDate = "2026-10-19", dateLabel = "19 Οκτ",
            kind = ActivityKind.EXPENSE, title = "Καφές", subtitle = "",
            amount = -5.0, accountLabel = "Μετρητά", category = "Τρόφιμα",
            canonicalSourceOrdinal = 2, note = "Καφές",
        )
        rule.setContent { MyFinHubTheme {
            ActivityLedgerScreen(
                state = ActivityUiState(items = listOf(malformed)),
                onAction = {}, onOpenDetail = { opens++ }, onOpenQuickEntry = {},
            )
        } }
        rule.onNodeWithText("Καφές").assertExists()
        rule.onNodeWithText("Μη έγκυρο αναγνωριστικό · η κίνηση δεν ανοίγει").assertExists()
        rule.onNodeWithTag("activity_ledger_source_0_0").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0, opens) }
    }

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
