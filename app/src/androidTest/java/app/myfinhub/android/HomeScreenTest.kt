package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    @Test
    fun home_showsDecisionRelevantSections_andSupportsQuickEntry() {
        composeRule.onNodeWithText("Η οικονομική σου εικόνα").assertIsDisplayed()

        composeRule.onNodeWithText("Εμφάνιση ποσών").performClick()
        composeRule.onNodeWithText("Απόκρυψη ποσών").assertIsDisplayed()

        // NavigationSuiteScaffold can reduce the actual Home content width below the physical
        // device width, especially on foldables. Infer the Compose branch from the semantics tree
        // instead of guessing it from Configuration.screenWidthDp.
        val expandedHome = runCatching {
            composeRule.onNodeWithText("Χρειάζεται προσοχή").fetchSemanticsNode()
        }.isSuccess
        if (expandedHome) {
            // Expanded Home uses regular vertically-scrollable columns, whose children remain
            // composed even when outside the viewport.
            composeRule.onNodeWithText("Χρειάζεται προσοχή")
                .performScrollTo()
                .assertIsDisplayed()
        } else {
            // Compact Home uses a LazyColumn, so the off-screen section must first be composed by
            // scrolling the owning lazy container to that semantic target.
            composeRule.onNode(hasScrollAction())
                .performScrollToNode(hasText("Χρειάζεται προσοχή"))
            composeRule.onNodeWithText("Χρειάζεται προσοχή").assertIsDisplayed()
        }

        if (expandedHome) {
            composeRule.onNodeWithText("Επίλεξε τύπο κίνησης", useUnmergedTree = true)
                .performScrollTo()
                .performClick()
        } else {
            composeRule.onNodeWithText("Νέα κίνηση", useUnmergedTree = true).performClick()
        }
        composeRule.onNodeWithText("Έξοδο").assertIsDisplayed().performClick()
        // Existence proves the state transition without requiring the confirmation marker to be
        // inside the current viewport on every supported screen/font configuration.
        composeRule.onNodeWithText("Επιλέχθηκε: Έξοδο").fetchSemanticsNode()
    }
}
