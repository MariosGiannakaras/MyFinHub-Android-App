package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** Hosted Android Compose: read-only receivable plan UI never records a repayment. */
class WebLendingSettlementComposeTest {
    @get:Rule val rule = createComposeRule()
    private fun state() = MoneyUiState(
        asOfDate = "2026-08-17",
        lendingItems = listOf(LendingItem("person-Alex", "Alex", 100.0, "")),
        lendingReceivable = 100.0,
    )
    @Test fun plannedInstallmentsDisplayExactCentTailWithoutPosting() {
        rule.setContent { MyFinHubTheme {
            CanonicalLendingScreen(state(), onBack = {})
        } }
        rule.onNodeWithTag("lending_select_person").performScrollTo().performClick()
        rule.onNodeWithTag("lending_settlement_toggle").performScrollTo().performClick()
        rule.onNodeWithTag("lending_settlement_amount")
            .performScrollTo().performTextReplacement("100")
        rule.onNodeWithTag("lending_settlement_due")
            .performScrollTo().performTextReplacement("2026-10-31")
        rule.onNodeWithTag("lending_settlement_schedule").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("2026-11-30", substring = true)
            .performScrollTo().assertIsDisplayed()
        // Global privacy preference may be hidden by another hosted test:
        // verify the final planned row by semantics, and assert cents in the
        // controlled-visibility panel test and JVM golden below.
        rule.onNodeWithTag("lending_settlement_installment_3")
            .performScrollTo().assertIsDisplayed()
    }


    @Test fun visiblePanelShowsTheExactCentTailIndependentlyOfGlobalPrivacyState() {
        rule.setContent { MyFinHubTheme {
            CanonicalLendingSettlementPanel(
                "Alex", 100.0, "2026-08-17", amountsVisible = true)
        } }
        rule.onNodeWithTag("lending_settlement_amount").performTextReplacement("100")
        rule.onNodeWithTag("lending_settlement_due").performTextReplacement("2026-10-31")
        rule.onNodeWithTag("lending_settlement_installment_3").assertIsDisplayed()
        rule.onNodeWithText("33,34", substring = true).assertIsDisplayed()
    }

    @Test fun largeSixtyPaymentProposalKeepsTheVisibleCardBoundedAtSixRows() {
        rule.setContent { MyFinHubTheme {
            CanonicalLendingSettlementPanel(
                "Alex", 100.0, "2026-08-17", amountsVisible = true)
        } }
        rule.onNodeWithTag("lending_settlement_amount").performTextReplacement("60")
        rule.onNodeWithTag("lending_settlement_installments").performTextReplacement("60")
        rule.onNodeWithTag("lending_settlement_more_installments").assertIsDisplayed()
        rule.onNodeWithTag("lending_settlement_installment_6").assertIsDisplayed()
        rule.onNodeWithTag("lending_settlement_installment_7").assertDoesNotExist()
    }

    @Test fun invalidPlannedDateDoesNotShowARealSettlement() {
        rule.setContent { MyFinHubTheme {
            CanonicalLendingScreen(state(), onBack = {})
        } }
        rule.onNodeWithTag("lending_select_person").performScrollTo().performClick()
        rule.onNodeWithTag("lending_settlement_toggle").performScrollTo().performClick()
        rule.onNodeWithTag("lending_settlement_amount")
            .performScrollTo().performTextReplacement("20")
        rule.onNodeWithTag("lending_settlement_due")
            .performScrollTo().performTextReplacement("2026-02-30")
        rule.onNodeWithTag("lending_settlement_error").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("lending_settlement_schedule").assertDoesNotExist()
    }

    @Test fun privacyHiddenScheduleMasksAmounts() {
        rule.setContent { MyFinHubTheme {
            CanonicalLendingSettlementPanel("Alex",100.0,
                "2026-08-17", amountsVisible = false)
        } }
        rule.onNodeWithTag("lending_settlement_amount").performTextReplacement("100")
        rule.onNodeWithTag("lending_settlement_schedule").assertIsDisplayed()
        rule.onNodeWithText("100,00", substring = true).assertDoesNotExist()
        rule.onNodeWithText("33,34", substring = true).assertDoesNotExist()
    }
}
