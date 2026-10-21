package app.myfinhub.android.feature.activity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** Exercise the actual production detail LazyColumn, not a standalone mock of its parts. */
class ActivitySplitDetailComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun splitDetailDisplaysCanonicalPartLabelsCategoriesAndAmountsOnScroll() {
        val activity = ActivityItem(
            "split", "21 Οκτ", ActivityKind.EXPENSE, "Σύνθετη αγορά", "",
            -30.0, "Μετρητά", "Διαχωρισμός", rawDate = "2026-10-21",
            canonicalKind = "split",
            splitParts = listOf(
                ActivitySplitPart("Σνακ", "Τρόφιμα", "Καφές", 8.0),
                ActivitySplitPart("", "Μεταφορές", "Εισιτήριο", 22.0),
            ),
        )
        rule.setContent {
            MyFinHubTheme {
                ActivityReadDetailScreen(
                    item = activity,
                    accountOptions = emptyList(),
                    mutationBlocked = false,
                    onBack = {}, onEdit = {}, onDelete = {}, onDeleted = {},
                )
            }
        }
        rule.onNodeWithTag("activity_detail_list")
            .performScrollToNode(hasText("Ανάλυση σύνθετης κίνησης · 2 μέρη"))
        rule.onNodeWithText("Ανάλυση σύνθετης κίνησης · 2 μέρη").assertIsDisplayed()
        rule.onNodeWithTag("activity_detail_list")
            .performScrollToNode(hasText("Μεταφορές › Εισιτήριο"))
        rule.onNodeWithText("Μεταφορές › Εισιτήριο").assertIsDisplayed()
        rule.onNodeWithText("Σνακ").assertExists()
    }

    @Test fun reviewedRefundShowsPositiveAmountOnRealDetailEvenWhenOriginalBankExpenseIsNegative() {
        val refund = ActivityItem(
            "reviewed", "21 Οκτ", ActivityKind.EXPENSE, "Επιστροφή αγοράς",
            "", -35.0, "Μετρητά", "Τρόφιμα", rawDate = "2026-10-21",
            financeIncomeContribution = 0.0, financeExpenseContribution = -35.0,
        )
        rule.setContent {
            MyFinHubTheme {
                ActivityReadDetailScreen(
                    item = refund, accountOptions = emptyList(), mutationBlocked = false,
                    onBack = {}, onEdit = {}, onDelete = {}, onDeleted = {},
                )
            }
        }
        rule.onNodeWithText(formatSignedEuro(35.0)).assertIsDisplayed()
        rule.onNodeWithText(formatSignedEuro(-35.0)).assertDoesNotExist()
    }

    @Test fun withdrawalDetailIsNeverMislabelledAsInternalTransfer() {
        val activity = ActivityItem(
            "withdrawal", "21 Οκτ", ActivityKind.TRANSFER, "Ανάληψη", "",
            25.0, "Μετρητά", null, rawDate = "2026-10-21",
            canonicalKind = "withdrawal",
        )
        rule.setContent {
            MyFinHubTheme {
                ActivityReadDetailScreen(
                    item = activity,
                    accountOptions = emptyList(),
                    mutationBlocked = false,
                    onBack = {}, onEdit = {}, onDelete = {}, onDeleted = {},
                )
            }
        }
        rule.onNodeWithText("Λογαριασμός").assertExists()
        rule.onNodeWithText("Προέλευση / προορισμός").assertDoesNotExist()
        rule.onNodeWithText("Η εσωτερική μεταφορά δεν μετρά ως έσοδο ή έξοδο.")
            .assertDoesNotExist()
    }
}
