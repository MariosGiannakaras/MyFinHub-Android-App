package app.myfinhub.android

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import org.junit.Rule
import org.junit.Test

/** The owner's Android companion is for balances and daily transactions, not administration. */
class FrontendUtilitiesParityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    @Test
    fun homeAndTabs_areSimple_andDoNotOfferDesktopManagement() {
        composeRule.onNodeWithTag("home_list").assertIsDisplayed()
        composeRule.onNodeWithText("Διαθέσιμα τώρα").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ρυθμίσεις").assertIsDisplayed()
        composeRule.onNodeWithText("Πλάνο").assertDoesNotExist()
        composeRule.onNodeWithText("Αναφορές").assertDoesNotExist()

        composeRule.onNodeWithText("Κινήσεις").performClick()
        composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Κάρτες").performClick()
        composeRule.onNodeWithTag("wallet_list").assertIsDisplayed()
        composeRule.onNodeWithText("Αρχική").performClick()
        composeRule.onNodeWithTag("home_list").performScrollToIndex(0)
        composeRule.onNodeWithTag("home_global_quick_entry").assertIsDisplayed()

        // Android device/app preferences remain available; Web finance administration does not.
        composeRule.onNodeWithContentDescription("Ρυθμίσεις").performClick()
        composeRule.onNodeWithText("Εφαρμογή και συσκευή").assertIsDisplayed()
        composeRule.onNodeWithText("Εμφάνιση").assertIsDisplayed()
        composeRule.onNodeWithText("Ειδοποιήσεις Android").assertIsDisplayed()
        composeRule.onNodeWithText("Σχετικά με την εφαρμογή").assertIsDisplayed()
        composeRule.onNodeWithText("Πληροφορίες και άδειες Android").assertIsDisplayed()
        composeRule.onNodeWithText("Διαχείριση κατηγοριών").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.onNodeWithTag("home_list").assertIsDisplayed()
    }
}
