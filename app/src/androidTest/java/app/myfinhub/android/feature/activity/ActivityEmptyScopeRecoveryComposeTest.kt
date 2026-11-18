package app.myfinhub.android.feature.activity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Recover from an empty source month or an invisible whitespace-only filter. */
class ActivityEmptyScopeRecoveryComposeTest {
    @get:Rule val rule = createComposeRule()

    private val november = ActivityMonthlyOverview(
        month = "2026-11", income = 0.0, expense = 0.0, net = 0.0,
        transactionCount = 0, previousIncome = 0.0, previousExpense = 7.0,
        previousNet = -7.0, previousTransactionCount = 1,
    )
    private val october = ActivityItem(
        "previous", "21 Οκτ", ActivityKind.EXPENSE, "Παλιά κίνηση", "", -7.0,
        "Μετρητά", "Άλλο", rawDate = "2026-10-21",
        canonicalSourceOrdinal = 0, note = "Παλιά κίνηση",
    )
    private val current = ActivityItem(
        "current", "7 Νοε", ActivityKind.EXPENSE, "Κίνηση του μήνα", "", -8.0,
        "Μετρητά", "Τρόφιμα", rawDate = "2026-11-07",
        canonicalSourceOrdinal = 1, note = "Κίνηση του μήνα",
    )

    @Test
    fun emptySelectedMonthProvidesDirectFullHistoryAction() {
        var state by mutableStateOf(ActivityUiState(
            items = listOf(october), monthOverview = november, monthSourceSelected = true,
        ))
        var createRequests = 0
        rule.setContent {
            MyFinHubTheme {
                ActivityLedgerScreen(state = state,
                    onAction = { state = reduceActivity(state, it) },
                    onOpenDetail = {}, onOpenQuickEntry = { createRequests++ })
            }
        }
        rule.onNodeWithTag("activity_list")
            .performScrollToNode(hasText("Δεν υπάρχουν συναλλαγές σε αυτή την περίοδο."))
        rule.onNodeWithText("Όλο το ιστορικό").assertIsDisplayed().performClick()
        rule.runOnIdle {
            assertFalse(state.showingOverviewMonth)
            assertEquals(listOf("previous"), state.visibleItems.map { it.id })
            assertEquals(0, createRequests)
        }
        rule.onNodeWithTag("activity_list").performScrollToNode(hasText("Παλιά κίνηση"))
        rule.onNodeWithText("Παλιά κίνηση").assertIsDisplayed()
    }

    @Test
    fun populatedMonthDisclosesRestrictedSourceAndExitsToFullHistory() {
        var state by mutableStateOf(ActivityUiState(
            items = listOf(current, october),
            monthOverview = november.copy(transactionCount = 1),
            monthSourceSelected = true,
        ))
        rule.setContent {
            MyFinHubTheme {
                ActivityLedgerScreen(state = state,
                    onAction = { state = reduceActivity(state, it) },
                    onOpenDetail = {}, onOpenQuickEntry = {})
            }
        }
        rule.onNodeWithTag("activity_month_source_indicator").assertExists()
        rule.onNodeWithTag("activity_show_all_history").assertExists().performClick()
        rule.runOnIdle {
            assertFalse(state.showingOverviewMonth)
            assertEquals(listOf("current", "previous"), state.visibleItems.map { it.id })
        }
        rule.onNodeWithTag("activity_month_source_indicator").assertDoesNotExist()
        rule.onNodeWithTag("activity_list").performScrollToNode(hasText("Παλιά κίνηση"))
        rule.onNodeWithText("Παλιά κίνηση").assertIsDisplayed()
    }

    @Test
    fun whitespaceOnlySearchHasAnActionableClearFilterButton() {
        var state by mutableStateOf(ActivityUiState(items = listOf(october), query = "   "))
        rule.setContent {
            MyFinHubTheme {
                ActivityLedgerScreen(state = state,
                    onAction = { state = reduceActivity(state, it) },
                    onOpenDetail = {}, onOpenQuickEntry = {})
            }
        }
        rule.runOnIdle {
            assertTrue(state.hasActiveListScope)
            assertTrue(state.visibleItems.isEmpty())
        }
        rule.onNodeWithTag("activity_list")
            .performScrollToNode(hasText("Καθαρισμός αναζήτησης και φίλτρων"))
        rule.onNodeWithText("Καθαρισμός αναζήτησης και φίλτρων")
            .assertIsDisplayed().performClick()
        rule.runOnIdle {
            assertFalse(state.hasActiveListScope)
            assertEquals("", state.query)
            assertEquals(listOf("previous"), state.visibleItems.map { it.id })
        }
        rule.onNodeWithTag("activity_list").performScrollToNode(hasText("Παλιά κίνηση"))
        rule.onNodeWithText("Παλιά κίνηση").assertIsDisplayed()
    }
}
