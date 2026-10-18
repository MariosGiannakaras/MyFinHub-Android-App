package app.myfinhub.android.feature.utilities

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.plan.PlanningRule
import org.junit.Rule
import org.junit.Test

class SettingsTransactionRulesOverviewComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun existingRulesAreReadOnlyAndWarningsVisible() {
        val entries=listOf(
            PlanningRule("active","Μάρκετ",
                "Όταν market · Τότε Τρόφιμα",true),
            PlanningRule("paused","Παλιός",
                "Όταν petrol · Τότε Άλλο",false,
                "Ο λογαριασμός της συνθήκης δεν είναι πλέον διαθέσιμος."),
        )
        rule.setContent {
            MyFinHubTheme {
                ProductionSettingsScreen(
                    state=FrontendUtilitiesUiState(),onAction={},onBack={},
                    transactionRules=entries,
                )
            }
        }
        rule.onNodeWithText("Αυτόματη ταξινόμηση").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("s9_transaction_rule_1").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Ο λογαριασμός της συνθήκης δεν είναι πλέον διαθέσιμος.")
            .assertIsDisplayed()
        rule.onNodeWithText("Διαγραφή κανόνα").assertDoesNotExist()
    }

    @Test fun pageSizeMatchesWebAndAdditionalRulesRequireExplicitUserAction() {
        val entries=(0 until 25).map { i ->
            PlanningRule("id-$i","Κανόνας $i",
                "Όταν shop · Τότε Τρόφιμα",true)
        }
        rule.setContent {
            MyFinHubTheme {
                ProductionSettingsScreen(
                    state=FrontendUtilitiesUiState(),onAction={},onBack={},
                    transactionRules=entries,
                )
            }
        }
        rule.onNodeWithTag("s9_transaction_rule_24").assertDoesNotExist()
        rule.onNodeWithText("Εμφάνιση περισσότερων κανόνων (1)")
            .performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag("s9_transaction_rule_24")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyRuleListKeepsExistingSettingsLayoutUnchanged() {
        rule.setContent {
            MyFinHubTheme {
                ProductionSettingsScreen(state=FrontendUtilitiesUiState(),onAction={},onBack={})
            }
        }
        rule.onNodeWithTag("s9_transaction_rules").assertDoesNotExist()
    }
}
