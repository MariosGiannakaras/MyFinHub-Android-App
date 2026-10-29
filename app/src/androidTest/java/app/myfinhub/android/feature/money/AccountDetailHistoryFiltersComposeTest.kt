package app.myfinhub.android.feature.money

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountDetailHistoryFiltersComposeTest {
    @get:Rule val rule = createComposeRule()
    private val account = MoneyAccount(
        id = "payroll", name = "Μισθοδοσία", balance = 900.0,
        kind = "Τράπεζα", canonicalKind = "bank",
    )
    private val records = (1..12).map { index ->
        ActivityItem(
            id = "event-$index",
            dateLabel = "2026-10-${index.toString().padStart(2, '0')}",
            kind = if (index % 2 == 0) ActivityKind.INCOME else ActivityKind.EXPENSE,
            title = if (index % 2 == 0) "Έσοδο $index" else "Έξοδο $index",
            subtitle = if (index % 2 == 0) "Μισθοδοσία" else "Αγορά τροφίμων",
            amount = if (index % 2 == 0) 100.0 else -30.0,
            accountLabel = "Μισθοδοσία",
            category = if (index % 2 == 0) "Εργασία" else "Τρόφιμα",
            rawDate = "2026-10-${index.toString().padStart(2, '0')}",
        )
    }

    @Test fun duplicateOrBlankSourceIdsStayVisibleButCannotOpenAnotherEvent() {
        val scoped = listOf(
            records[0].copy(id = "duplicate", title = "Αγορά πρώτη"),
            records[1].copy(id = "duplicate", title = "Αγορά δεύτερη"),
            records[2].copy(id = "", title = "Χωρίς κωδικό"),
            records[3].copy(id = "safe", title = "Έγκυρη κίνηση"),
        )
        var opened: String? = null
        rule.setContent { MyFinHubTheme {
            CanonicalAccountDetailScreen(
                account = account,
                activityItems = scoped,
                fullActivityIdCounts = mapOf("duplicate" to 2, "" to 1, "safe" to 1),
                onBack = {},
                onOpenActivity = { opened = it },
                amountsVisibleOverride = false,
            )
        } }
        rule.onNodeWithTag("account_history_source_0").assertIsNotEnabled()
        rule.onNodeWithTag("account_history_source_1").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("account_history_source_2").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("account_history_source_3").performScrollTo()
            .assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals("safe", opened) }
        rule.onNodeWithTag("account_history_identity_warning_0").assertExists()
        rule.onNodeWithTag("account_history_identity_warning_2").assertExists()
    }

    @Test fun duplicateIdOnAnotherAccountAlsoDisablesThisAccountsRow() {
        // This account has only one row; the full canonical ledger has a collision elsewhere.
        var opened: String? = null
        rule.setContent { MyFinHubTheme {
            CanonicalAccountDetailScreen(
                account = account,
                activityItems = listOf(records[0].copy(id = "cross-account")),
                fullActivityIdCounts = mapOf("cross-account" to 2),
                onBack = {},
                onOpenActivity = { opened = it },
                amountsVisibleOverride = false,
            )
        } }
        rule.onNodeWithTag("account_history_source_0").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(null, opened) }
    }

    @Test fun searchingAndKindFilteringStayInSameAccountAndKeepPrivacy() {
        val opened = mutableStateOf<String?>(null)
        rule.setContent { MyFinHubTheme {
            CanonicalAccountDetailScreen(
                account = account, activityItems = records,
                onBack = {}, onOpenActivity = { opened.value = it },
                amountsVisibleOverride = false,
            )
        } }
        rule.onNodeWithTag("account_detail_history_list")
            .performScrollToNode(hasTestTag("account_history_search"))
        rule.onNodeWithTag("account_history_search").performTextInput("τροφίμων")
        rule.onNodeWithTag("account_history_result_count").assertExists()
        rule.onNodeWithTag("account_history_kind_EXPENSE").performScrollTo().performClick()
        rule.onNodeWithTag("account_detail_history_list")
            .performScrollToNode(hasTestTag("account_history_result_count"))
        assertTrue(rule.onAllNodesWithText("6 από 12 κινήσεις").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("account_history_kind_INCOME").performScrollTo().performClick()
        assertTrue(rule.onAllNodesWithText("Δεν βρέθηκαν κινήσεις με τα επιλεγμένα φίλτρα.")
            .fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("account_history_reset_filters")
            .performScrollTo().performClick()
        rule.onNodeWithTag("account_history_result_count").performScrollTo()
        assertTrue(rule.onAllNodesWithText("12 από 12 κινήσεις")
            .fetchSemanticsNodes().isNotEmpty())
        rule.runOnIdle { assertEquals(null, opened.value) }
    }

    @Test fun searchAndTypeSurviveActivityStateRestorationForSameAccount() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { MyFinHubTheme {
            CanonicalAccountDetailScreen(
                account = account,
                activityItems = records,
                onBack = {},
                onOpenActivity = {},
                amountsVisibleOverride = false,
            )
        } }
        rule.onNodeWithTag("account_detail_history_list")
            .performScrollToNode(hasTestTag("account_history_search"))
        rule.onNodeWithTag("account_history_search").performTextInput("τροφίμων")
        rule.onNodeWithTag("account_history_kind_EXPENSE").performScrollTo().performClick()
        rule.onNodeWithTag("account_history_result_count").performScrollTo()
        assertTrue(rule.onAllNodesWithText("6 από 12 κινήσεις").fetchSemanticsNodes().isNotEmpty())
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("account_detail_history_list")
            .performScrollToNode(hasTestTag("account_history_result_count"))
        assertTrue(rule.onAllNodesWithText("6 από 12 κινήσεις").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("account_history_reset_filters").performScrollTo().assertExists()
    }

    @Test fun switchingAccountResetsSearchAndShowsItsOwnHistory() {
        val selected = mutableStateOf(account)
        rule.setContent { MyFinHubTheme {
            CanonicalAccountDetailScreen(
                account = selected.value,
                activityItems = records,
                onBack = {}, onOpenActivity = {},
                amountsVisibleOverride = true,
            )
        } }
        rule.onNodeWithTag("account_detail_history_list")
            .performScrollToNode(hasTestTag("account_history_search"))
        rule.onNodeWithTag("account_history_search").performTextInput("κανένα")
        rule.onNodeWithTag("account_history_kind_EXPENSE").performScrollTo().performClick()
        rule.runOnIdle { selected.value = account.copy(id = "savings", name = "Αποταμίευση") }
        rule.waitForIdle()
        rule.onNodeWithTag("account_detail_history_list")
            .performScrollToNode(hasTestTag("account_history_search"))
        assertTrue(rule.onAllNodesWithText("12 από 12 κινήσεις").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("account_history_kind_ALL").performScrollTo().assertExists()
        assertTrue(rule.onAllNodesWithText("Καθαρισμός φίλτρων").fetchSemanticsNodes().isEmpty())
    }
}
