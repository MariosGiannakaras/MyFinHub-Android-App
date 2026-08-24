package app.myfinhub.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
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

        val screenWidthDp = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .resources
            .configuration
            .screenWidthDp
        if (screenWidthDp < 840) {
            // Compact Home uses a LazyColumn, so an off-screen section is not composed yet.
            // Scroll through the owning lazy container before asking for the section node.
            composeRule.onNode(hasScrollAction())
                .performScrollToNode(hasText("Χρειάζεται προσοχή"))
            composeRule.onNodeWithText("Χρειάζεται προσοχή").assertIsDisplayed()
        } else {
            // Expanded Home uses regular vertically-scrollable columns, whose children are
            // composed even when outside the viewport.
            composeRule.onNodeWithText("Χρειάζεται προσοχή")
                .performScrollTo()
                .assertIsDisplayed()
        }

        composeRule.onNodeWithText("Νέα κίνηση", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Έξοδο").assertIsDisplayed().performClick()
        // Existence proves the state transition without requiring the confirmation marker to be
        // inside the current viewport on every supported screen/font configuration.
        composeRule.onNodeWithText("Επιλέχθηκε: Έξοδο").fetchSemanticsNode()
    }
}
