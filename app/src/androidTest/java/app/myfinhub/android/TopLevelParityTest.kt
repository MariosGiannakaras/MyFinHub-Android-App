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
        composeRule.onNodeWithText("Λογαριασμοί").assertIsDisplayed()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("credit_card_stack"))
        composeRule.onNodeWithTag("credit_card_stack")
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag("credit_card_stack")
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithText(
            "PAN/λήξη αποκαλύπτονται μόνο από το owner+AAL2 server vault. Το CVV παραμένει αποκλειστικά σε κρυπτογραφημένο vault αυτής της συσκευής.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Αποκάλυψη ασφαλών στοιχείων").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()

        selectDestination("Πλάνο")
        composeRule.onNodeWithText("Επόμενες υποχρεώσεις").assertIsDisplayed()
        composeRule.onNode(hasText("Budgets", substring = true) and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()

        selectAnalysis()
        composeRule.onNodeWithText("Πορεία 4 μηνών").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_list")
            .performScrollToNode(hasText("Πού πηγαίνουν τα έξοδα"))
        composeRule.onNodeWithText("Πού πηγαίνουν τα έξοδα").assertIsDisplayed()
    }

    @Test
    fun plan_drillsIntoItemAndBudgetWorkflows() {
        selectDestination("Πλάνο")

        composeRule.onNode(hasText("Ενοίκιο") and hasClickAction())
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Επεξεργασία").assertIsDisplayed()
        composeRule.onNodeWithText("Αποθήκευση").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Πίσω").performClick()

        composeRule.onNode(hasText("Budgets", substring = true) and hasClickAction())
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Συνολικό μηνιαίο budget").assertIsDisplayed()
        composeRule.onNodeWithText("Budgets ανά κατηγορία").assertIsDisplayed()
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
            .performScrollToNode(hasText("Τρόφιμα"))
        composeRule.onNodeWithContentDescription("Προβολή κινήσεων κατηγορίας Τρόφιμα")
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
