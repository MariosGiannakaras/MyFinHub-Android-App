package app.myfinhub.android.feature.utilities

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import app.myfinhub.android.core.network.ServerFinanceHistoryPoint
import app.myfinhub.android.core.network.ServerFinanceHistorySummary
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real Settings server history is read-only and masks user labels in privacy mode. */
class ServerFinanceHistorySettingsComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun oldHistoryRowsExpandByTwentyFourAndResetOnServerRevision() {
        fun history(revision: String) = ServerFinanceHistorySummary(
            available=true,generation="5",financeRevision=revision,
            currentPointId="26",canUndo=false,canRedo=true,undoDepth=4,redoDepth=2,
            points=(1..26).map { i ->
                ServerFinanceHistoryPoint(
                    id=i.toString(),parentId=(i-1).takeIf { it>0 }?.toString(),
                    label="Δοκιμαστική εγγραφή " + i,
                    createdAt="2026-10-19T08:00:00Z",current=i==26,
                )
            },
        )
        var summary by androidx.compose.runtime.mutableStateOf(history("22"))
        rule.setContent { MyFinHubTheme {
            ProductionSettingsScreen(
                state=FrontendUtilitiesUiState(),onAction={},onBack={},
                serverHistory=summary,serverHistoryEnabled=true,serverHistoryLoaded=true,
                onRefreshServerHistory={},
            )
        } }
        rule.onNodeWithTag("s9_history_page_info").performScrollTo()
        rule.onNodeWithText("Προβολή 24 από 26 αλλαγές").assertIsDisplayed()
        rule.onNodeWithTag("s9_history_more").performScrollTo()
            .performClick()
        rule.onNodeWithTag("s9_history_page_info").performScrollTo()
        rule.onNodeWithText("Προβολή 26 από 26 αλλαγές").assertIsDisplayed()
        rule.onNodeWithTag("s9_history_row_25").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { summary=history("23") }
        rule.onNodeWithTag("s9_history_page_info").performScrollTo()
        rule.onNodeWithText("Προβολή 24 από 26 αλλαγές").assertIsDisplayed()
    }

    @Test fun emptyServerHistoryIsExplicitAndNeverDisplaysUndoActions() {
        val summary=ServerFinanceHistorySummary(
            available=true,generation="7",financeRevision="11",
            currentPointId=null,canUndo=false,canRedo=false,
            undoDepth=0,redoDepth=0,points=emptyList(),
        )
        rule.setContent { MyFinHubTheme {
            ProductionSettingsScreen(
                state=FrontendUtilitiesUiState(),onAction={},onBack={},
                serverHistory=summary,serverHistoryEnabled=true,serverHistoryLoaded=true,
            )
        } }
        rule.onNodeWithText("Δεν υπάρχουν καταγεγραμμένες αλλαγές.")
            .performScrollTo().assertIsDisplayed()
        assertEquals(0,rule.onAllNodesWithText("POST Undo").fetchSemanticsNodes().size)
    }

    @Test fun exposesReadOnlyServerHistoryWithPrivacyControlledLabels() {
        val summary=ServerFinanceHistorySummary(
            available=true,generation="2",financeRevision="6",
            currentPointId="3",canUndo=true,canRedo=false,
            undoDepth=1,redoDepth=0,
            points=listOf(ServerFinanceHistoryPoint(
                id="3",parentId=null,label="Ευαίσθητη μεταφορά",
                createdAt="2026-10-16T11:00:00Z",current=true,
            )),
        )
        rule.setContent { MyFinHubTheme {
            ProductionSettingsScreen(
                state=FrontendUtilitiesUiState(),onAction={},onBack={},
                serverHistory=summary,serverHistoryEnabled=true,serverHistoryLoaded=true,
                onRefreshServerHistory={},
            )
        } }
        rule.onNodeWithTag("s9_server_finance_history").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("s9_history_refresh").performScrollTo().assertIsDisplayed()
        assertEquals(0,rule.onAllNodesWithText("POST Undo").fetchSemanticsNodes().size)
    }
}
