package app.myfinhub.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccessibilitySemanticsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    @Test
    fun criticalSurfaces_haveSpokenLabelsForClickableSemantics() {
        assertClickableNodesHaveSpokenLabels("Home")

        listOf("Κινήσεις", "Χρήματα", "Πλάνο", "Αναλύσεις").forEach { destination ->
            composeRule.onNodeWithText(destination).performClick()
            composeRule.waitForIdle()
            assertClickableNodesHaveSpokenLabels(destination)
        }
    }

    private fun assertClickableNodesHaveSpokenLabels(surface: String) {
        val unlabeled = composeRule
            .onAllNodes(hasClickAction(), useUnmergedTree = false)
            .fetchSemanticsNodes()
            .filter { node ->
                val text = node.config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .joinToString(separator = " ") { it.text }
                val contentDescription = node.config
                    .getOrNull(SemanticsProperties.ContentDescription)
                    .orEmpty()
                    .joinToString(separator = " ")
                val editableText = node.config
                    .getOrNull(SemanticsProperties.EditableText)
                    ?.text
                    .orEmpty()

                text.isBlank() && contentDescription.isBlank() && editableText.isBlank()
            }

        assertTrue(
            "$surface contains clickable semantics without a spoken label: ${unlabeled.joinToString { it.id.toString() }}",
            unlabeled.isEmpty(),
        )
    }
}
