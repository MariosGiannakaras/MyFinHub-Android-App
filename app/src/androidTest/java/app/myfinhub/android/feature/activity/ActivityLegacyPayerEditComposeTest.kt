package app.myfinhub.android.feature.activity

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
        rule.onAllNodesWithText("Αποταμίευση")[1].performClick()
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
    @Test fun historicalExpenseWithoutNoteCanBeCorrectedWithoutInventingDescription() {
        val writes = mutableListOf<Pair<String, ActivityLegacyEdit?>>()
        rule.setContent { MyFinHubTheme {
            ActivityEditScreen(
                item = historical(accountId = "cash").copy(note = ""),
                categoryOptions = emptyList(), accountOptions = accounts,
                mutationInFlight = false, mutationBlocked = false,
                onBack = {},
                onSave = { _, note, _, _, edit -> writes += note to edit },
                onSaved = {},
            )
        } }
        rule.onNodeWithText("Μετρητά").performClick()
        rule.onNodeWithText("Αποταμίευση").performClick()
        rule.onNodeWithText("Αποθήκευση αλλαγών").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(1, writes.size)
            assertEquals("", writes.single().first)
            assertEquals("savings", writes.single().second?.accountId)
        }
    }

    @Test fun rejectedEditReleasesSubmitLockAndAllowsDiscardInsteadOfTrappingOwner() {
        val blocked = mutableStateOf(false)
        var writes = 0
        var backs = 0
        rule.setContent { MyFinHubTheme {
            ActivityEditScreen(
                item = historical(), categoryOptions = emptyList(),
                accountOptions = accounts, mutationInFlight = false,
                mutationBlocked = blocked.value,
                onBack = { backs++ },
                onSave = { _, _, _, _, _ ->
                    writes++
                    blocked.value = true
                },
                onSaved = {},
            )
        } }
        rule.onNodeWithText("Επίλεξε λογαριασμό").performClick()
        rule.onNodeWithText("Μετρητά").performClick()
        rule.onNodeWithText("Αποθήκευση αλλαγών").assertIsEnabled().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Αποθήκευση αλλαγών").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Πίσω").performClick()
        rule.onNodeWithText("Απόρριψη αλλαγών;").assertIsDisplayed()
        rule.onNodeWithText("Απόρριψη").performClick()
        rule.runOnIdle {
            assertEquals(1, writes)
            assertEquals(1, backs)
        }
    }

    @Test fun externalMutationInFlightDisablesHistoricalSaveEvenBeforeOwnerSubmits() {
        val inFlight = mutableStateOf(false)
        var writes = 0
        rule.setContent { MyFinHubTheme {
            ActivityEditScreen(
                item = historical(), categoryOptions = emptyList(),
                accountOptions = accounts, mutationInFlight = inFlight.value,
                mutationBlocked = false,
                onBack = {}, onSave = { _, _, _, _, _ -> writes++ }, onSaved = {},
            )
        } }
        rule.onNodeWithText("Επίλεξε λογαριασμό").performClick()
        rule.onNodeWithText("Μετρητά").performClick()
        rule.onNodeWithText("Αποθήκευση αλλαγών").assertIsEnabled()
        rule.runOnIdle { inFlight.value = true }
        rule.onNodeWithText("Αποθήκευση…").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0, writes) }
    }

    @Test fun sameNamedHistoricalSourcesAreDistinctAndKeepExactChosenId() {
        val named = listOf(
            ActivityAccountOption("cash-00000001", "Κοινός"),
            ActivityAccountOption("cash-00000002", "Κοινός"),
        )
        val writes = mutableListOf<ActivityLegacyEdit?>()
        rule.setContent { MyFinHubTheme {
            ActivityEditScreen(
                item = historical(), categoryOptions = emptyList(),
                accountOptions = named, mutationInFlight = false, mutationBlocked = false,
                onBack = {}, onSave = { _, _, _, _, edit -> writes += edit }, onSaved = {},
            )
        } }
        rule.onNodeWithText("Επίλεξε λογαριασμό").performClick()
        rule.onNodeWithText("Κοινός · 00000002").performClick()
        rule.onNodeWithText("Αποθήκευση αλλαγών").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals("cash-00000002", writes.single()?.accountId)
        }
    }

    @Test fun staleAccountFilterMustNotPresentAllAccountsAsSelected() {
        val state = ActivityUiState(
            items = emptyList(),
            accountOptions = accounts,
            accountFilterId = "no-longer-present",
        )
        rule.setContent { MyFinHubTheme {
            ActivityFilterSheetContent(
                state = state,
                onApply = { _, _, _, _, _ -> },
                onReset = {},
            )
        } }
        rule.onNodeWithText("Η επιλογή δεν είναι πλέον διαθέσιμη").assertIsDisplayed()
    }

    @Test fun changingHistoricalTypeNeverReintroducesOppositeCategory() {
        rule.setContent { MyFinHubTheme {
            ActivityEditScreen(
                item = historical(accountId = "cash"),
                categoryOptions = listOf(ActivityCategoryOption("Άλλο")),
                expenseCategoryOptions = listOf(ActivityCategoryOption("Άλλο")),
                incomeCategoryOptions = listOf(ActivityCategoryOption("Μισθός")),
                accountOptions = accounts,
                mutationInFlight = false, mutationBlocked = false,
                onBack = {}, onSave = { _, _, _, _, _ -> }, onSaved = {},
            )
        } }
        rule.onNodeWithText("Έξοδο").performClick()
        rule.onNodeWithText("Έσοδο").performClick()
        rule.onNodeWithText("Επίλεξε επιλογή").performClick()
        rule.onNodeWithText("Μισθός").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Άλλο").fetchSemanticsNodes().size)
    }

}
