package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

class FrontendUtilitiesParityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    @Test
    fun homeReviewAndUtilityFlows_areNestedAndReachable() {
        composeRule.onNodeWithTag("attention-scheduled-review").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Γιατί εμφανίζεται").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Γιατί εμφανίζεται").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Σήμανση ως ελεγμένο").performScrollTo().performClick()
        composeRule.onNodeWithText("Η οικονομική σου εικόνα").assertIsDisplayed()

        composeRule.onNodeWithTag("home_list").performScrollToNode(hasText("Ρυθμίσεις & δεδομένα"))
        composeRule.onNodeWithText("Ρυθμίσεις").performClick()
        composeRule.onNodeWithText("Προτιμήσεις εφαρμογής").assertIsDisplayed()
        composeRule.onNodeWithText("Πίσω").performClick()

        composeRule.onNodeWithText("Εισαγωγή & αντίγραφα").performClick()
        composeRule.onNodeWithText("Προεπισκόπηση εισαγωγής").performClick()
        composeRule.onNodeWithText("Αντικατάσταση δεδομένων").performClick()
        composeRule.onNodeWithText("Αντικατάσταση όλων των δεδομένων;").assertIsDisplayed()
        composeRule.onNodeWithText("Ακύρωση").performClick()
        composeRule.onNodeWithText("Πίσω").performClick()

        composeRule.onNodeWithText("Ιστορικό αλλαγών").performClick()
        composeRule.onNodeWithText("Αναίρεση & επανάληψη").assertIsDisplayed()
        composeRule.onNodeWithText("Αναίρεση").performClick()
        composeRule.onNodeWithText("2 από 3 αλλαγές εφαρμοσμένες").assertIsDisplayed()
    }
}
