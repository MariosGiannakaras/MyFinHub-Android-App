package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** Variable amount display never claims a real payment or reveals hidden range values. */
class RecurringEstimateComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun actualLinkedExpensesRemainPrivateAndSeparateFromVariableEstimates() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val before = app.myfinhub.android.feature.utilities.AmountVisibilityPreference.read(context)
        try {
            app.myfinhub.android.feature.utilities.AmountVisibilityPreference.write(context, false)
            rule.setContent { MyFinHubTheme {
                CanonicalRecurringManagementScreen(
                    PlanUiState(recurringManageItems = listOf(
                        RecurringManageItem("electric", "Ρεύμα", 80.0, "active",
                            amountMode = "variable", estimateMin = 60.0, estimateMax = 100.0,
                            lastActualPaymentDateIso = "2026-10-08",
                            lastActualPaymentAmount = 95.03,
                            actualPaidThisMonth = 105.05, actualPaymentsThisMonth = 2),
                    )),
                    onBack = {}, onStatusChanged = { _, _ -> }, mutationBlocked = true,
                )
            } }
            rule.onNodeWithTag("recurring_last_actual_electric").performScrollTo()
                .assertTextContains("•••• €", substring = true)
            rule.onNodeWithTag("recurring_actual_paid_month_electric").performScrollTo()
                .assertTextContains("•••• €", substring = true)
            // This Compose test dependency lacks assertDoesNotExist; inspect
            // the semantics tree directly without weakening the privacy assertion.
            rule.onAllNodesWithText("95,03", substring = true)
                .fetchSemanticsNodes().let { org.junit.Assert.assertTrue(it.isEmpty()) }
            rule.onAllNodesWithText("105,05", substring = true)
                .fetchSemanticsNodes().let { org.junit.Assert.assertTrue(it.isEmpty()) }
        } finally {
            app.myfinhub.android.feature.utilities.AmountVisibilityPreference.write(context, before)
        }
    }

    @Test fun fixedAndVariableModesRemainDistinguishableWhenPrivacyIsEnabled() {
        val state = PlanUiState(recurringManageItems = listOf(
            RecurringManageItem("electric", "Ρεύμα", 65.0, "active",
                amountMode = "variable", estimateMin = 40.0, estimateMax = 90.0),
            RecurringManageItem("rent", "Ενοίκιο", 400.0, "active",
                amountMode = "fixed"),
        ))
        rule.setContent { MyFinHubTheme {
            CanonicalRecurringManagementScreen(
                state, onBack = {}, onStatusChanged = { _, _ -> },
                mutationBlocked = true, paymentAccountAvailable = false,
            )
        } }
        rule.onNodeWithTag("recurring_manage_item_electric").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Εκτίμηση · όχι πραγματική πληρωμή").assertIsDisplayed()
        rule.onNodeWithTag("recurring_estimate_range_electric").assertIsDisplayed()
        rule.onNodeWithTag("recurring_amount_mode_rent").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Σταθερό ποσό").assertIsDisplayed()
    }
}
