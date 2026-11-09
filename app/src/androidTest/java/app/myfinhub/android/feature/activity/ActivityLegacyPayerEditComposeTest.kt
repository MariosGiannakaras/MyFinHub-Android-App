package app.myfinhub.android.feature.activity

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** A reviewed legacy transaction may only be reassigned by an explicit current payer choice. */
class ActivityLegacyPayerEditComposeTest {
    @get:Rule val rule = createComposeRule()

    private val accounts = listOf(
        ActivityAccountOption("cash", "Μετρητά"),
        ActivityAccountOption("savings", "Αποταμίευση"),
    )

    private fun historical(
        type: String = "expense",
        accountId: String? = null,
        fromId: String? = null,
        toId: String? = null,
    ) = ActivityItem(
        id = "legacy-1", dateLabel = "20 Οκτ", rawDate = "2026-10-20",
        kind = if (type == "transfer") ActivityKind.TRANSFER else ActivityKind.EXPENSE,
        canonicalKind = type, title = "Ιστορική κίνηση",
        subtitle = "Ιστορική κίνηση", note = "Ιστορική κίνηση",
        amount = if (type == "expense") -25.0 else 25.0,
        accountLabel = "Ιστορική πηγή", category = "Άλλο",
        accountId = accountId, fromAccountId = fromId, toAccountId = toId,
        isLegacyTransaction = true,
    )

    @Test fun missingHistoricalExpensePayerShowsNoFalseDefaultAndBlocksSave() {
        val writes = mutableListOf<ActivityLegacyEdit?>()
        rule.setContent { MyFinHubTheme {
            ActivityEditScreen(
                item = historical(), categoryOptions = emptyList(),
                accountOptions = accounts, mutationInFlight = false, mutationBlocked = false,
                onBack = {}, onSave = { _, _, _, _, legacy -> writes += legacy }, onSaved = {},
            )
        } }
        rule.onNodeWithText("Επίλεξε λογαριασμό").assertIsDisplayed()
        rule.onNodeWithText("Επίλεξε ρητά διαθέσιμο λογαριασμό.").assertIsDisplayed()
        rule.onNodeWithText("Αποθήκευση αλλαγών").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0, writes.size) }

        rule.onNodeWithText("Επίλεξε λογαριασμό").performClick()
        rule.onNodeWithText("Μετρητά").performClick()
        rule.onNodeWithText("Αποθήκευση αλλαγών").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(1, writes.size)
            assertEquals("cash", writes.single()?.accountId)
            assertEquals(null, writes.single()?.fromAccountId)
            assertEquals(null, writes.single()?.toAccountId)
        }
    }

    @Test fun historicalAccountRemovedDuringEditRevokesPendingAuthorization() {
        val available = mutableStateOf(accounts)
        val writes = mutableListOf<ActivityLegacyEdit?>()
        rule.setContent { MyFinHubTheme {
            ActivityEditScreen(
                item = historical(), categoryOptions = emptyList(),
                accountOptions = available.value,
                mutationInFlight = false, mutationBlocked = false,
                onBack = {}, onSave = { _, _, _, _, legacy -> writes += legacy },
                onSaved = {},
            )
        } }
        rule.onNodeWithText("Επίλεξε λογαριασμό").performClick()
        rule.onNodeWithText("Μετρητά").performClick()
        rule.onNodeWithText("Αποθήκευση αλλαγών").assertIsEnabled()
        rule.runOnIdle {
            available.value = listOf(ActivityAccountOption("savings", "Αποταμίευση"))
        }
        rule.onNodeWithText("Επίλεξε λογαριασμό").assertIsDisplayed()
        rule.onNodeWithText("Αποθήκευση αλλαγών").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0, writes.size) }
    }

    @Test fun historicalTransferSwitchingSourceOntoDestinationClearsConflict() {
        val writes = mutableListOf<ActivityLegacyEdit?>()
        rule.setContent { MyFinHubTheme {
            ActivityEditScreen(
                item = historical(type = "transfer", fromId = "cash", toId = "savings"),
                categoryOptions = emptyList(), accountOptions = accounts,
                mutationInFlight = false, mutationBlocked = false,
                onBack = {}, onSave = { _, _, _, _, legacy -> writes += legacy },
                onSaved = {},
            )
        } }
        rule.onNodeWithText("Μετρητά").performClick()
        // The currently selected destination and the open dropdown item both
        // contain this label; target the menu choice, not the underlying field.
        rule.onAllNodesWithText("Αποταμίευση").onLast().performClick()
        rule.onNodeWithText("Επίλεξε προορισμό").assertIsDisplayed()
        rule.onNodeWithText("Αποθήκευση αλλαγών").assertIsNotEnabled()
        rule.onNodeWithText("Επίλεξε προορισμό").performClick()
        rule.onNodeWithText("Μετρητά").performClick()
        rule.onNodeWithText("Αποθήκευση αλλαγών").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(1, writes.size)
            assertEquals("savings", writes.single()?.fromAccountId)
            assertEquals("cash", writes.single()?.toAccountId)
            assertEquals(null, writes.single()?.accountId)
        }
    }
}
