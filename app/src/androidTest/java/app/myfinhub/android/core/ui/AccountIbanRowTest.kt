package app.myfinhub.android.core.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.core.network.AccountMetadataRecord
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountIbanRowTest {
    @get:Rule val composeRule = createComposeRule()

    private val sample = AccountMetadataRecord(
        accountId = "piraeus-payroll",
        iban = "GR1601101250000000012300695",
        revision = 1,
        updatedAt = "2026-10-14T00:00:00Z",
    )

    @Test
    fun visibleAccountShowsOnlyMaskedIbanAndCopyRequiresTap() {
        composeRule.setContent {
            MyFinHubTheme { AccountIbanRow(sample, loaded = true, amountsVisible = true) }
        }
        composeRule.onNodeWithText("GR16 •••• •••• •••• 0695", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Αντιγραφή").assertExists()
        composeRule.onNodeWithText(sample.iban!!, substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun privacyHiddenNeverExposesLastFourOrCopy() {
        composeRule.setContent {
            MyFinHubTheme { AccountIbanRow(sample, loaded = true, amountsVisible = false) }
        }
        composeRule.onNodeWithText("IBAN ••••", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Αντιγραφή").assertDoesNotExist()
        composeRule.onNodeWithText("0695", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun unavailableOfflineMetadataRendersNothingEvenWhenRecordIsPassed() {
        composeRule.setContent {
            MyFinHubTheme { AccountIbanRow(sample, loaded = false, amountsVisible = true) }
        }
        composeRule.onNodeWithText("GR16", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Αντιγραφή").assertDoesNotExist()
    }

    @Test
    fun absentConfiguredIbanDoesNotOfferCopy() {
        composeRule.setContent {
            MyFinHubTheme { AccountIbanRow(null, loaded = true, amountsVisible = true) }
        }
        composeRule.onNodeWithText("Δεν έχει οριστεί").assertExists()
        composeRule.onNodeWithText("Αντιγραφή").assertDoesNotExist()
    }
}
