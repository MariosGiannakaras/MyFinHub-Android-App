package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileRecurringOverviewTest {
    @get:Rule val rule = createComposeRule()

    @Test fun duplicatedHistoricalIdsCanRenderWithoutMutationActions() {
        rule.setContent {
            MyFinHubTheme {
                MobileRecurringOverviewContent(
                    items = listOf(
                        RecurringManageItem("reused", "Internet", 35.0, "active", "2026-10-30"),
                        RecurringManageItem("reused", "Ασφάλεια", 50.0, "paused"),
                    ),
                    amountsVisible = true,
                    onBack = {},
                )
            }
        }
        rule.onNodeWithText("Internet").assertExists()
        rule.onNodeWithText("Ασφάλεια").assertExists()
        rule.onNodeWithTag("mobile_recurring_card_0").assertExists()
        rule.onNodeWithTag("mobile_recurring_card_1").assertExists()
        assertTrue(rule.onAllNodesWithText("Καταχώριση πληρωμής").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("Επεξεργασία").fetchSemanticsNodes().isEmpty())
    }

    @Test fun realPaymentRowsExpandInPlaceAndPrivacyMasksAllAmounts() {
        val history = (1..12).map { index ->
            RecurringActualPaymentRow(
                eventId = "event-$" + index,
                dateIso = "2026-10-$" + index.toString().padStart(2, '0'),
                amount = (100 + index).toDouble(),
            )
        }
        val item = RecurringManageItem(
            id = "internet",
            title = "Internet",
            amount = 35.0,
            status = "active",
            nextDueDateIso = "2026-11-08",
            actualPaidThisMonth = 101.0,
            actualPaymentsThisMonth = 1,
            lastActualPaymentDateIso = "2026-10-01",
            lastActualPaymentAmount = 101.0,
            actualPaymentHistory = history,
        )
        rule.setContent {
            MyFinHubTheme {
                MobileRecurringOverviewContent(
                    items = listOf(item),
                    onBack = {},
                    amountsVisible = false,
                )
            }
        }
        rule.onNodeWithText("Επόμενη: 08/11/2026", substring = true).assertExists()
        rule.onNodeWithTag("mobile_recurring_payment_0_4").assertExists()
        assertTrue(rule.onAllNodesWithTag("mobile_recurring_payment_0_5").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("mobile_recurring_more_0").performScrollTo().performClick()
        rule.onNodeWithTag("mobile_recurring_payment_0_5").assertExists()
        rule.onNodeWithTag("mobile_recurring_more_0").performScrollTo().performClick()
        rule.onNodeWithTag("mobile_recurring_payment_0_11").assertExists()
        assertTrue(rule.onAllNodesWithTag("mobile_recurring_more_0").fetchSemanticsNodes().isEmpty())
        // Both scheduled/estimated and actual ledger amounts are private.
        assertTrue(rule.onAllNodesWithText("101,00", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("35,00", substring = true).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Προβολή · διαχείριση από το Web").assertIsDisplayed()
    }
}
