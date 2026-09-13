package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

class FrontendUtilitiesParityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    @Test
    fun homeReviewAndSettingsFlow_areReachableWithoutUtilityCardsInFinancialFeed() {
        scrollHomeTagIntoView("attention-scheduled-review")
        composeRule.onNodeWithTag("attention-scheduled-review").assertIsDisplayed().performClick()
        waitForText("Στοιχεία υποχρέωσης")
        assertTextIntoView("Στοιχεία υποχρέωσης")
        clickTextIntoView("Απόκρυψη για τώρα")
        returnHomeListToTop()
        composeRule.onNodeWithText("Η οικονομική σου εικόνα").assertIsDisplayed()

        clickTextIntoView("Ρυθμίσεις")
        waitForText("Προτιμήσεις εφαρμογής")
        assertTextIntoView("Προτιμήσεις εφαρμογής")
        composeRule.onNodeWithContentDescription("Πίσω").assertIsDisplayed().performClick()
        returnHomeListToTop()
        composeRule.onNodeWithText("Η οικονομική σου εικόνα").assertIsDisplayed()
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
        val node = composeRule.onNode(hasText(text) and hasClickAction())
        runCatching { node.performScrollTo() }
        node.assertIsDisplayed().performClick()
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

    private fun returnHomeListToTop() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("home_list").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithTag("home_list").performScrollToIndex(0)
    }
}
