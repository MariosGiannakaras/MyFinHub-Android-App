package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
        composeRule.onNodeWithText("Ποσό εξόδου").assertIsDisplayed()
        composeRule.onNodeWithText("Περισσότεροι τύποι").assertIsDisplayed()
        // The date selector is below the initial compact-device viewport.
        // Exercise the real scrollable form instead of assuming it starts visible.
        composeRule.onNodeWithText("Ημερομηνία").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Μεταφορά").performClick()
        // Fields are in a scrollable form; the destination can start offscreen
        // after navigating through the date input on compact emulator viewports.
        composeRule.onNodeWithText("Από λογαριασμό").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Προς λογαριασμό").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Η εσωτερική μεταφορά αλλάζει υπόλοιπα, όχι έσοδα ή έξοδα.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun splitEditor_exposesAccountingPartsInsteadOfPeopleCount() {
        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNodeWithText("Νέα κίνηση", useUnmergedTree = true).performClick()

        composeRule.onNodeWithText("Περισσότεροι τύποι").performClick()
        composeRule.onNodeWithTag("quick_entry_kind_list")
            .performScrollToNode(hasText("Σύνθετη αγορά"))
        composeRule.onNodeWithText("Σύνθετη αγορά").performClick()

        // The split total is a labeled editable input, not a navigation heading.
        // Verify the accessible field label and actual accounting-part editor.
        composeRule.onNodeWithText("Συνολικό ποσό")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Κατανομή ποσού").assertIsDisplayed()
        composeRule.onNodeWithText("Ποσό μέρους 1").assertIsDisplayed()
        composeRule.onNodeWithText("+ Προσθήκη μέρους")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun dirtyTransactionDraft_requiresExplicitDiscardOnBack() {
        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNodeWithText("Νέα κίνηση", useUnmergedTree = true).performClick()

        composeRule.onNodeWithText("Έσοδο").performClick()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()

        composeRule.onNodeWithText("Απόρριψη νέας κίνησης;").assertIsDisplayed()
        composeRule.onNodeWithText("Παραμονή στην επεξεργασία").assertIsDisplayed()
        composeRule.onNodeWithText("Απόρριψη αλλαγών").assertIsDisplayed()
    }
}
