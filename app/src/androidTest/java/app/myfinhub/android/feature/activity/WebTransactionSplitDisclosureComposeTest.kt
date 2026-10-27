package app.myfinhub.android.feature.activity

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.utilities.AmountVisibilityPreference
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Hosted S24 UI: splits are independently expandable, never new transactions. */
class WebTransactionSplitDisclosureComposeTest {
    @get:Rule val rule = createComposeRule()
    private fun split() = ActivityItem(
        id = "split-1", dateLabel = "25 Οκτ",
        kind = ActivityKind.EXPENSE, title = "Αγορά",
        subtitle = "", amount = -30.0, accountLabel = "Μετρητά",
        category = "Διαχωρισμός", canonicalKind = "split",
        rawDate = "2026-10-25", canonicalSourceOrdinal = 0,
        splitParts = listOf(
            ActivitySplitPart("Σνακ", "Τρόφιμα", "Καφές", 8.0),
            ActivitySplitPart("", "Μεταφορές", "Εισιτήριο", 22.0),
        ),
    )

    @Test fun realLedgerExpandsSplitWithoutNavigatingToDetail() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prior = AmountVisibilityPreference.read(context)
        try {
            AmountVisibilityPreference.write(context, true)
            var navigations = 0
            rule.setContent { MyFinHubTheme {
                ActivityLedgerScreen(
                    state = ActivityUiState(items = listOf(split())),
                    onAction = {}, onOpenDetail = { navigations++ },
                    onOpenQuickEntry = {},
                )
            } }
            rule.onNodeWithTag("activity_split_details_split-1_0").assertDoesNotExist()
            rule.onNodeWithTag("activity_split_toggle_split-1_0")
                .performScrollTo().performClick()
            rule.onNodeWithTag("activity_split_details_split-1_0").assertExists()
            rule.onNodeWithText("Σνακ").assertExists()
            rule.onNodeWithText("Μεταφορές › Εισιτήριο").assertExists()
            rule.runOnIdle { assertEquals(0, navigations) }
            rule.onNodeWithTag("activity_split_toggle_split-1_0")
                .performScrollTo().performClick()
            rule.onNodeWithTag("activity_split_details_split-1_0").assertDoesNotExist()
        } finally {
            AmountVisibilityPreference.write(context, prior)
        }
    }

    @Test fun hiddenAmountsMaskEveryPartWhileCategoryLabelsRemainAvailable() {
        rule.setContent { MyFinHubTheme {
            ActivityLedgerSplitDisclosure(split(), amountsVisible = false)
        } }
        rule.onNodeWithTag("activity_split_toggle_split-1_0").performClick()
        rule.onNodeWithTag("activity_split_amount_split-1_0_0").assertExists()
        rule.onNodeWithTag("activity_split_amount_split-1_0_1").assertExists()
        rule.onNodeWithText("8,00", substring = true).assertDoesNotExist()
        rule.onNodeWithText("22,00", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Τρόφιμα › Καφές").assertExists()
    }

    @Test fun onePartOrNonsplitNeverIntroducesMisleadingDisclosure() {
        rule.setContent { MyFinHubTheme {
            ActivityLedgerSplitDisclosure(
                split().copy(splitParts = listOf(split().splitParts.first())),
                amountsVisible = true,
            )
            ActivityLedgerSplitDisclosure(
                split().copy(canonicalKind = "expense"),
                amountsVisible = true,
            )
        } }
        rule.onNodeWithTag("activity_split_toggle_split-1_0").assertDoesNotExist()
    }
}
