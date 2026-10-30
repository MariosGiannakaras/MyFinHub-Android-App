package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** Real native payoff UI, no fake estimate renderer. */
class CreditPayoffSourceSafetyComposeTest {
    @get:Rule val rule = createComposeRule()

    private fun card(
        debt: Double = 250.0,
        rates: List<CardAnnualRateVersion> = listOf(CardAnnualRateVersion("2025-01-01", 9.0)),
    ) = MoneyCard(
        id = "payoff-card", nickname = "Credit", last4 = "1234",
        kind = "Πιστωτική", canonicalKind = "credit", currentBalance = debt,
        limit = 1000.0, vaultState = VaultState.LOCKED, annualRates = rates,
    )

    @Test fun invalidDebtCannotBecomeForecastOrMisleadingPaymentInputError() {
        rule.setContent { MyFinHubTheme {
            CanonicalCreditPayoffSimulator(card(Double.NaN), amountsVisible = true)
        } }
        rule.onNodeWithTag("card_payoff_invalid_debt").assertExists()
        rule.onNodeWithTag("card_payoff_monthly_amount").assertDoesNotExist()
        rule.onNodeWithTag("card_payoff_estimate").assertDoesNotExist()
        rule.onNodeWithText("NaN", substring = true).assertDoesNotExist()
    }

    @Test fun invalidLatestRateBlocksForecastWithoutFallingBack() {
        rule.setContent { MyFinHubTheme {
            CanonicalCreditPayoffSimulator(
                card(rates = listOf(
                    CardAnnualRateVersion("2025-01-01", 8.0),
                    CardAnnualRateVersion("2026-01-01", Double.POSITIVE_INFINITY),
                )), amountsVisible = true,
            )
        } }
        rule.onNodeWithTag("card_payoff_invalid_rate").assertExists()
        rule.onNodeWithTag("card_payoff_monthly_amount").assertDoesNotExist()
        rule.onNodeWithText("Infinity", substring = true).assertDoesNotExist()
    }

    @Test fun validSourceProducesWorkingAdvisoryWithoutWritingPayment() {
        rule.setContent { MyFinHubTheme {
            CanonicalCreditPayoffSimulator(card(), amountsVisible = true)
        } }
        rule.onNodeWithTag("card_payoff_monthly_amount").performTextInput("50")
        rule.onNodeWithTag("card_payoff_estimate").assertExists()
    }

    @Test fun hideAmountsStillHidesAnyForecastInputsAndErrors() {
        rule.setContent { MyFinHubTheme {
            CanonicalCreditPayoffSimulator(card(Double.NaN), amountsVisible = false)
        } }
        rule.onNodeWithText("Η πρόβλεψη αποκρύπτεται όσο είναι κρυφά τα ποσά.").assertExists()
        rule.onNodeWithTag("card_payoff_monthly_amount").assertDoesNotExist()
        rule.onNodeWithTag("card_payoff_invalid_debt").assertDoesNotExist()
    }
}
