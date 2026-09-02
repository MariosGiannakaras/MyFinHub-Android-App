package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class ActivityQuickEntryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    @Test
    fun activity_supportsDetailBackAndFullTransactionEntryNavigation() {
        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNode(hasText("Σούπερ μάρκετ") and hasClickAction()).performClick()

        val compactDetail = runCatching {
            composeRule.onNodeWithContentDescription("Πίσω").fetchSemanticsNode()
        }.isSuccess
        if (compactDetail) {
            composeRule.onNodeWithText("Λεπτομέρειες κίνησης").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Πίσω").performClick()
            composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true).assertIsDisplayed()
        } else {
            composeRule.onNodeWithText("Σημείωση").assertIsDisplayed()
            composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true).assertIsDisplayed()
        }

        composeRule.onNodeWithText("Νέα κίνηση", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Τι θέλεις να καταχωρίσεις;").assertIsDisplayed()
        composeRule.onNodeWithText("Τύπος κίνησης").assertIsDisplayed()
        composeRule.onNodeWithText("Ημερομηνία · YYYY-MM-DD").assertIsDisplayed()

        composeRule.onNodeWithText("Έξοδο").performClick()
        composeRule.onNodeWithText("Μεταφορά").performClick()
        composeRule.onNodeWithText("Από λογαριασμό").assertIsDisplayed()
        composeRule.onNodeWithText("Προς λογαριασμό").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Η εσωτερική μεταφορά αλλάζει υπόλοιπα αλλά δεν μετρά ως έσοδο ή έξοδο.",
        ).assertIsDisplayed()
    }

    @Test
    fun splitEditor_exposesAccountingPartsInsteadOfPeopleCount() {
        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNodeWithText("Νέα κίνηση", useUnmergedTree = true).performClick()

        composeRule.onNodeWithText("Έξοδο").performClick()
        composeRule.onNodeWithText("Σύνθετη αγορά").performClick()

        composeRule.onNodeWithText("Μέρη σύνθετης αγοράς").assertIsDisplayed()
        composeRule.onNodeWithText("Ποσό μέρους 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Προσθήκη μέρους σύνθετης αγοράς")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun dirtyTransactionDraft_requiresExplicitDiscardOnBack() {
        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNodeWithText("Νέα κίνηση", useUnmergedTree = true).performClick()

        composeRule.onNodeWithText("Έξοδο").performClick()
        composeRule.onNodeWithText("Έσοδο").performClick()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()

        composeRule.onNodeWithText("Απόρριψη αλλαγών;").assertIsDisplayed()
        composeRule.onNodeWithText("Συνέχεια επεξεργασίας").assertIsDisplayed()
        composeRule.onNodeWithText("Απόρριψη").assertIsDisplayed()
    }
}
