package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActivityS3NavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    @Test
    fun activity_filterSheet_exposesExactScopeControls() {
        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNodeWithText("Φίλτρα").performClick()

        composeRule.onNodeWithText("Φίλτρα κινήσεων").assertIsDisplayed()
        composeRule.onNodeWithText("Τύπος κίνησης").assertIsDisplayed()
        composeRule.onNodeWithText("Λογαριασμός").assertIsDisplayed()
        composeRule.onNodeWithText("Κατηγορία").assertIsDisplayed()
        composeRule.onNodeWithText("Από ημερομηνία").assertIsDisplayed()
        composeRule.onNodeWithText("Έως ημερομηνία").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Εφαρμογή").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun activity_detailIsReadFirst_andEditorIsSeparateSecondaryRoute() {
        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNodeWithText("Σούπερ μάρκετ").performClick()

        composeRule.onNodeWithText("Λεπτομέρειες κίνησης").assertIsDisplayed()
        composeRule.onNodeWithText("Επεξεργασία").assertIsDisplayed()
        composeRule.onNodeWithText("Περισσότερα").assertIsDisplayed()
        assertTrue(runCatching { composeRule.onNodeWithText("Πορτοφόλι").fetchSemanticsNode() }.isFailure)

        composeRule.onNodeWithText("Επεξεργασία").performClick()
        composeRule.onNodeWithText("Επεξεργασία κίνησης").assertIsDisplayed()
        composeRule.onNodeWithText("Αποθήκευση αλλαγών").assertIsDisplayed()
        assertTrue(runCatching { composeRule.onNodeWithText("Πορτοφόλι").fetchSemanticsNode() }.isFailure)

        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.onNodeWithText("Λεπτομέρειες κίνησης").assertIsDisplayed()
    }

    @Test
    fun activity_deleteConfirmation_statesMyFinHubMeaning_notBankReversal() {
        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNodeWithText("Σούπερ μάρκετ").performClick()
        composeRule.onNodeWithText("Περισσότερα").performClick()
        composeRule.onNodeWithText("Διαγραφή κίνησης").performClick()

        composeRule.onNodeWithText("Διαγραφή κίνησης;").assertIsDisplayed()
        composeRule.onNodeWithText("Δεν ακυρώνει συναλλαγή στην τράπεζα.", substring = true).assertIsDisplayed()
    }
}
