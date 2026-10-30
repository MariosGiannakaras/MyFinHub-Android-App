package app.myfinhub.android.feature.activity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.utilities.AmountVisibilityPreference
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

/** Exercise the actual production detail LazyColumn, not a standalone mock of its parts. */
class ActivitySplitDetailComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun openDeleteDialogIsRevokedIfSourceMutationsBecomeBlocked() {
        val row = ActivityItem(
            "source", "30 Οκτ", ActivityKind.EXPENSE, "Καφές", "",
            -6.0, "Μετρητά", "Τρόφιμα", rawDate = "2026-10-30",
        )
        var blocked by mutableStateOf(false)
        var deleted = 0
        rule.setContent {
            MyFinHubTheme {
                ActivityReadDetailScreen(
                    item = row, accountOptions = emptyList(), mutationBlocked = blocked,
                    onBack = {}, onEdit = {}, onDelete = { deleted++ }, onDeleted = {},
                )
            }
        }
        rule.onNodeWithTag("activity_detail_list")
            .performScrollToNode(hasText("Περισσότερα"))
        rule.onNodeWithText("Περισσότερα").performClick()
        rule.onNodeWithText("Διαγραφή κίνησης").performClick()
        rule.onNodeWithText("Διαγραφή κίνησης;").assertExists()
        rule.runOnIdle { blocked = true }
        rule.onNodeWithText("Διαγραφή κίνησης;").assertDoesNotExist()
        rule.runOnIdle { blocked = false }
        rule.onNodeWithText("Διαγραφή κίνησης;").assertDoesNotExist()
        rule.runOnIdle { org.junit.Assert.assertEquals(0, deleted) }
    }

    @Test fun invalidSplitConstituentNeverDisplaysFakeEurosAndMasksUnderPrivacy() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val original = AmountVisibilityPreference.read(ctx)
        try {
            AmountVisibilityPreference.write(ctx, true)
            val row = ActivityItem(
                "split-invalid", "30 Οκτ", ActivityKind.EXPENSE, "Σύνθετη κίνηση",
                "", -10.0, "Μετρητά", "Άλλο", rawDate = "2026-10-30",
                canonicalKind = "split",
                splitParts = listOf(ActivitySplitPart(
                    "Προβληματικό μέρος", "Άλλο", null, Double.POSITIVE_INFINITY)),
            )
            rule.setContent {
                MyFinHubTheme {
                    ActivityReadDetailScreen(item = row, accountOptions = emptyList(),
                        mutationBlocked = false, onBack = {}, onEdit = {},
                        onDelete = {}, onDeleted = {})
                }
            }
            rule.onNodeWithTag("activity_detail_list")
                .performScrollToNode(hasText("Προβληματικό μέρος"))
            rule.onNodeWithText("Μη έγκυρο ποσό").assertExists()
            rule.onNodeWithText("Infinity", substring = true).assertDoesNotExist()
        } finally {
            AmountVisibilityPreference.write(ctx, original)
        }
    }


    @Test fun activityEditReadOnlyAmountNeverLeaksWhenPrivacyIsHidden() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = AmountVisibilityPreference.read(context)
        try {
            AmountVisibilityPreference.write(context, false)
            val item = ActivityItem(
                "hidden-edit", "21 Οκτ", ActivityKind.EXPENSE, "Ιστορική δαπάνη", "",
                -123.0, "Μετρητά", "Τρόφιμα", rawDate = "2026-10-21",
            )
            rule.setContent {
                MyFinHubTheme {
                    ActivityEditScreen(
                        item = item, categoryOptions = emptyList(),
                        mutationInFlight = false, mutationBlocked = false,
                        onBack = {}, onSave = { _, _, _, _, _ -> }, onSaved = {},
                    )
                }
            }
            rule.onNodeWithText("Ποσό").assertExists()
            rule.onNodeWithText("•••• €").assertExists()
            rule.onNodeWithText(formatActivityFlowEuro(item)).assertDoesNotExist()
        } finally {
            AmountVisibilityPreference.write(context, original)
        }
    }


    @Test fun historicalOverrideEditorExposesRealTypeAmountAndAccountControls() {
        val original = AmountVisibilityPreference.read(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        try {
            AmountVisibilityPreference.write(
                InstrumentationRegistry.getInstrumentation().targetContext, false,
            )
            val legacy = ActivityItem(
                "old-imported", "18 Οκτ", ActivityKind.EXPENSE,
                "Ιστορικό", "", -23.50, "Μετρητά", "Τρόφιμα",
                rawDate = "2026-10-18", accountId = "cash",
                canonicalKind = "expense", isLegacyTransaction = true,
            )
            rule.setContent {
                MyFinHubTheme {
                    ActivityEditScreen(
                        item = legacy,
                        accountOptions = listOf(
                            ActivityAccountOption("cash", "Μετρητά"),
                            ActivityAccountOption("payroll", "Μισθοδοσία"),
                        ),
                        categoryOptions = listOf(ActivityCategoryOption("Τρόφιμα")),
                        expenseCategoryOptions = listOf(ActivityCategoryOption("Τρόφιμα")),
                        incomeCategoryOptions = listOf(ActivityCategoryOption("Μισθός")),
                        mutationInFlight = false,
                        mutationBlocked = false,
                        onBack = {}, onSave = { _, _, _, _, _ -> }, onSaved = {},
                    )
                }
            }
            rule.onNodeWithText(
                "Ιστορική κίνηση", substring = true,
            ).assertExists()
            rule.onNodeWithText("Τύπος ιστορικής κίνησης").assertExists()
            rule.onNodeWithText("Ποσό ιστορικής κίνησης").assertExists()
            rule.onNodeWithText("Λογαριασμός ιστορικής κίνησης").assertExists()
            rule.onNodeWithText("Από λογαριασμό").assertDoesNotExist()
            rule.onNodeWithText("Το ποσό, ο τύπος και ο λογαριασμός δεν αλλάζουν", substring = true)
                .assertDoesNotExist()
        } finally {
            AmountVisibilityPreference.write(
                InstrumentationRegistry.getInstrumentation().targetContext, original,
            )
        }
    }

    @Test fun historicalBalanceRendersInRealDetailOnlyWithAmountVisibility() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = AmountVisibilityPreference.read(context)
        try {
            AmountVisibilityPreference.write(context, true)
            val row = ActivityItem(
                "payroll-historic", "3 Οκτ", ActivityKind.EXPENSE, "Παλιά πληρωμή", "",
                -4.0, "Μισθοδοσία", "Τρόφιμα", rawDate = "2026-10-03",
                accountId = "payroll",
            )
            rule.setContent {
                MyFinHubTheme {
                    ActivityReadDetailScreen(
                        item = row, accountOptions = emptyList(), mutationBlocked = false,
                        onBack = {}, onEdit = {}, onDelete = {}, onDeleted = {},
                        historicalBalance = 856.0,
                    )
                }
            }
            rule.onNodeWithTag("activity_detail_list")
                .performScrollToNode(hasText("Υπόλοιπο λογαριασμού την ημέρα"))
            rule.onNodeWithText("Υπόλοιπο λογαριασμού την ημέρα").assertIsDisplayed()
            val amount = java.text.NumberFormat.getCurrencyInstance(
                java.util.Locale.forLanguageTag("el-GR")).format(856.0)
            rule.onNodeWithText(amount).assertExists()
        } finally {
            AmountVisibilityPreference.write(context, original)
        }
    }

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
        // Amount visibility defaults to hidden for user privacy. Explicitly
        // enable it for this display-sign assertion and restore the original
        // device-local test preference even when an assertion fails.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wasVisible = AmountVisibilityPreference.read(context)
        try {
            AmountVisibilityPreference.write(context, true)
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
        } finally {
            AmountVisibilityPreference.write(context, wasVisible)
        }
    }

    @Test fun multilineImportedNoteAndOverrideAreSeparateInRealReadDetail() {
        val item = ActivityItem(
            "imported", "21 Οκτ", ActivityKind.EXPENSE, "Τρόφιμα", "",
            -12.0, "Μετρητά", "Τρόφιμα", rawDate = "2026-10-21",
            note = "Πρωινός καφές\\nΣυνάντηση εργασίας".replace("\\n", "\n"),
            isLegacyTransaction = true, isLegacyOverridden = true,
        )
        rule.setContent {
            MyFinHubTheme {
                ActivityReadDetailScreen(
                    item = item, accountOptions = emptyList(), mutationBlocked = false,
                    onBack = {}, onEdit = {}, onDelete = {}, onDeleted = {},
                )
            }
        }
        rule.onNodeWithTag("activity_detail_list")
            .performScrollToNode(hasText("Πρωινός καφές"))
        rule.onNodeWithText("Πρωινός καφές").assertIsDisplayed()
        rule.onNodeWithTag("activity_detail_list")
            .performScrollToNode(hasText("Συνάντηση εργασίας"))
        rule.onNodeWithText("Συνάντηση εργασίας").assertIsDisplayed()
        rule.onNodeWithTag("activity_detail_list")
            .performScrollToNode(hasText("Ιστορικό · override"))
        rule.onNodeWithText("Ιστορικό · override").assertIsDisplayed()
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
