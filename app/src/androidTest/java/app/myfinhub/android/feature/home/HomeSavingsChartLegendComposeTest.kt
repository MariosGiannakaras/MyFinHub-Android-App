package app.myfinhub.android.feature.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

class HomeSavingsChartLegendComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun legendTracksDrawnComparisonAndTargetWithoutRevealingAmounts() {
        val original = syntheticHomeUiState()
        val ui = mutableStateOf(original)
        rule.setContent {
            MyFinHubTheme {
                ProductionHomeScreen(
                    state = ui.value, onAction = {}, onOpenAttention = {},
                    onOpenQuickEntry = {}, amountsVisibleOverride = false,
                )
            }
        }
        rule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_savings_legend"))
        rule.onNodeWithText("Ιστορικό").assertExists()
        rule.onNodeWithText("Σύγκριση προηγ. μήνα").assertExists()
        rule.onNodeWithText("Στόχος").assertExists()
        rule.runOnIdle {
            ui.value = original.copy(accounts = original.accounts.map {
                if (it.primaryRole == HomePrimaryRole.SAVINGS)
                    it.copy(previousMonthTrend = emptyList(), savingsTarget = 1_000_000.0)
                else it
            })
        }
        rule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_savings_legend"))
        rule.onNodeWithText("Ιστορικό").assertExists()
        rule.onNodeWithText("Σύγκριση προηγ. μήνα").assertDoesNotExist()
        rule.onNodeWithText("Στόχος").assertDoesNotExist()
    }
}
