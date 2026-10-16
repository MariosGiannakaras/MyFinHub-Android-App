package app.myfinhub.android.feature.utilities

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.plan.CanonicalRecurringManagementScreen
import app.myfinhub.android.feature.plan.PlanUiState
import app.myfinhub.android.feature.plan.RecurringManageItem
import org.junit.Rule
import org.junit.Test

/** Real Compose screens at 150% font scale, including vertical recovery and 48dp actions. */
class WebParityLargeTypeComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun securityPasswordFieldsRemainReachableWhenTextSizeIsLarge() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,1.5f)) {
                MyFinHubTheme {
                    AccountSecuritySettingsControls(enabled=true,saving=false,feedback=null) {
                        error("No unconfirmed mutation")
                    }
                }
            }
        }
        rule.onNodeWithTag("s9_account_password_open").performClick()
        rule.onNodeWithTag("s9_account_current_password").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("s9_account_new_password").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("s9_account_confirm_password").performScrollTo().assertIsDisplayed()
    }

    @Test fun recurringManagementActionsRemainAccessibleAtLargeFontScale() {
        val state=PlanUiState(recurringManageItems=listOf(
            RecurringManageItem("active","Πάγιο Internet",35.0,"active","2026-10-31"),
            RecurringManageItem("paused","Ασφάλεια",50.0,"paused"),
        ))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,1.5f)) {
                MyFinHubTheme {
                    CanonicalRecurringManagementScreen(
                        state=state,onBack={},
                        onStatusChanged={_,_->error("No unconfirmed mutation")},
                        mutationBlocked=true,
                    )
                }
            }
        }
        rule.onNodeWithTag("recurring_manage_item_active").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("recurring_manage_item_paused").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("recurring_toggle_paused").performScrollTo().assertIsDisplayed()
    }
}
