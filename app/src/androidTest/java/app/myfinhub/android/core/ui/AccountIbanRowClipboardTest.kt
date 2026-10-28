package app.myfinhub.android.core.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myfinhub.android.core.network.AccountMetadataRecord
import app.myfinhub.android.core.security.SensitiveCardClipboard
import app.myfinhub.android.core.security.SensitiveClipboardPort
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountIbanRowClipboardTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private class MemoryPort : SensitiveClipboardPort {
        @Volatile var clip: Pair<String, String>? = null
        @Volatile var clearAttempts = 0
        override fun copy(marker: String, value: String): Boolean {
            clip = marker to value
            return true
        }
        override fun clearIfUnchanged(marker: String, value: String) {
            clearAttempts += 1
            if (clip == (marker to value)) clip = null
        }
        fun foreignCopy(value: String) { clip = "external" to value }
    }

    private val valid = AccountMetadataRecord(
        accountId = "payroll", iban = "gr16 0110 1250 0000 0001 2300 695",
        revision = 1, updatedAt = "2026-10-28",
    )

    @Test fun copyNormalizesValidIbanAndRevokeOnHideAmounts() {
        val store = MemoryPort()
        val controller = SensitiveCardClipboard(context, port = store)
        val visible = mutableStateOf(true)
        rule.setContent {
            MyFinHubTheme {
                CompositionLocalProvider(LocalIbanClipboardController provides controller) {
                    AccountIbanRow(valid, loaded = true, amountsVisible = visible.value)
                }
            }
        }
        rule.onNodeWithTag("account_iban_copy").performClick()
        rule.runOnIdle {
            assertEquals("GR1601101250000000012300695", store.clip?.second)
            assertTrue(store.clip?.first?.startsWith("MyFinHub:sensitive:") == true)
        }
        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("account_iban_copy").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("IBAN ••••").assertExists()
        rule.runOnIdle { assertNull(store.clip) }
    }

    @Test fun disposalClearsOnlyOurCopyAndPreservesNewerForeignText() {
        val store = MemoryPort()
        val controller = SensitiveCardClipboard(context, port = store)
        val visible = mutableStateOf(true)
        rule.setContent {
            MyFinHubTheme {
                CompositionLocalProvider(LocalIbanClipboardController provides controller) {
                    if (visible.value) AccountIbanRow(valid, loaded = true, amountsVisible = true)
                    else androidx.compose.material3.Text("Closed")
                }
            }
        }
        rule.onNodeWithTag("account_iban_copy").performClick()
        rule.runOnIdle {
            assertNotNull(store.clip)
            store.foreignCopy("external IBAN recipient")
            visible.value = false
        }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals("external" to "external IBAN recipient", store.clip) }
    }

    @Test fun switchingAccountsClearsOwnedCopyEvenIfIbanIsIdentical() {
        val store = MemoryPort()
        val controller = SensitiveCardClipboard(context, port = store)
        val record = mutableStateOf(valid)
        rule.setContent {
            MyFinHubTheme {
                CompositionLocalProvider(LocalIbanClipboardController provides controller) {
                    AccountIbanRow(record.value, loaded = true, amountsVisible = true)
                }
            }
        }
        rule.onNodeWithTag("account_iban_copy").performClick()
        rule.runOnIdle { assertEquals("GR1601101250000000012300695", store.clip?.second) }

        // Two owner accounts can share a canonical IBAN (e.g. imported metadata).
        // Moving to the new account must revoke the previous row's copy entitlement.
        rule.runOnIdle { record.value = valid.copy(accountId = "savings", revision = 2) }
        rule.waitForIdle()
        rule.runOnIdle { assertNull(store.clip) }
        rule.onNodeWithText("Αντιγραφή").assertExists()
        rule.onNodeWithTag("account_iban_copy").performClick()
        rule.runOnIdle { assertEquals("GR1601101250000000012300695", store.clip?.second) }
    }

    @Test fun expiryClearsUnchangedCopyEvenIfComposeIsNoLongerActive() {
        val store = MemoryPort()
        val controller = SensitiveCardClipboard(context, expiryMillis = 120L, port = store)
        rule.setContent { MyFinHubTheme { androidx.compose.material3.Text("Waiting") } }
        rule.runOnIdle { assertTrue(controller.copy("GR1601101250000000012300695")) }
        rule.waitUntil(4_000) { store.clip == null }
        rule.runOnIdle { assertTrue(store.clearAttempts > 0) }
    }

    @Test fun invalidIbanDoesNotExposeCopyAction() {
        rule.setContent {
            MyFinHubTheme {
                AccountIbanRow(
                    valid.copy(iban = "GR00 invalid"),
                    loaded = true,
                    amountsVisible = true,
                )
            }
        }
        assertTrue(rule.onAllNodesWithTag("account_iban_copy").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Δεν έχει οριστεί").assertExists()
    }
}
