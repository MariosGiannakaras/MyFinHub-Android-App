package app.myfinhub.android.feature.money

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.myfinhub.android.core.network.AccountMetadataRecord
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Real account detail: owner metadata IBAN changes require explicit validated input. */
class WebAccountIbanEditorComposeTest {
    @get:Rule val rule = createComposeRule()
    private val account = MoneyAccount(
        id="payroll", name="Μισθοδοσία", balance=230.0,
        kind="Τράπεζα", canonicalKind="bank",
    )

    @Test fun invalidIbanNeverEnablesSaveAndValidIbanEmitsOnlyExplicitAction() {
        var saved: Pair<String,String?>? = null
        rule.setContent { MyFinHubTheme {
            CanonicalAccountDetailScreen(
                account=account, activityItems=emptyList(),
                onBack={}, onOpenActivity={},
                accountMetadataLoaded=true,
                onSaveAccountIban={id,iban -> saved = id to iban},
            )
        } }
        rule.onNodeWithText("Προσθήκη IBAN").performScrollTo().performClick()
        rule.onNodeWithText("Νέο IBAN").performTextInput("GR0000000000000000000000000")
        rule.onNodeWithText("Αποθήκευση").assertIsNotEnabled()
        assertNull(saved)
        rule.onNodeWithText("Ακύρωση").performClick()
        rule.onNodeWithText("Προσθήκη IBAN").performScrollTo().performClick()
        rule.onNodeWithText("Νέο IBAN").performTextInput("GR16 0110 1250 0000 0001 2300 695")
        rule.onNodeWithText("Αποθήκευση").performClick()
        rule.runOnIdle {
            assertEquals("payroll" to "GR1601101250000000012300695",saved)
        }
    }

    @Test fun deletingIbanIsMetadataOnlyAndRequiresExplicitSecondConfirmation() {
        var saved: Pair<String,String?>? = null
        rule.setContent { MyFinHubTheme {
            CanonicalAccountDetailScreen(
                account=account, activityItems=emptyList(),
                onBack={}, onOpenActivity={},
                ibanRecord=AccountMetadataRecord(
                    "payroll", "GR1601101250000000012300695", 2, "2026-10-16T08:00:00Z"),
                accountMetadataLoaded=true,
                onSaveAccountIban={id,iban -> saved = id to iban},
            )
        } }
        rule.onNodeWithText("Αφαίρεση IBAN").performScrollTo().performClick()
        rule.onNodeWithText("Επιβεβαίωση αφαίρεσης IBAN").assertIsDisplayed()
        rule.runOnIdle { assertNull(saved) }
        rule.onNodeWithText("Ακύρωση").performClick()
        rule.runOnIdle { assertNull(saved) }
        rule.onNodeWithText("Αφαίρεση IBAN").performScrollTo().performClick()
        rule.onAllNodesWithText("Αφαίρεση IBAN").onLast().performClick()
        rule.runOnIdle { assertEquals("payroll" to null,saved) }
    }

    @Test fun noOwnerMetadataOrHiddenFinancialPrivacySuppressesEditor() {
        rule.setContent { MyFinHubTheme {
            CanonicalAccountDetailScreen(
                account=account, activityItems=emptyList(),
                onBack={}, onOpenActivity={},
                accountMetadataLoaded=false,
                onSaveAccountIban={_,_ -> error("Unauthorized write") },
            )
        } }
        assertEquals(0,rule.onAllNodesWithText("Προσθήκη IBAN").fetchSemanticsNodes().size)
    }
}
