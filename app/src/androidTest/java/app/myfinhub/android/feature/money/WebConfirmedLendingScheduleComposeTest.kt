package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.core.data.CanonicalConfirmedLendingPlanView
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Rule
import org.junit.Test

/** Hosted S24 UI: neither calendar dates nor visibility fabricate receivables. */
class WebConfirmedLendingScheduleComposeTest {
    @get:Rule val rule = createComposeRule()

    private fun plan(count: Int = 3, amount: Double = 100.0) =
        CanonicalConfirmedLendingPlanView(
            person="Alex",id="confirmed-1",total=amount,
            firstDueDate="2026-10-31",installments=count,everyMonths=1,
            confirmedAt="2026-08-17T10:00:00Z",
        )

    @Test fun elapsedDatesWarnButAreNeverMarkedPaid() {
        rule.setContent { MyFinHubTheme {
            CanonicalConfirmedLendingPlanDetails(
                plan(),currentOutstanding=40.0,amountsVisible=true,
                asOf="2026-12-01",
            )
        } }
        rule.onNodeWithTag("lending_confirmed_schedule_heading").assertExists()
        rule.onNodeWithTag("lending_confirmed_schedule_row_3").assertExists()
        rule.onNodeWithTag("lending_confirmed_schedule_past_warning").assertExists()
        rule.onNodeWithText("Αυτό δεν σημαίνει ότι εισπράχθηκαν",substring=true)
            .assertExists()
        rule.onNodeWithText("33,34",substring=true).assertExists()
    }

    @Test fun privacyMasksEveryInstallmentWhileKeepingDatesReadable() {
        rule.setContent { MyFinHubTheme {
            CanonicalConfirmedLendingPlanDetails(
                plan(),currentOutstanding=40.0,amountsVisible=false,
                asOf="2026-11-30",
            )
        } }
        rule.onNodeWithTag("lending_confirmed_schedule_row_3").assertExists()
        rule.onNodeWithText("2026-12-31",substring=true).assertExists()
        rule.onNodeWithText("100,00",substring=true).assertDoesNotExist()
        rule.onNodeWithText("40,00",substring=true).assertDoesNotExist()
        rule.onNodeWithText("33,33",substring=true).assertDoesNotExist()
        rule.onNodeWithText("33,34",substring=true).assertDoesNotExist()
    }

    @Test fun sixtyInstallmentsExpandOnlySixAtATime() {
        rule.setContent { MyFinHubTheme {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CanonicalConfirmedLendingPlanDetails(
                    plan(60,60.0),currentOutstanding=60.0,
                    amountsVisible=true,asOf="2026-08-17",
                )
            }
        } }
        rule.onNodeWithTag("lending_confirmed_schedule_row_6").assertExists()
        rule.onNodeWithTag("lending_confirmed_schedule_row_7").assertDoesNotExist()
        rule.onNodeWithTag("lending_confirmed_schedule_more")
            .performScrollTo().performClick()
        rule.onNodeWithTag("lending_confirmed_schedule_row_12").assertExists()
        rule.onNodeWithTag("lending_confirmed_schedule_row_13").assertDoesNotExist()
        rule.onNodeWithText("Μόνο καταγεγραμμένες επιστροφές",substring=true)
            .assertExists()
    }

    @Test fun malformedScheduleFailsClosedWithoutFinancialRows() {
        rule.setContent { MyFinHubTheme {
            CanonicalConfirmedLendingPlanDetails(
                plan().copy(total=0.001),null,true,asOf="2026-12-01")
        } }
        rule.onNodeWithTag("lending_confirmed_schedule_invalid").assertExists()
        rule.onNodeWithTag("lending_confirmed_schedule_row_1").assertDoesNotExist()
    }
}
