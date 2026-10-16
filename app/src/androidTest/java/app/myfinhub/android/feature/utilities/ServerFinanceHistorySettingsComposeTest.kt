package app.myfinhub.android.feature.utilities

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.core.network.ServerFinanceHistoryPoint
import app.myfinhub.android.core.network.ServerFinanceHistorySummary
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real Settings server history is read-only and masks user labels in privacy mode. */
class ServerFinanceHistorySettingsComposeTest {
    @get:Rule val rule=createComposeRule()

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
