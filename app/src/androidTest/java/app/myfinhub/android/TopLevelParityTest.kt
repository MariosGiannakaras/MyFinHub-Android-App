package app.myfinhub.android

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
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
        // The card stack is a lazy Money-list item and may not be composed initially on compact,
        // large-font, foldable or tablet viewports. Scroll the owning list to the real stack before
        // validating its keyboard activation contract.
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
        composeRule.onNodeWithText("Μηνιαίο budget").assertIsDisplayed()

        composeRule.onNodeWithText("Αναλύσεις").performClick()
        composeRule.onNodeWithText("Μηνιαία ροή").assertIsDisplayed()
        composeRule.onNodeWithText("Κορυφαίες κατηγορίες").assertIsDisplayed()
    }

    @Test
    fun insights_drillsIntoExpenseActivityProjection() {
        composeRule.onNodeWithText("Αναλύσεις").performClick()
        composeRule.onNodeWithTag("insights_list")
            .performScrollToNode(hasText("Προβολή σχετικών κινήσεων"))
        composeRule.onNodeWithText("Προβολή σχετικών κινήσεων").performClick()

        composeRule.onNodeWithText("Αναζήτηση κινήσεων").assertIsDisplayed()
        composeRule.onNodeWithText("Σούπερ μάρκετ").assertIsDisplayed()
        composeRule.onNodeWithText("Μισθός").assertDoesNotExist()
    }
}
