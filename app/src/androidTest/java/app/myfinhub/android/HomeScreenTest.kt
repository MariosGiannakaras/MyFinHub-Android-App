package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    @Test
    fun home_showsDecisionRelevantSections_andSupportsQuickEntry() {
        composeRule.onNodeWithText("Η οικονομική σου εικόνα").assertIsDisplayed()

        composeRule.onNodeWithText("Εμφάνιση ποσών").performClick()
        composeRule.onNodeWithText("Απόκρυψη ποσών").assertIsDisplayed()

        composeRule
            .onNodeWithText("Χρειάζεται προσοχή")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithText("Νέα κίνηση", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Έξοδο").assertIsDisplayed().performClick()
        // Existence proves the state transition without requiring the confirmation marker to be
        // inside the current viewport on every supported screen/font configuration.
        composeRule.onNodeWithText("Επιλέχθηκε: Έξοδο").fetchSemanticsNode()
    }
}
