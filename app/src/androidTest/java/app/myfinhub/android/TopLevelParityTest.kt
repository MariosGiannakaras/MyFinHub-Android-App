package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

class TopLevelParityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun moneyPlanAndInsights_haveRealMobileContent() {
        composeRule.onNodeWithText("Χρήματα").performClick()
        composeRule.onNodeWithText("Λογαριασμοί").assertIsDisplayed()
        composeRule.onNodeWithText("Πιστωτική").performClick()
        composeRule.onNodeWithText("PAN/λήξη παραμένουν σε server vault και αποκαλύπτονται μόνο μετά από έγκυρο owner+AAL2 session.").assertIsDisplayed()
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
