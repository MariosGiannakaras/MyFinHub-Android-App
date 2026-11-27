package app.myfinhub.android.feature.receipt

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityCategoryOption
import app.myfinhub.android.feature.activity.ActivityKind
import app.myfinhub.android.feature.bankalert.BankAlertParser
import app.myfinhub.android.feature.quickentry.ProductionQuickEntryScreen
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ReceiptReviewComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun suspectedWithdrawalDuplicateRequiresBothBankAndDuplicateConfirmations() {
        val parsed = requireNotNull(BankAlertParser.parse(
            "PIRAEUS; WITHDRAWAL; card XX5678; 2026-11-26 09:15; EUR 20.00; ATM: TEST ATM",
        ))
        var result: ReceiptPrefill? = null
        val existing = ActivityItem(
            id = "existing-withdrawal", dateLabel = "26/11/2026", kind = ActivityKind.TRANSFER,
            title = "ATM", subtitle = "", amount = 20.0, accountLabel = "Bank",
            category = null, rawDate = "2026-11-26", note = "ATM", canonicalKind = "withdrawal",
        )
        composeRule.setContent {
            MyFinHubTheme {
                ReceiptReviewDialog(
                    proposal = ReceiptProposal(parsed.merchant, parsed.date, parsed.cents,
                        ReceiptCurrency.EUR, bankAlert = parsed),
                    activityItems = listOf(existing),
                    onDismiss = {},
                    onApply = { result = it },
                )
            }
        }
        composeRule.onNodeWithTag("receipt_review_apply").assertIsNotEnabled()
        composeRule.onNodeWithTag("bank_alert_confirmation").performScrollTo().performClick()
        composeRule.onNodeWithTag("receipt_review_apply").assertIsNotEnabled()
        composeRule.onNodeWithTag("receipt_duplicate_confirmation").performScrollTo().performClick()
        composeRule.onNodeWithTag("receipt_review_apply").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(ReceiptPrefill("20.00", "2026-11-26", "TEST ATM",
                bankAlertKind = BankAlertParser.Kind.WITHDRAWAL), result)
        }
    }

    @Test
    fun productionExpenseExposesReceiptEntryOnlyThroughAuthorizedHost() {
        var opened = false
        composeRule.setContent {
            MyFinHubTheme {
                CompositionLocalProvider(LocalReceiptImportController provides ReceiptImportController(
                    open = { opened = true }, prefill = null, consume = { false },
                )) {
                    ProductionQuickEntryScreen(QuickEntryUiState(), {}, {})
                }
            }
        }
        composeRule.onNodeWithTag("quick_entry_receipt").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun duplicateRequiresExplicitConfirmationBeforePrefillAndDoesNotSave() {
        var result: ReceiptPrefill? = null
        val existing = ActivityItem(
            id = "existing", dateLabel = "26/11/2026", kind = ActivityKind.EXPENSE,
            title = "Store", subtitle = "", amount = -12.34, accountLabel = "Cash",
            category = "Other", rawDate = "2026-11-26", note = "Store",
            canonicalKind = "expense", financeExpenseContribution = 12.34,
        )
        composeRule.setContent {
            MyFinHubTheme {
                ReceiptReviewDialog(
                    proposal = ReceiptProposal("Store", LocalDate.of(2026, 11, 26), 1234, ReceiptCurrency.EUR),
                    activityItems = listOf(existing),
                    onDismiss = {},
                    onApply = { result = it },
                )
            }
        }
        composeRule.onNodeWithTag("receipt_review_apply").assertIsNotEnabled()
        composeRule.onNodeWithTag("receipt_duplicate_confirmation").performScrollTo().performClick()
        composeRule.onNodeWithTag("receipt_review_apply").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(ReceiptPrefill("12.34", "2026-11-26", "Store"), result) }
    }

    @Test
    fun foreignCurrencyCannotPrefillAnUnreviewedAmount() {
        composeRule.setContent {
            MyFinHubTheme {
                ReceiptReviewDialog(
                    proposal = ReceiptProposal("Store", LocalDate.of(2026, 11, 26), 1234, ReceiptCurrency.FOREIGN),
                    activityItems = emptyList(),
                    onDismiss = {},
                    onApply = { error("Foreign currency was not reviewed") },
                )
            }
        }
        composeRule.onNodeWithTag("receipt_review_apply").assertIsNotEnabled()
    }

    @Test
    fun qrDisagreementAndCategorySuggestionRequireDeliberateReview() {
        var result: ReceiptPrefill? = null
        val existing = ActivityItem(
            id = "prior", dateLabel = "01/11/2026", kind = ActivityKind.EXPENSE,
            title = "TEST STORE", subtitle = "", amount = -5.0, accountLabel = "Cash",
            category = "Food", subcategory = "Groceries", rawDate = "2026-11-01", note = "TEST STORE",
            canonicalKind = "expense", financeExpenseContribution = 5.0,
        )
        val fiscal = FiscalReceiptQr(1835, java.time.LocalDateTime.of(2026, 11, 26, 10, 37), "synthetic")
        val proposal = mergeReceiptQr(
            ReceiptProposal("TEST STORE", LocalDate.of(2026, 11, 25), 1935, ReceiptCurrency.EUR),
            ReceiptQrRead(ReceiptQrStatus.AADE, fiscal),
        )
        composeRule.setContent {
            MyFinHubTheme {
                ReceiptReviewDialog(
                    proposal = proposal,
                    activityItems = listOf(existing),
                    categoryOptions = listOf(ActivityCategoryOption("Food", listOf("Groceries"))),
                    onDismiss = {},
                    onApply = { result = it },
                )
            }
        }
        composeRule.onNodeWithTag("receipt_review_apply").assertIsNotEnabled()
        composeRule.onNodeWithTag("receipt_qr_confirmation").performScrollTo().performClick()
        composeRule.onNodeWithTag("receipt_category_confirmation").performScrollTo().performClick()
        composeRule.onNodeWithTag("receipt_review_apply").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(ReceiptPrefill(
                "18.35", "2026-11-26", "TEST STORE",
                categorySuggestion = ReceiptCategorySuggestion("Food", "Groceries"),
            ), result)
        }
    }
}
