package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** Real native read-only category budget progress is reviewable and explicitly net-of-refunds. */
class WebCategoryBudgetComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun netRefundAndRemainingAreReadOnlyAndPeriodBudgetsPaginatePastTwentyFour() {
        val rows=(1..25).map { i ->
            CategoryBudget(
                id=i.toString(),name="Κατηγορία $i",
                monthlyLimitText="100",alertThresholdText="80",
                spent=if(i==1) 0.0 else 50.0,
                rawSpent=if(i==1) -30.0 else 50.0,
                remaining=if(i==1) 100.0 else 50.0,
            )
        }
        rule.setContent { MyFinHubTheme {
            CanonicalBudget2026Screen(
                state=PlanUiState(categoryBudgets=rows),
                onSaveBudget={_,_->error("Read-only budget list")},
                onBack={},
            )
        } }
        rule.onNodeWithTag("s7_category_budget_refunds_0")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("s7_category_budget_remaining_0")
            .assertIsDisplayed()
        // The 25th LazyColumn item is initially beyond the composed viewport.
        // Scroll the LazyList by index so Compose materializes the button.
        rule.onNodeWithTag("s7_budget_root").performScrollToIndex(25)
        rule.onNodeWithTag("s7_category_budget_more").performClick()
        rule.onNodeWithTag("s7_category_budget_25")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun corruptedImportedBudgetValuesDoNotRenderNaNOrAnInfiniteProgressCard() {
        rule.setContent { MyFinHubTheme {
            CanonicalBudget2026Screen(
                state = PlanUiState(
                    budget = BudgetDraft("Infinity", "80"),
                    budgetSpent = Double.NaN,
                    budgetProjectedDue = Double.POSITIVE_INFINITY,
                ),
                onSaveBudget = { _, _ -> error("Invalid source must not dispatch") },
                onBack = {},
            )
        } }
        assertTrue(runCatching { rule.onNodeWithText("Πρόοδος μήνα").fetchSemanticsNode() }.isFailure)
        assertTrue(runCatching {
            rule.onNodeWithText("Γνωστές εκκρεμείς υποχρεώσεις:").fetchSemanticsNode()
        }.isFailure)
        rule.onNodeWithTag("s7_budget_root").assertIsDisplayed()
    }

    @Test fun subcentMonthlyLimitCannotDispatchARealBudgetMutation() {
        var saveCount = 0
        rule.setContent { MyFinHubTheme {
            CanonicalBudget2026Screen(
                state = PlanUiState(budget = BudgetDraft("900", "80")),
                onSaveBudget = { _, _ -> saveCount++ },
                onBack = {},
            )
        } }
        rule.onNodeWithTag("s7_budget_limit").performScrollTo()
            .performTextClearance()
        rule.onNodeWithTag("s7_budget_limit").performTextInput("900.001")
        rule.onNodeWithTag("s7_budget_save").performClick()
        rule.runOnIdle { assertEquals(0, saveCount) }
        rule.onNodeWithText("Το μηνιαίο όριο πρέπει να είναι θετικό ποσό με έως δύο δεκαδικά.")
            .performScrollTo().assertIsDisplayed()
    }

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
