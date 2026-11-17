package app.myfinhub.android.feature.home

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

class HomeFinanceCacheProvenanceComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun cachedFinanceIsMarkedUnverifiedAndClearsOnlyOnAcceptedServerState() {
        val cache = mutableStateOf(true)
        val pending = mutableIntStateOf(2)
        rule.setContent {
            MyFinHubTheme {
                ProductionHomeScreen(
                    state = syntheticHomeUiState(),
                    onAction = {}, onOpenAttention = {}, onOpenQuickEntry = {},
                    financeCacheMode = cache.value,
                    pendingFinanceChanges = pending.intValue,
                    amountsVisibleOverride = true,
                )
            }
        }
        rule.onNodeWithTag("home_finance_cache_notice").assertExists()
        rule.onNodeWithText("Αποθηκευμένα οικονομικά στοιχεία").assertExists()
        rule.onNodeWithText("Εκκρεμούν 2 τοπικές αλλαγές για συγχρονισμό.").assertExists()

        rule.runOnIdle { pending.intValue = 0 }
        rule.onNodeWithText("Εκκρεμούν 2 τοπικές αλλαγές για συγχρονισμό.").assertDoesNotExist()
        rule.onNodeWithTag("home_finance_cache_notice").assertExists()

        rule.runOnIdle { cache.value = false }
        rule.onNodeWithTag("home_finance_cache_notice").assertDoesNotExist()
    }
}
