package app.myfinhub.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.filters.SdkSuppress
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

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun criticalSurfaces_passAccessibilityFrameworkChecks() {
        composeRule.enableAccessibilityChecks()
        composeRule.onRoot().tryPerformAccessibilityChecks()

        listOf("Κινήσεις", "Χρήματα", "Πλάνο", "Αναλύσεις").forEach { destination ->
            composeRule.onNodeWithText(destination).performClick()
            composeRule.waitForIdle()
            composeRule.onRoot().tryPerformAccessibilityChecks()
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
