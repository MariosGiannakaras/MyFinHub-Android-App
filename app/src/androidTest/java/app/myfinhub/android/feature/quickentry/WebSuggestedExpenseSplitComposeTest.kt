package app.myfinhub.android.feature.quickentry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WebSuggestedExpenseSplitComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun explicitSuggestionCopiesTwoExpensePartsOnlyAfterUserPressesButton() {
        var state by mutableStateOf(QuickEntryUiState(
            kind=QuickEntryKind.SPLIT,amountText="27.50",
            category="Τρόφιμα",note="Κρέας 12,50 €\nΛαχανικά 15 €",
        ))
        rule.setContent { MyFinHubTheme {
            ProductionQuickEntryScreen(
                state=state,
                onAction={ action -> state=reduceQuickEntry(state,action) },
                onBack={},
            )
        } }
        rule.onNodeWithTag("quick_entry_split_suggest")
            .performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle {
            assertEquals(listOf("12.5","15"),state.splitParts.map { it.amountText })
            assertEquals(27.5,state.splitTotal,0.001)
        }
        rule.onNodeWithTag("quick_entry_split_suggest").assertDoesNotExist()
    }

    @Test fun semanticRefundPredictionsNeverBecomeUnsafeExpenseParts() {
        val state=QuickEntryUiState(
            kind=QuickEntryKind.SPLIT,amountText="20",
            category="Τρόφιμα",note="Έξοδο 10 €\nΕΠΙΣΤΡΟΦΗ 10 €",
        )
        rule.setContent { MyFinHubTheme {
            ProductionQuickEntryScreen(state,onAction={},onBack={})
        } }
        rule.onNodeWithTag("quick_entry_split_suggest").assertDoesNotExist()
    }
}
