package app.myfinhub.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import org.junit.Assert.assertTrue
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
        composeRule.onNodeWithText("Περισσότερα").assertIsDisplayed()
        composeRule.onNodeWithText("Ημερομηνία").assertIsDisplayed()

        composeRule.onNodeWithText("Μεταφορά").performClick()
        composeRule.onNodeWithText("Από λογαριασμό").assertIsDisplayed()
        composeRule.onNodeWithText("Προς λογαριασμό").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Η εσωτερική μεταφορά αλλάζει υπόλοιπα, όχι έσοδα ή έξοδα.",
        ).assertIsDisplayed()
    }

    @Test
    fun splitEditor_exposesAccountingPartsInsteadOfPeopleCount() {
        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNodeWithText("Νέα κίνηση", useUnmergedTree = true).performClick()

        composeRule.onNodeWithText("Περισσότερα").performClick()
        composeRule.onNodeWithTag("quick_entry_kind_list")
            .performScrollToNode(hasText("Σύνθετη αγορά"))
        composeRule.onNodeWithText("Σύνθετη αγορά").performClick()

        val splitAmountHeadings = composeRule
            .onAllNodes(hasText("Συνολικό ποσό"))
            .fetchSemanticsNodes()
        assertTrue(
            splitAmountHeadings.any { node ->
                node.config.getOrNull(SemanticsProperties.Heading) != null
            },
        )
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
        composeRule.onNodeWithText("Συνέχεια").assertIsDisplayed()
        composeRule.onNodeWithText("Απόρριψη").assertIsDisplayed()
    }
}
