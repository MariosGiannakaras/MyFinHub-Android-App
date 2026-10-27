package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FrontendUtilitiesParityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    @Test
    fun homeRemainsAccountFocused_planOwnsObligations_settingsAreDeviceOnly() {
        composeRule.onNodeWithTag("home_list").assertIsDisplayed()
        assertTrue(runCatching { composeRule.onNodeWithText("Χρειάζεται προσοχή").fetchSemanticsNode() }.isFailure)

        // Scheduled payments are owned by Plan, never embedded in the Home account feed.
        composeRule.onNodeWithText("Πλάνο").performClick()
        composeRule.onNodeWithTag("s7_plan_root").assertIsDisplayed()
        composeRule.onNodeWithTag("s7_forecast_link").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Αρχική").performClick()

        returnHomeListToTop()
        composeRule.onNodeWithContentDescription("Ρυθμίσεις").assertIsDisplayed().performClick()
        waitForText("Εφαρμογή")
        composeRule.onNodeWithText("Ρυθμίσεις συσκευής").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Εμφάνιση ποσών").assertIsDisplayed()
        composeRule.onNodeWithText("Ειδοποιήσεις Android").assertIsDisplayed()
        assertTrue(runCatching { composeRule.onNodeWithText("Διαχείριση κατηγοριών").fetchSemanticsNode() }.isFailure)
        composeRule.onNodeWithContentDescription("Πίσω").assertIsDisplayed().performClick()

        returnHomeListToTop()
        composeRule.onNodeWithTag("home_list").assertIsDisplayed()
        composeRule.onNodeWithText("Διαθέσιμα τώρα").assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun returnHomeListToTop() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("home_list").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithTag("home_list").performScrollToIndex(0)
    }
}
