package app.myfinhub.android.feature.activity

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

class ActivityFinanceCacheProvenanceComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun transactionListDisclosesUnverifiedCacheAndPendingWritesUntilServerReadIsAccepted() {
        val cached = mutableStateOf(true)
        val pending = mutableIntStateOf(3)
        rule.setContent {
            MyFinHubTheme {
                ActivityLedgerScreen(
                    state = ActivityUiState(items = emptyList()),
                    onAction = {}, onOpenDetail = {}, onOpenQuickEntry = {},
                    financeCacheMode = cached.value,
                    pendingFinanceChanges = pending.intValue,
                )
            }
        }
        rule.onNodeWithTag("activity_finance_cache_notice").assertExists()
        rule.onNodeWithText("Οι κινήσεις μπορεί να μην είναι ενημερωμένες.", substring = true).assertExists()
        rule.onNodeWithText("Εκκρεμούν 3 τοπικές αλλαγές για συγχρονισμό.").assertExists()

        rule.runOnIdle { pending.intValue = 0 }
        rule.onNodeWithText("Εκκρεμούν 3 τοπικές αλλαγές για συγχρονισμό.").assertDoesNotExist()
        rule.onNodeWithTag("activity_finance_cache_notice").assertExists()

        rule.runOnIdle { cached.value = false }
        rule.onNodeWithTag("activity_finance_cache_notice").assertDoesNotExist()
    }
}
