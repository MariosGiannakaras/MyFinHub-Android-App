package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.core.data.CanonicalCreditStatementView
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Actual native credit-statement section, not a synthetic test-only preview. */
class WebCardStatementHistorySafetyComposeTest {
    @get:Rule val rule = createComposeRule()
    private fun card(events: List<MoneyCardActivity>) =
        MoneyCard("credit-1","Κάρτα","4422","credit",100.0,
            500.0,VaultState.LOCKED,activity=events)
    private fun statement(ids: List<String>) =
        CanonicalCreditStatementView(
            "statement-1","credit-1","2026-09-01","2026-09-30",
            "2026-10-25","include-closing-day",
            purchaseIds=ids,paymentIds=emptyList(),
            purchaseTotal=100.0,paymentTotal=0.0,
            remaining=100.0,status="due",
        )
    private fun purchase(id: String) =
        MoneyCardActivity(id,"2026-09-03","Αγορά",
            3.50,MoneyCardActivityKind.PURCHASE)

    @Test fun missingSourceLinkShowsWarningWithoutRawIdOrPaymentNavigation() {
        var opens=0
        val missing="raw-private-event-identity"
        rule.setContent { MyFinHubTheme {
            CanonicalCardStatementHistorySection(card(emptyList()),
                listOf(statement(listOf(missing))),amountsVisible=true,
                onOpenActivity={opens++},onPayStatement={_,_->})
        } }
        rule.onNodeWithTag("card_statement_expand_0").performClick()
        rule.onNodeWithTag("card_statement_link_warning_0_0").assertExists()
        rule.onNodeWithText(missing,substring=true).assertDoesNotExist()
        rule.onNodeWithTag("card_statement_event_0_0").assertDoesNotExist()
        rule.runOnIdle { assertEquals(0,opens) }
    }

    @Test fun privacyMasksAmountsWhileRealEventRemainsAvailable() {
        rule.setContent { MyFinHubTheme {
            CanonicalCardStatementHistorySection(card(listOf(purchase("a"))),
                listOf(statement(listOf("a"))),amountsVisible=false,
                onOpenActivity={},onPayStatement={_,_->})
        } }
        rule.onNodeWithTag("card_statement_expand_0").performClick()
        rule.onNodeWithTag("card_statement_event_0_0").assertExists()
        rule.onNodeWithText("100,00",substring=true).assertDoesNotExist()
        rule.onNodeWithText("3,50",substring=true).assertDoesNotExist()
    }

    @Test fun largeSourceLinkedHistoryShowsOnlyFifteenThenNextFifteen() {
        val ids=(1..32).map { "event-$it" }
        rule.setContent { MyFinHubTheme {
            Column(modifier=Modifier.verticalScroll(rememberScrollState())) {
                CanonicalCardStatementHistorySection(
                    card(ids.map(::purchase)),listOf(statement(ids)),
                    amountsVisible=true,onOpenActivity={},onPayStatement={_,_->})
            }
        } }
        rule.onNodeWithTag("card_statement_expand_0").performScrollTo().performClick()
        rule.onNodeWithTag("card_statement_linked_row_0_14").assertExists()
        rule.onNodeWithTag("card_statement_linked_row_0_15").assertDoesNotExist()
        rule.onNodeWithTag("card_statement_more_links_0").performScrollTo().performClick()
        rule.onNodeWithTag("card_statement_linked_row_0_29").assertExists()
        rule.onNodeWithTag("card_statement_linked_row_0_30").assertDoesNotExist()
    }

    @Test fun duplicatedHistoricSourceLinkIsNonNavigable() {
        val ids=listOf("double","double")
        rule.setContent { MyFinHubTheme {
            CanonicalCardStatementHistorySection(card(listOf(purchase("double"))),
                listOf(statement(ids)),amountsVisible=true,
                onOpenActivity={},onPayStatement={_,_->})
        } }
        rule.onNodeWithTag("card_statement_expand_0").performClick()
        rule.onNodeWithTag("card_statement_link_warning_0_0").assertExists()
        rule.onNodeWithTag("card_statement_event_0_0").assertDoesNotExist()
    }
}
