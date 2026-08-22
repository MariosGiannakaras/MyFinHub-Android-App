package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun home_showsDecisionRelevantSections_andTogglesAmounts() {
        composeRule.onNodeWithText("Η οικονομική σου εικόνα").assertIsDisplayed()
        composeRule.onNodeWithText("Χρειάζεται προσοχή").assertIsDisplayed()
        composeRule.onNodeWithText("Νέα κίνηση").assertIsDisplayed()

        composeRule.onNodeWithText("Εμφάνιση ποσών").performClick()
        composeRule.onNodeWithText("Απόκρυψη ποσών").assertIsDisplayed()
    }
}
