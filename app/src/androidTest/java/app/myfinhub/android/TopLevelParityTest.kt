package app.myfinhub.android

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import org.junit.Rule
import org.junit.Test

class TopLevelParityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    @Test
    fun moneyPlanAndInsights_haveRealMobileContent() {
        composeRule.onNodeWithText("Περιουσία").performClick()
        composeRule.onNodeWithText("Λογαριασμοί").assertIsDisplayed()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("credit_card_stack"))
        composeRule.onNodeWithTag("credit_card_stack")
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag("credit_card_stack")
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithText(
            "PAN/λήξη αποκαλύπτονται μόνο από το owner+AAL2 server vault. Το CVV παραμένει αποκλειστικά σε κρυπτογραφημένο vault αυτής της συσκευής.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Αποκάλυψη ασφαλών στοιχείων").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()

        composeRule.onNodeWithText("Πλάνο").performClick()
        composeRule.onNodeWithText("Επόμενες υποχρεώσεις").assertIsDisplayed()
        composeRule.onNode(hasText("Budgets", substring = true) and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithText("Ανάλυση").performClick()
        composeRule.onNodeWithText("Πορεία 4 μηνών").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_list")
            .performScrollToNode(hasText("Πού πηγαίνουν τα έξοδα"))
        composeRule.onNodeWithText("Πού πηγαίνουν τα έξοδα").assertIsDisplayed()
    }

    @Test
    fun plan_drillsIntoItemAndBudgetWorkflows() {
        composeRule.onNodeWithText("Πλάνο").performClick()

        composeRule.onNode(hasText("Ενοίκιο") and hasClickAction())
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Επεξεργασία").assertIsDisplayed()
        composeRule.onNodeWithText("Αποθήκευση").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()

        composeRule.onNode(hasText("Budgets", substring = true) and hasClickAction())
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Συνολικό μηνιαίο budget").assertIsDisplayed()
        composeRule.onNodeWithText("Budgets ανά κατηγορία").assertIsDisplayed()
    }

    @Test
    fun reselectingTopLevelDestination_returnsNestedFlowToRoot() {
        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNode(hasText("Σούπερ μάρκετ") and hasClickAction())
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Λεπτομέρειες κίνησης").assertIsDisplayed()

        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun insights_categoryDeepLink_opensExpenseActivityWithCategoryQuery() {
        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNode(hasText("Σούπερ μάρκετ") and hasClickAction())
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Λεπτομέρειες κίνησης").assertIsDisplayed()

        composeRule.onNodeWithText("Ανάλυση").performClick()
        composeRule.onNodeWithTag("insights_list")
            .performScrollToNode(hasText("Τρόφιμα"))
        composeRule.onNodeWithContentDescription("Προβολή κινήσεων κατηγορίας Τρόφιμα")
            .performClick()

        composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Έξοδα").assertIsDisplayed()
        composeRule.onNodeWithText("Τρόφιμα").assertIsDisplayed()
        composeRule.onNode(hasText("Σούπερ μάρκετ") and hasClickAction()).assertIsDisplayed()
    }
}
