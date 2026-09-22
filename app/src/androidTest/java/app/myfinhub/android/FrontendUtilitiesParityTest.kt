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
        composeRule.onNodeWithTag("home_list")
            .performScrollToNode(hasText("Έλεγχος προγραμματισμένης πληρωμής"))
        composeRule.onNode(hasText("Έλεγχος προγραμματισμένης πληρωμής") and hasClickAction())
            .assertIsDisplayed()
            .performClick()
        waitForText("Στοιχεία υποχρέωσης")
        assertTextIntoView("Στοιχεία υποχρέωσης")
        clickTextIntoView("Απόκρυψη για τώρα")
        returnHomeListToTop()
        composeRule.onNodeWithTag("home_list").assertIsDisplayed()
        composeRule.onNodeWithText("Διαθέσιμα τώρα").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Ρυθμίσεις").assertIsDisplayed().performClick()
        waitForText("Ρυθμίσεις")
        composeRule.onNodeWithTag("s9_settings_preferences").assertIsDisplayed()
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
