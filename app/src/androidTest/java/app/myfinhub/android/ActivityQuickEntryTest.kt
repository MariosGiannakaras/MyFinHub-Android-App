package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class ActivityQuickEntryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    @Test
    fun activity_supportsDetailBackAndQuickEntryNavigation() {
        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true).assertIsDisplayed()

        // Expanded Activity renders the selected transaction in a parallel detail pane, so the
        // title can legitimately exist twice. Target the interactive list row explicitly.
        composeRule.onNode(hasText("Σούπερ μάρκετ") and hasClickAction()).performClick()

        val compactDetail = runCatching {
            composeRule.onNodeWithContentDescription("Πίσω").fetchSemanticsNode()
        }.isSuccess
        if (compactDetail) {
            composeRule.onNodeWithText("Λεπτομέρειες κίνησης").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Πίσω").performClick()
            composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true).assertIsDisplayed()
        } else {
            // Tablet/expanded layout keeps the Activity list visible and updates the inline pane.
            composeRule.onNodeWithText("Σημείωση").assertIsDisplayed()
            composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true).assertIsDisplayed()
        }

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
