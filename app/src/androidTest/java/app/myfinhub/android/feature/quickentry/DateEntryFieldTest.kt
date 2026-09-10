package app.myfinhub.android.feature.quickentry

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

class DateEntryFieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sharedDateField_usesGreekDisplayAndPickerCopy() {
        composeRule.setContent {
            MyFinHubTheme {
                DateEntryField(
                    value = "2026-09-02",
                    onValueChange = {},
                    label = "Ημερομηνία",
                    errorMessage = null,
                )
            }
        }

        composeRule.onNodeWithText("2 Σεπτεμβρίου 2026")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Επιλογή ημερομηνίας").assertIsDisplayed()
        composeRule.onAllNodesWithText("September", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Select date", substring = true).assertCountEquals(0)
    }
}
