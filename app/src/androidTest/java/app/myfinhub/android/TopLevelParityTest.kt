package app.myfinhub.android

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TopLevelParityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    private fun selectDestination(label: String) {
        composeRule.onNode(
            hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).performClick()
    }

    private fun selectAnalysis() {
        selectDestination("Κινήσεις")
        composeRule.onNodeWithTag("activity_section_analysis").performClick()
    }

    private fun assertGlobalNavigationHidden() {
        val globalDestinationMissing = runCatching {
            composeRule.onNodeWithText("Πορτοφόλι").fetchSemanticsNode()
        }.isFailure
        assertTrue("Secondary routes must hide the global navigation suite", globalDestinationMissing)
    }

    @Test
    fun fourRoots_walletPlanAndAnalysis_haveRealMobileContent() {
        selectDestination("Πορτοφόλι")
        composeRule.onNodeWithTag("wallet_list").assertIsDisplayed()
        composeRule.onNode(hasText("Κάρτες") and hasClickAction()).performClick()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("credit_card_stack"))
        composeRule.onNodeWithTag("credit_card_card-1")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("card_secure_details")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText(
            "Ο αριθμός, η λήξη και το CVV αποθηκεύονται κρυπτογραφημένα στη συσκευή και εμφανίζονται πλήρως όσο χρησιμοποιείς την εφαρμογή.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Αποκάλυψη στοιχείων").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.onNodeWithTag("card_secure_details").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.onNodeWithTag("wallet_list").assertIsDisplayed()

        selectDestination("Πλάνο")
        composeRule.onNodeWithTag("s7_plan_root").assertIsDisplayed()
        composeRule.onNodeWithTag("s7_forecast_link").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("s7_budget_link").performScrollTo().assertIsDisplayed()

        selectAnalysis()
        composeRule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_details"))
        composeRule.onNodeWithText("Έσοδα, καθαρό & πορεία").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_list")
            .performScrollToNode(hasText("Πού πηγαίνουν τα έξοδα"))
        composeRule.onNodeWithText("Πού πηγαίνουν τα έξοδα").assertIsDisplayed()
    }

    @Test
    fun plan_drillsIntoForecastAndBudgetWorkflows() {
        selectDestination("Πλάνο")

        composeRule.onNodeWithTag("s7_forecast_link").performScrollTo().performClick()
        composeRule.onNodeWithTag("s7_forecast_root").assertIsDisplayed()
        composeRule.onNodeWithText("Κινήσεις που υπολογίζονται").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()

        composeRule.onNodeWithTag("s7_budget_link").performScrollTo().performClick()
        composeRule.onNodeWithTag("s7_budget_root").assertIsDisplayed()
        composeRule.onNodeWithTag("s7_budget_limit").assertIsDisplayed()
        composeRule.onNodeWithTag("s7_budget_save").assertIsDisplayed()
    }

    @Test
    fun secondaryRoute_hidesGlobalNavigation_andBackRestoresOrigin() {
        selectDestination("Κινήσεις")
        composeRule.onNode(hasText("Σούπερ μάρκετ") and hasClickAction())
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Λεπτομέρειες κίνησης").assertIsDisplayed()
        assertGlobalNavigationHidden()

        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Πορτοφόλι").assertIsDisplayed()
    }

    @Test
    fun analysis_categoryDeepLink_preservesOrigin_andSiblingAcrossTabSwitches() {
        selectAnalysis()
        composeRule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_category_Τρόφιμα"))
        composeRule.onNodeWithTag("insights_category_Τρόφιμα")
            .performClick()

        composeRule.onNodeWithText("Τρόφιμα").assertIsDisplayed()
        composeRule.onNode(hasText("Σούπερ μάρκετ") and hasClickAction()).assertIsDisplayed()
        assertGlobalNavigationHidden()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.onNodeWithTag("insights_list").assertIsDisplayed()

        selectDestination("Πλάνο")
        selectDestination("Κινήσεις")
        composeRule.onNodeWithTag("insights_list").assertIsDisplayed()
        composeRule.onNodeWithTag("activity_section_history").performClick()
        composeRule.onNodeWithText("Αναζήτηση κινήσεων", useUnmergedTree = true).assertIsDisplayed()
    }
}