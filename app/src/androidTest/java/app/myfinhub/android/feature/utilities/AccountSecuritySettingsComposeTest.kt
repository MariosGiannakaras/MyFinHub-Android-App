package app.myfinhub.android.feature.utilities

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.myfinhub.android.core.network.WebAccountSecurityChange
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real account security editor requires validated explicit confirmation. */
class AccountSecuritySettingsComposeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun emailMustBeValidAndConfirmedBeforeDispatch() {
        val intents=mutableListOf<WebAccountSecurityChange>()
        rule.setContent { MyFinHubTheme {
            AccountSecuritySettingsControls(true,false,null) { intents += it }
        } }
        rule.onNodeWithTag("s9_account_email_open").performClick()
        rule.onNodeWithTag("s9_account_security_confirm").assertIsNotEnabled()
        rule.onNodeWithTag("s9_account_email_input").performTextInput(" New@Example.org ")
        rule.onNodeWithTag("s9_account_security_confirm").performClick()
        rule.runOnIdle {
            assertEquals(1,intents.size)
            assertEquals("new@example.org",(intents.first() as WebAccountSecurityChange.Email).value)
        }
    }

    @Test fun passwordNeedsPolicyMatchAndTwoEntriesWithoutPersistingAfterClose() {
        val intents=mutableListOf<WebAccountSecurityChange>()
        rule.setContent { MyFinHubTheme {
            AccountSecuritySettingsControls(true,false,null) { intents += it }
        } }
        rule.onNodeWithTag("s9_account_password_open").performClick()
        rule.onNodeWithTag("s9_account_current_password").performTextInput("Old123!Password")
        rule.onNodeWithTag("s9_account_new_password").performTextInput("New123!Password")
        rule.onNodeWithTag("s9_account_confirm_password").performTextInput("Different123!")
        rule.onNodeWithTag("s9_account_security_confirm").assertIsNotEnabled()
    }

    @Test fun validatedMatchingPasswordExplicitlyDispatchesOnlyAfterConfirmation() {
        val intents = mutableListOf<WebAccountSecurityChange>()
        rule.setContent { MyFinHubTheme {
            AccountSecuritySettingsControls(true,false,null) { intents += it }
        } }
        rule.onNodeWithTag("s9_account_password_open").performClick()
        rule.onNodeWithTag("s9_account_current_password").performTextInput("Old123!Password")
        rule.onNodeWithTag("s9_account_new_password").performTextInput("New123!Password")
        rule.onNodeWithTag("s9_account_confirm_password").performTextInput("New123!Password")
        rule.onNodeWithTag("s9_account_security_confirm").performClick()
        rule.runOnIdle {
            assertEquals(1,intents.size)
            val intent = intents.single() as WebAccountSecurityChange.Password
            assertEquals("Old123!Password",intent.current)
            assertEquals("New123!Password",intent.replacement)
            assertFalse(intent.toString().contains(intent.replacement))
        }
    }

    @Test fun inFlightServerChangeLocksEditors() {
        rule.setContent { MyFinHubTheme {
            AccountSecuritySettingsControls(enabled=true,saving=true,feedback=null) {
                error("No second submission")
            }
        } }
        rule.onNodeWithTag("s9_account_email_open").assertIsNotEnabled()
        rule.onNodeWithTag("s9_account_password_open").assertIsNotEnabled()
    }

    @Test fun offlineOrUnverifiedSessionDisablesAccountChanges() {
        rule.setContent { MyFinHubTheme {
            AccountSecuritySettingsControls(false,false,null) { error("No submit") }
        } }
        rule.onNodeWithTag("s9_account_email_open").assertIsNotEnabled()
        rule.onNodeWithTag("s9_account_password_open").assertIsNotEnabled()
    }
}
