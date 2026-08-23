package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Χρειάζεται προσοχή"))
        composeRule.onNodeWithText("Χρειάζεται προσοχή").assertIsDisplayed()

        composeRule.onNodeWithText("Νέα κίνηση", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Έξοδο").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Επιλέχθηκε: Έξοδο").assertIsDisplayed()
    }
}
