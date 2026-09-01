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
        waitForText("Γιατί εμφανίζεται")
        assertTextIntoView("Γιατί εμφανίζεται")
        clickTextIntoView("Σήμανση ως ελεγμένο")
        scrollHomeTextIntoView("Η οικονομική σου εικόνα")
        composeRule.onNodeWithText("Η οικονομική σου εικόνα").assertIsDisplayed()

        scrollHomeTextIntoView("Ρυθμίσεις & δεδομένα")
        clickTextIntoView("Ρυθμίσεις")
        waitForText("Προτιμήσεις εφαρμογής")
        assertTextIntoView("Προτιμήσεις εφαρμογής")
        clickTextIntoView("Πίσω")

        scrollHomeTextIntoView("Ρυθμίσεις & δεδομένα")
        clickTextIntoView("Εισαγωγή & αντίγραφα")
        waitForText("Προεπισκόπηση εισαγωγής")
        clickTextIntoView("Προεπισκόπηση εισαγωγής")
        clickTextIntoView("Αντικατάσταση δεδομένων")
        waitForText("Αντικατάσταση όλων των δεδομένων;")
        composeRule.onNodeWithText("Αντικατάσταση όλων των δεδομένων;").assertIsDisplayed()
        clickTextIntoView("Ακύρωση")
        clickTextIntoView("Πίσω")

        scrollHomeTextIntoView("Ρυθμίσεις & δεδομένα")
        clickTextIntoView("Ιστορικό αλλαγών")
        waitForText("Αναίρεση & επανάληψη")
        assertTextIntoView("Αναίρεση & επανάληψη")
        clickTextIntoView("Αναίρεση")
        waitForText("2 από 3 αλλαγές εφαρμοσμένες")
        assertTextIntoView("2 από 3 αλλαγές εφαρμοσμένες")
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertTextIntoView(text: String) {
        val node = composeRule.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.assertIsDisplayed()
    }

    private fun clickTextIntoView(text: String) {
        assertTextIntoView(text)
        composeRule.onNodeWithText(text).performClick()
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
