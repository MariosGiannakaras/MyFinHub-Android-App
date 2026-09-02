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

class MoneyParityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    @Test
    fun money_drillsIntoSavingsLoansAndLending() {
        composeRule.onNodeWithText("Χρήματα").performClick()
        composeRule.onNodeWithText("Λογαριασμοί").assertIsDisplayed()

        composeRule.onNodeWithTag("money_list")
            .performScrollToNode(hasText("Διαχείριση στόχου"))
        composeRule.onNodeWithText("Διαχείριση στόχου").performClick()
        composeRule.onNodeWithText("Χρονικός στόχος").assertIsDisplayed()
        composeRule.onNodeWithText("Μηνιαία συνεισφορά").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()

        composeRule.onNodeWithTag("money_list")
            .performScrollToNode(hasText("Δάνεια"))
        composeRule.onNodeWithText("Δάνεια").performClick()
        composeRule.onNodeWithText("Συνολικό υπόλοιπο").assertIsDisplayed()
        composeRule.onNodeWithText("Προσωπικό δάνειο").performClick()
        composeRule.onNodeWithText("Πιστωτής").assertIsDisplayed()
        composeRule.onNodeWithText("Μηνιαία δόση").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()

        composeRule.onNodeWithTag("money_list")
            .performScrollToNode(hasText("Απαιτήσεις"))
        composeRule.onNodeWithText("Απαιτήσεις").performClick()
        composeRule.onNodeWithText("Αναμενόμενες επιστροφές").assertIsDisplayed()
        composeRule.onNodeWithText("Επιστροφή χρημάτων").performClick()
        composeRule.onNodeWithText("Αναμενόμενη ημερομηνία").assertIsDisplayed()
    }
}
