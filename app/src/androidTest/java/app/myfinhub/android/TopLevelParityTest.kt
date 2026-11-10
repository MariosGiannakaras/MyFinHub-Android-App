package app.myfinhub.android

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests the simplified daily-use navigation; domain details have independent screen tests. */
class TopLevelParityTest {
    @get:Rule val composeRule = createAndroidComposeRule<ProductTestActivity>()

    private fun selectDestination(label: String) {
        composeRule.onNode(
            hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).performClick()
    }

    private fun assertGlobalNavigationHidden() {
        assertTrue(
            "Secondary routes must hide the global navigation suite",
            runCatching { composeRule.onNodeWithText("Κάρτες").fetchSemanticsNode() }.isFailure,
        )
    }

    @Test
    fun onlyThreeDailyUseRoots_andCardDetailsRemainReachable() {
        composeRule.onNodeWithTag("home_list").assertIsDisplayed()
        composeRule.onNodeWithText("Διαθέσιμα τώρα").assertIsDisplayed()
        composeRule.onNodeWithText("Πλάνο").assertDoesNotExist()
        composeRule.onNodeWithText("Αναφορές").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Ρυθμίσεις").assertDoesNotExist()

        selectDestination("Κινήσεις")
        composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true)
            .assertIsDisplayed()
        selectDestination("Κάρτες")
        composeRule.onNodeWithTag("wallet_list").assertIsDisplayed()
        composeRule.onNodeWithTag("cards_primary_action").assertIsDisplayed()
        composeRule.onNodeWithText("Νέα κάρτα").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Αρχειοθέτηση κάρτας").assertDoesNotExist()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("credit_card_stack"))
        composeRule.onNodeWithTag("credit_card_card-1")
            .assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("card_secure_details")
            .assertIsDisplayed().performClick()
        composeRule.onNodeWithText(
            "Ο αριθμός, η λήξη και το CVV αποθηκεύονται κρυπτογραφημένα στο κοινό card vault και συγχρονίζονται στις εγκεκριμένες εφαρμογές.",
        ).assertIsDisplayed()
        assertTrue(runCatching {
            composeRule.onNodeWithText("Αποκάλυψη στοιχείων").fetchSemanticsNode()
        }.isFailure)
        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.onNodeWithTag("card_secure_details").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.onNodeWithTag("wallet_list").assertIsDisplayed()
    }

    @Test
    fun quickEntryRemainsOneActionAwayFromHome() {
        composeRule.onNodeWithTag("home_global_quick_entry").performClick()
        composeRule.onNodeWithText("Ποσό εξόδου").assertIsDisplayed()
        assertGlobalNavigationHidden()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.onNodeWithTag("home_list").assertIsDisplayed()
        selectDestination("Κινήσεις")
        composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun activityDetail_hidesNavigation_andBackRestoresHistory() {
        selectDestination("Κινήσεις")
        composeRule.onNode(hasText("Σούπερ μάρκετ") and hasClickAction())
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Λεπτομέρειες κίνησης").assertIsDisplayed()
        assertGlobalNavigationHidden()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Κάρτες").assertIsDisplayed()
    }

    @Test
    fun switchingTabs_keepsHistoryAvailable() {
        selectDestination("Κινήσεις")
        composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true)
            .assertIsDisplayed()
        selectDestination("Κάρτες")
        composeRule.onNodeWithTag("wallet_list").assertIsDisplayed()
        selectDestination("Κινήσεις")
        composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
