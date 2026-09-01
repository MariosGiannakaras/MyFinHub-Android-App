package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
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
        scrollHomeTagIntoView("attention-scheduled-review")
        composeRule.onNodeWithTag("attention-scheduled-review").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Γιατί εμφανίζεται").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Γιατί εμφανίζεται").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Σήμανση ως ελεγμένο").performScrollTo().performClick()
        scrollHomeTextIntoView("Η οικονομική σου εικόνα")
        composeRule.onNodeWithText("Η οικονομική σου εικόνα").assertIsDisplayed()

        scrollHomeTextIntoView("Ρυθμίσεις & δεδομένα")
        composeRule.onNodeWithText("Ρυθμίσεις").performClick()
        composeRule.onNodeWithText("Προτιμήσεις εφαρμογής").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Πίσω").performClick()

        scrollHomeTextIntoView("Ρυθμίσεις & δεδομένα")
        composeRule.onNodeWithText("Εισαγωγή & αντίγραφα").performClick()
        composeRule.onNodeWithText("Προεπισκόπηση εισαγωγής").performScrollTo().performClick()
        composeRule.onNodeWithText("Αντικατάσταση δεδομένων").performScrollTo().performClick()
        composeRule.onNodeWithText("Αντικατάσταση όλων των δεδομένων;").assertIsDisplayed()
        composeRule.onNodeWithText("Ακύρωση").performClick()
        composeRule.onNodeWithText("Πίσω").performClick()

        scrollHomeTextIntoView("Ρυθμίσεις & δεδομένα")
        composeRule.onNodeWithText("Ιστορικό αλλαγών").performClick()
        composeRule.onNodeWithText("Αναίρεση & επανάληψη").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Αναίρεση").performScrollTo().performClick()
        composeRule.onNodeWithText("2 από 3 αλλαγές εφαρμοσμένες").performScrollTo().assertIsDisplayed()
    }

    private fun scrollHomeTagIntoView(tag: String) {
        val targetAlreadyComposed = runCatching {
            composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        }.isSuccess
        if (targetAlreadyComposed) {
            composeRule.onNodeWithTag(tag).performScrollTo()
        } else {
            composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag(tag))
        }
    }

    private fun scrollHomeTextIntoView(text: String) {
        val targetAlreadyComposed = runCatching {
            composeRule.onNodeWithText(text).fetchSemanticsNode()
        }.isSuccess
        if (targetAlreadyComposed) {
            composeRule.onNodeWithText(text).performScrollTo()
        } else {
            composeRule.onNodeWithTag("home_list").performScrollToNode(hasText(text))
        }
    }
}
