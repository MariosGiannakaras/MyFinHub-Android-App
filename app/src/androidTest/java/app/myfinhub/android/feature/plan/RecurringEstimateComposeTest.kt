package app.myfinhub.android.feature.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** Variable amount display never claims a real payment or reveals hidden range values. */
class RecurringEstimateComposeTest {
    @get:Rule val rule = createComposeRule()

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
