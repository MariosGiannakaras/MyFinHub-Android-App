package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    @Test
    fun home_showsCanonicalDecisionSections_andQuickEntryCompletesNavigation() {
        composeRule.onNodeWithTag("home_list").assertIsDisplayed()
        composeRule.onNodeWithText("Διαθέσιμα τώρα").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Εμφάνιση ποσών").performClick()
        composeRule.onNodeWithContentDescription("Απόκρυψη ποσών").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("Εμφάνιση ποσών").assertIsDisplayed()

        composeRule.onNodeWithTag("home_list")
            .performScrollToNode(hasText("Χρειάζεται προσοχή"))
        composeRule.onNodeWithText("Χρειάζεται προσοχή").assertIsDisplayed()

        composeRule.onNodeWithText("Νέα κίνηση", useUnmergedTree = true).performClick()

        composeRule.onNodeWithText("Νέα κίνηση").assertIsDisplayed()
        composeRule.onNodeWithText("Ποσό εξόδου").assertIsDisplayed()
        composeRule.onNodeWithText("Πλήρωσα για κάτι").assertIsDisplayed()
        composeRule.onNodeWithText("Αποθήκευση έξοδο").assertIsDisplayed()
    }
}
