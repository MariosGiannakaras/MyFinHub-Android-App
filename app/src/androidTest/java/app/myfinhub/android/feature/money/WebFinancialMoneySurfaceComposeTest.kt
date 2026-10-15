package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** Real Money surfaces accept Web financial rows, no demo placeholders. */
class WebFinancialMoneySurfaceComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun actualLoansShowCanonicalNameProviderAndAsOfOutstanding() {
        val state=MoneyUiState(
            loanOutstanding=120.0,
            loans=listOf(LoanItem("bank","Στεγαστικό","Τράπεζα",120.0,60.0,
                "Συνήθης ημέρα πληρωμής: 10",240.0)),
        )
        rule.setContent { MyFinHubTheme {
            CanonicalLoansScreen(state,onBack={})
        } }
        rule.onNodeWithText("Στεγαστικό").assertIsDisplayed()
        rule.onNodeWithText("Συνήθης ημέρα πληρωμής: 10").assertIsDisplayed()
    }

    @Test fun actualLendingShowsCounterpartyAndPermitsExplicitRepaymentPrefill() {
        val state=MoneyUiState(
            lendingReceivable=42.0,
            lendingItems=listOf(LendingItem("person-a","Άννα",42.0,"")),
        )
        rule.setContent { MyFinHubTheme {
            CanonicalLendingScreen(state,onBack={},onRecordRepayment={})
        } }
        rule.onNodeWithText("Άννα").assertIsDisplayed()
        rule.onNodeWithText("Καταχώριση επιστροφής").assertIsDisplayed()
    }

    @Test fun savingsShowsPayAndSaveButDoesNotCombineItWithExistingAccountBalance() {
        val state=MoneyUiState(
            savingsCurrent=240.0,
            savingsSources=mapOf("pay_and_save" to 15.0, "manual_transfer" to 12.0,
                "cash_offset" to 5.0),
        )
        rule.setContent { MyFinHubTheme {
            CanonicalSavingsScreen(state,onBack={})
        } }
        rule.onNodeWithText("Πηγές αποταμίευσης μήνα").assertIsDisplayed()
        rule.onNodeWithText("Pay & Save").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Μεταφορές στην αποταμίευση").performScrollTo().assertIsDisplayed()
    }
}
