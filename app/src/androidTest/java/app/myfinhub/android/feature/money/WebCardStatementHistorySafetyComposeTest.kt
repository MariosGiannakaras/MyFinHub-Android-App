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
    @Test fun mismatchedCardStatementsNeverAppearOrEnableForeignPayment() {
        var paid = 0
        val own = statement(emptyList())
        val foreign = own.copy(
            id = "foreign-statement", cardId = "credit-2",
            openDate = "2099-01-01", remaining = 93521.0,
        )
        rule.setContent { MyFinHubTheme {
            CanonicalCardStatementHistorySection(
                card(emptyList()), listOf(own, foreign), amountsVisible = true,
                onOpenActivity = {}, onPayStatement = { _, _ -> paid++ },
            )
        } }
        rule.onNodeWithTag("card_statement_row_0").assertExists()
        rule.onNodeWithTag("card_statement_row_1").assertDoesNotExist()
        rule.onNodeWithText("2099-01-01", substring = true).assertDoesNotExist()
        rule.onNodeWithText("93.521", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("card_statement_pay_0").performClick()
        rule.runOnIdle { assertEquals(1, paid) }
    }

    @Test fun nonFiniteStatementMoneyIsMarkedInvalidAndCannotBePaid() {
        var paid = 0
        val invalid = statement(emptyList()).copy(
            remaining = Double.POSITIVE_INFINITY, purchaseTotal = Double.NaN,
        )
        rule.setContent { MyFinHubTheme {
            CanonicalCardStatementHistorySection(
                card(emptyList()), listOf(invalid), amountsVisible = true,
                onOpenActivity = {}, onPayStatement = { _, _ -> paid++ },
            )
        } }
        rule.onNodeWithTag("card_statement_pay_0").assertDoesNotExist()
        rule.onNodeWithText("Πληρωτέο Μη έγκυρο ποσό", substring = true).assertExists()
        rule.onNodeWithText("Αγορές Μη έγκυρο ποσό", substring = true).assertExists()
        rule.onNodeWithText("Infinity", substring = true).assertDoesNotExist()
        rule.onNodeWithText("NaN", substring = true).assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, paid) }
    }

    @Test fun invalidStatementAmountsStayPrivateUnderHideAmounts() {
        rule.setContent { MyFinHubTheme {
            CanonicalCardStatementHistorySection(
                card(emptyList()), listOf(statement(emptyList()).copy(remaining = Double.NaN)),
                amountsVisible = false, onOpenActivity = {}, onPayStatement = { _, _ -> },
            )
        } }
        rule.onNodeWithText("Μη έγκυρο ποσό", substring = true).assertDoesNotExist()
        rule.onNodeWithText("NaN", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("card_statement_pay_0").assertDoesNotExist()
    }

    @Test fun crossCardCollisionDisablesStatementLinkWithoutHidingUniqueNeighbor() {
        var opened: String? = null
        rule.setContent { MyFinHubTheme {
            CanonicalCardStatementHistorySection(
                card(listOf(purchase("shared-id"), purchase("valid-id"))),
                listOf(statement(listOf("shared-id", "valid-id"))),
                amountsVisible = true,
                onOpenActivity = { opened = it },
                onPayStatement = { _, _ -> },
                fullActivityIdCounts = mapOf("shared-id" to 2, "valid-id" to 1),
            )
        } }
        rule.onNodeWithTag("card_statement_expand_0").performClick()
        rule.onNodeWithTag("card_statement_link_warning_0_0").assertExists()
        rule.onNodeWithTag("card_statement_event_0_0").assertDoesNotExist()
        rule.onNodeWithTag("card_statement_event_0_1").performClick()
        rule.runOnIdle { assertEquals("valid-id", opened) }
    }

    @Test fun foreignCardStatementIdCollisionHidesPayAndLinkedControlsWithoutDroppingRow() {
        val colliding = statement(listOf("movement"))
        var pays = 0
        var opens = 0
        rule.setContent { MyFinHubTheme {
            CanonicalCardStatementHistorySection(
                card(listOf(purchase("movement"))), listOf(colliding),
                amountsVisible = false, onOpenActivity = { opens++ },
                onPayStatement = { _, _ -> pays++ },
                fullStatementIdCounts = mapOf("statement-1" to 2),
            )
        } }
        rule.onNodeWithTag("card_statement_row_0").assertExists()
        rule.onNodeWithTag("card_statement_pay_0").assertDoesNotExist()
        rule.onNodeWithTag("card_statement_expand_0").assertDoesNotExist()
        rule.onNodeWithText("Μη έγκυρο ή μη μοναδικό ID κατάστασης", substring = true)
            .assertExists()
        rule.onNodeWithText("100,00", substring = true).assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(0, pays)
            assertEquals(0, opens)
        }
    }

    @Test fun globallyUniqueStatementKeepsOriginalPaymentAndExpansionActions() {
        var pays = 0
        rule.setContent { MyFinHubTheme {
            CanonicalCardStatementHistorySection(
                card(listOf(purchase("movement"))), listOf(statement(listOf("movement"))),
                amountsVisible = true, onOpenActivity = {},
                onPayStatement = { _, _ -> pays++ },
                fullStatementIdCounts = mapOf("statement-1" to 1),
            )
        } }
        rule.onNodeWithTag("card_statement_pay_0").performClick()
        rule.onNodeWithTag("card_statement_expand_0").performClick()
        rule.onNodeWithTag("card_statement_event_0_0").assertExists()
        rule.runOnIdle { assertEquals(1, pays) }
    }

}
