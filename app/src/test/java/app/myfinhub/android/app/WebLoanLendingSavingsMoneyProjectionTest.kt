package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden integration: production Money account/loan/receivable sources are as-of, not mock rows. */
class WebLoanLendingSavingsMoneyProjectionTest {
    @Test fun realLoanLendingAndPayAndSaveRecordsReachMoneyDetailWithoutFutureDebtRewrite() {
        val document=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[{"id":"cash","kind":"cash","name":"Μετρητά"}],
             "snapshots":[{"date":"2026-10-01","balances":{"cash":200}}],
             "transactions":[{"id":"old-saving","type":"expense","date":"2026-10-04",
                 "amount":11,"note":"Pay & Save","category":"Άλλο","accountId":"cash"}],
             "loans":[{"id":"bank-loan","name":"Στεγαστικό","provider":"Τράπεζα",
                 "total":200,"installment":100,"installments":2,"day":"20"}],
             "lending":[{"person":"Άννα","outstanding":60,"entries":[]}]},
           "state":{"settings":{},"events":[
             {"id":"loan-past","kind":"loan_payment","date":"2026-10-10",
                 "loanId":"bank-loan","amount":100},
             {"id":"loan-future","kind":"loan_payment","date":"2026-10-25",
                 "loanId":"bank-loan","amount":100},
             {"id":"lending-past","kind":"repayment","date":"2026-10-11",
                 "person":"Άννα","amount":20,"receivableDelta":-20},
             {"id":"saving-transfer","kind":"saving_cash_offset","date":"2026-10-12",
                 "amount":23,"savingAmount":13,"savingSource":"manual_transfer"}
           ]}}
        """.trimIndent()).jsonObject)
        val product=projectCanonicalProduct(document,LocalDate.parse("2026-10-15"))
        assertEquals(100.0,product.moneyState.loanOutstanding,0.001)
        assertEquals("Στεγαστικό",product.moneyState.loans.single().name)
        assertEquals(100.0,product.moneyState.loans.single().remaining,0.001)
        assertEquals("Συνήθης ημέρα πληρωμής: 10",product.moneyState.loans.single().nextPaymentLabel)
        assertEquals(40.0,product.moneyState.lendingReceivable,0.001)
        assertEquals("Άννα",product.moneyState.lendingItems.single().personLabel)
        assertEquals(40.0,product.moneyState.lendingItems.single().amount,0.001)
        assertEquals(11.0,product.moneyState.savingsSources["pay_and_save"]!!,0.001)
        assertEquals(13.0,product.moneyState.savingsSources["manual_transfer"]!!,0.001)
        assertTrue(product.moneyState.savingsSources.values.sum() > 0.0)
    }
}
