package app.myfinhub.android

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
        composeRule.onNodeWithText("Χρήματα").performClick()
        composeRule.onNodeWithText("Λογαριασμοί").assertIsDisplayed()
        // Production Money data owns real stable card IDs; the stack is also a lazy item on
        // compact/large-font/adaptive viewports, so scroll the owning list to the real stack.
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
        composeRule.onNodeWithText("Πίσω").performClick()

        composeRule.onNodeWithText("Πλάνο").performClick()
        composeRule.onNodeWithText("Επόμενες υποχρεώσεις").assertIsDisplayed()
        composeRule.onNode(hasText("Budgets", substring = true) and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNode(hasText("πρόβλεψη", substring = true, ignoreCase = true) and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithText("Αναλύσεις").performClick()
        composeRule.onNodeWithText("Μηνιαία ροή").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_list")
            .performScrollToNode(hasText("Κορυφαίες κατηγορίες"))
        composeRule.onNodeWithText("Κορυφαίες κατηγορίες").assertIsDisplayed()
    }

    @Test
    fun plan_drillsIntoItemBudgetAndForecastWorkflows() {
        composeRule.onNodeWithText("Πλάνο").performClick()

        composeRule.onNode(hasText("Ενοίκιο") and hasClickAction())
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Επεξεργασία").assertIsDisplayed()
        composeRule.onNodeWithText("Αποθήκευση").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Πίσω").performClick()

        composeRule.onNode(hasText("Budgets", substring = true) and hasClickAction())
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Συνολικό μηνιαίο budget").assertIsDisplayed()
        composeRule.onNodeWithText("Budgets ανά κατηγορία").assertIsDisplayed()
        composeRule.onNodeWithText("Πίσω").performClick()

        composeRule.onNode(hasText("πρόβλεψη", substring = true, ignoreCase = true) and hasClickAction())
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Προβλεπόμενο διαθέσιμο").assertIsDisplayed()
        composeRule.onNodeWithText("Τι επηρεάζει την πρόβλεψη").assertIsDisplayed()
    }

    @Test
    fun insights_drillsIntoExpenseActivityProjection() {
        composeRule.onNodeWithText("Αναλύσεις").performClick()
        composeRule.onNodeWithTag("insights_list")
            .performScrollToNode(hasText("Προβολή σχετικών κινήσεων"))
        composeRule.onNodeWithText("Προβολή σχετικών κινήσεων").performClick()

        composeRule.onNodeWithText("Αναζήτηση κινήσεων").assertIsDisplayed()
        // Expanded Activity can show the same title in both list and detail pane. The clickable
        // row is the projection under test; the detail copy is not a second transaction.
        composeRule.onNode(hasText("Σούπερ μάρκετ") and hasClickAction()).assertIsDisplayed()
        composeRule.onNodeWithText("Μισθός").assertDoesNotExist()
    }
}
