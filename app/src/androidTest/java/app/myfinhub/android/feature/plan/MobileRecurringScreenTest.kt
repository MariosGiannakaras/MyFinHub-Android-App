package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MobileRecurringScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun emptySubscriptionsStillShowSafeReadOnlyOverview() {
        rule.setContent {
            MyFinHubTheme {
                MobileRecurringOverviewScreen(items = emptyList(), onBack = {})
            }
        }
        rule.onNodeWithTag("mobile_recurring_overview").assertExists()
        rule.onNodeWithTag("mobile_recurring_empty").assertExists()
        rule.onNodeWithText("Προβολή · διαχείριση από το Web").assertExists()
    }

    @Test fun duplicateSourceIdsAndActivePausedFiltersDoNotLoseEntries() {
        val entries = listOf(
            RecurringManageItem(id = "duplicate", title = "Netflix", amount = 15.0, status = "active"),
            RecurringManageItem(id = "duplicate", title = "Spotify", amount = 11.0, status = "paused"),
        )
        rule.setContent {
            MyFinHubTheme {
                MobileRecurringOverviewScreen(items = entries, onBack = {})
            }
        }
        rule.onNodeWithTag("mobile_recurring_row_0").assertExists()
        rule.onNodeWithTag("mobile_recurring_row_1").assertExists()
        rule.onNodeWithTag("mobile_recurring_filter_active").performClick()
        assertTrue(runCatching { rule.onNodeWithTag("mobile_recurring_row_1").fetchSemanticsNode() }.isFailure)
        rule.onNodeWithTag("mobile_recurring_filter_inactive").performClick()
        rule.onNodeWithTag("mobile_recurring_row_1").assertExists()
    }
}
