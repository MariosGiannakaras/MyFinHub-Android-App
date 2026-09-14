package app.myfinhub.android.app

import app.myfinhub.android.feature.plan.PlannedFlow
import app.myfinhub.android.feature.plan.PlannedItem
import app.myfinhub.android.feature.plan.PlannedKind
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S7PlanQuickEntryPrefillTest {
    @Test
    fun overdueObligation_prefillsContextButKeepsActualTransactionDateAtToday() {
        val today = LocalDate.of(2026, 9, 14).toString()
        val actions = plannedItemQuickEntryPrefillActions(
            item = PlannedItem(
                id = "rent",
                title = "Ενοίκιο",
                dueLabel = "8 Σεπ",
                amount = 680.0,
                kind = PlannedKind.SCHEDULED,
                flow = PlannedFlow.OBLIGATION,
                category = "Στέγαση",
                dueDateIso = "2026-09-08",
            ),
            quickEntryState = QuickEntryUiState(dateText = today),
        )

        assertTrue(actions.contains(QuickEntryAction.SelectKind(QuickEntryKind.EXPENSE)))
        assertTrue(actions.contains(QuickEntryAction.AmountChanged("680.0")))
        assertFalse(actions.any { it is QuickEntryAction.DateChanged })
    }

    @Test
    fun transfer_hasNoFakeCompletionRecordingAction() {
        val actions = plannedItemQuickEntryPrefillActions(
            item = PlannedItem(
                id = "move",
                title = "Αποταμίευση",
                dueLabel = "18 Σεπ",
                amount = 200.0,
                kind = PlannedKind.SCHEDULED,
                flow = PlannedFlow.TRANSFER,
            ),
            quickEntryState = QuickEntryUiState(),
        )
        assertEquals(emptyList<QuickEntryAction>(), actions)
    }
}
