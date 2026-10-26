package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.core.data.CanonicalConfirmedLendingPlanView
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

class WebConfirmedLendingPlanComposeTest {
    @get:Rule val rule = createComposeRule()
    private val plan = CanonicalConfirmedLendingPlanView(
        person = "Alex", id = "confirmed-1", total = 100.0,
        firstDueDate = "2026-10-31", installments = 3, everyMonths = 1,
        confirmedAt = "2026-08-17T10:00:00Z",
    )

    @Test fun existingConfirmedPlanAppearsBesideRealDebtWithoutPosting() {
        rule.setContent { MyFinHubTheme {
            CanonicalLendingScreen(
                state = MoneyUiState(
                    asOfDate = "2026-08-21",
                    lendingItems = listOf(LendingItem("lending-Alex","Alex",40.0,"")),
                    lendingReceivable = 40.0,
                    confirmedLendingPlans = listOf(plan),
                ),
                onBack = {},
            )
        } }
        rule.onNodeWithTag("lending_select_person").performScrollTo().performClick()
        rule.onNodeWithText("Επιβεβαιωμένο πρόγραμμα επιστροφής")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("2026-10-31", substring = true)
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("δεν καταχωρίζει είσπραξη", substring = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun maskedAmountsRemainHiddenEvenOnSavedConfirmedPlan() {
        rule.setContent { MyFinHubTheme {
            CanonicalConfirmedLendingPlanDetails(plan,40.0,amountsVisible = false)
        } }
        rule.onNodeWithTag("lending_confirmed_plan_details").assertIsDisplayed()
        rule.onNodeWithText("100,00", substring = true).assertDoesNotExist()
        rule.onNodeWithText("40,00", substring = true).assertDoesNotExist()
    }

    @Test fun malformedImportedPlanShowsWarningWithoutFinancialNumbers() {
        rule.setContent { MyFinHubTheme {
            CanonicalConfirmedLendingPlanDetails(
                CanonicalConfirmedLendingPlanView(
                    person = "Alex",
                    validationError = "Το εισαγόμενο πρόγραμμα επιστροφής χρειάζεται έλεγχο στοιχείων.",
                ),null,amountsVisible = true,
            )
        } }
        rule.onNodeWithTag("lending_confirmed_plan_invalid").assertIsDisplayed()
        rule.onNodeWithTag("lending_confirmed_plan_total").assertDoesNotExist()
    }
}
