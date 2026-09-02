package app.myfinhub.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

        openQuickEntryFromHome()
        assertClickableNodesHaveSpokenLabels("Quick Entry")
        leaveDirtyQuickEntry()

        composeRule.onNodeWithText("Ρυθμίσεις").performClick()
        composeRule.waitForIdle()
        assertClickableNodesHaveSpokenLabels("Settings")
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun criticalSurfaces_passAccessibilityFrameworkChecks() {
        composeRule.enableAccessibilityChecks()
        checkCurrentSurface()

        listOf("Κινήσεις", "Χρήματα", "Πλάνο", "Αναλύσεις").forEach { destination ->
            composeRule.onNodeWithText(destination).performClick()
            composeRule.waitForIdle()
            checkCurrentSurface()
        }

        openQuickEntryFromHome()
        checkCurrentSurface()
        leaveDirtyQuickEntry()

        composeRule.onNodeWithText("Ρυθμίσεις").performClick()
        composeRule.waitForIdle()
        checkCurrentSurface()
    }

    private fun openQuickEntryFromHome() {
        composeRule.onNodeWithText("Αρχική").performClick()
        composeRule.onNodeWithText("Νέα κίνηση", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Έξοδο").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Πλήρης καταχώριση").assertExists()
    }

    private fun leaveDirtyQuickEntry() {
        composeRule.onNodeWithContentDescription("Πίσω").performClick()
        composeRule.onNodeWithText("Απόρριψη αλλαγών;").assertExists()
        composeRule.onNodeWithText("Απόρριψη").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Η οικονομική σου εικόνα").assertExists()
    }

    private fun checkCurrentSurface() {
        composeRule.onRoot().tryPerformAccessibilityChecks()
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
