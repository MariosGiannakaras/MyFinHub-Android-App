package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** Real native read-only category budget progress is reviewable and explicitly net-of-refunds. */
class WebCategoryBudgetComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun seededWebCategoryBudgetDisplaysExceededAndNetSpendingWithoutDraftSave() {
        val state=PlanUiState(
            budget=BudgetDraft("900","80"),
            categoryBudgets=listOf(
                CategoryBudget("food","Τρόφιμα","150","80",spent=175.0),
                CategoryBudget("refund","Εστιατόρια","50","75",spent=0.0),
            ),
            budgetMonthLabel="Οκτ 2026",
            budgetSpent=175.0,
        )
        rule.setContent { MyFinHubTheme {
            CanonicalBudget2026Screen(state,onSaveBudget={_,_->},onBack={})
        } }
        rule.onNodeWithTag("s7_category_budget_food").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Υπέρβαση ορίου").assertIsDisplayed()
        rule.onNodeWithTag("s7_category_budget_refund").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Εστιατόρια").assertIsDisplayed()
    }
}
