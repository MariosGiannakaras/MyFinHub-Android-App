package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class ActivityQuickEntryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activity_supportsDetailBackAndQuickEntryNavigation() {
        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNodeWithText("Αναζήτηση κινήσεων").assertIsDisplayed()

        composeRule.onNodeWithText("Σούπερ μάρκετ").performClick()
        composeRule.onNodeWithText("Λεπτομέρειες κίνησης").assertIsDisplayed()
        composeRule.onNodeWithText("Πίσω").performClick()
        composeRule.onNodeWithText("Αναζήτηση κινήσεων").assertIsDisplayed()

        composeRule.onNodeWithText("Νέα κίνηση", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Τι θέλεις να καταχωρίσεις;").assertIsDisplayed()
        composeRule.onNodeWithText("Μεταφορά").performClick()
        composeRule.onNodeWithText("Οι εσωτερικές μεταφορές δεν μετρούν ως έσοδα ή έξοδα.").assertIsDisplayed()
    }

    @Test
    fun splitControls_exposeTalkBackLabels() {
        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNodeWithText("Νέα κίνηση", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Μοίρασμα").performClick()

        composeRule.onNodeWithContentDescription("Μείωση ατόμων").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Αύξηση ατόμων").assertIsDisplayed()
    }
}
