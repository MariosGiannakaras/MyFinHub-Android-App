package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLoansLendingSavingsAsOfParityTest {
    private val finance=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{
        "loans":[
          {"id":"bank","name":"Δάνειο","total":300,"installment":100,"installments":3,"paidCount":1,
           "provider":"bank","firstExpectedDate":"2026-10-21"},
          {"id":"self","name":"HELP","total":60,"installment":30,"installments":2,"paidCount":0}
        ],
        "lending":[
          {"person":"Άννα","outstanding":100,"entries":[{"date":"2026-01-01","lent":100}]},
          {"person":"Κώστας","outstanding":40,"entries":[]}
        ],
        "transactions":[
          {"id":"old-pay-save","date":"2026-10-02","type":"expense","amount":15,"note":"Pay & Save",
           "category":"Άλλο"}
        ]},
       "state":{"settings":{},"loanExtra":{},"events":[
         {"id":"bank-paid","kind":"loan_payment","loanId":"bank","date":"2026-10-10",
           "amount":100,"installmentCount":1},
         {"id":"bank-future","kind":"loan_payment","loanId":"bank","date":"2026-10-25",
           "amount":100,"installmentCount":1},
         {"id":"self-transfer","kind":"transfer","loanId":"self","date":"2026-10-12",
           "amount":30,"note":"ΕΠΙΣΤΡΟΦΗ βοήθειας"},
         {"id":"self-ignored","kind":"expense","loanId":"self","date":"2026-10-12",
           "amount":30,"note":"Αγορά"},
         {"id":"anna-lent","kind":"lending","date":"2026-10-08","person":"Άννα",
           "amount":30,"receivableDelta":30},
         {"id":"kostas-repaid","kind":"repayment","date":"2026-10-09","person":"Κώστας",
           "amount":15,"receivableDelta":-15},
         {"id":"anna-future","kind":"repayment","date":"2026-10-20","person":"Άννα",
           "amount":20,"receivableDelta":-20},
         {"id":"unattributed","kind":"lending","date":"2026-10-09","receivableDelta":8},
         {"id":"save-manual","kind":"saving_cash_offset","date":"2026-10-09","amount":20,
           "savingAmount":12,"note":"Μεταφορά στην αποταμίευση"},
         {"id":"save-cash","kind":"saving_cash_offset","date":"2026-10-10","amount":8,
           "note":"Μικρό ποσό"},
         {"id":"save-pay","kind":"saving_cash_offset","date":"2026-10-11","amount":5,
           "note":"Pay & Save"},
         {"id":"save-zero","kind":"saving_cash_offset","date":"2026-10-12","amount":100,
           "savingAmount":0,"note":"Pay & Save"},
         {"id":"save-future","kind":"saving_cash_offset","date":"2026-10-20","amount":1000,
           "note":"Pay & Save"}
       ],"customLoans":[
         {"id":"custom","name":"Άλλο δάνειο","total":80,"installment":40,"installments":2}
       ]}}
    """.trimIndent()).jsonObject)
    private val asOf="2026-10-15"

    @Test fun futureLoanPaymentsNeverReduceTodaysOutstandingAndSelfLoanRequiresReturnTransfer() {
        val rows=finance.canonicalLoanBalanceRows(asOf).associateBy { it.id }
        assertEquals(100.0,rows["bank"]!!.remaining,0.001)
        assertEquals(1,rows["bank"]!!.remainingInstallments)
        assertEquals(30.0,rows["self"]!!.remaining,0.001)
        assertEquals(80.0,rows["custom"]!!.remaining,0.001)
        assertEquals(210.0,finance.loanOutstanding(asOf),0.001)
        assertEquals(110.0,finance.loanOutstanding(),0.001)
    }

    @Test fun lendingPerPersonHonorsAsOfWhileLegacyBaselinesRemain() {
        val rows=finance.canonicalLendingPersonRows(asOf)
        assertEquals(listOf("Άννα","Κώστας"),rows.map { it.person })
        assertEquals(130.0,rows[0].outstanding,0.001)
        assertEquals(25.0,rows[1].outstanding,0.001)
        assertEquals(163.0,finance.receivableOutstanding(asOf),0.001)
        assertEquals(143.0,finance.receivableOutstanding(),0.001)
    }

    @Test fun payAndSaveIncludesLegacyAndExplicitZeroWithoutInflatingSaving() {
        val breakdown=finance.canonicalSavingsBreakdown("2026-10",asOf)
        assertEquals(20.0,breakdown.bySource["pay_and_save"]!!,0.001)
        assertEquals(12.0,breakdown.bySource["manual_transfer"]!!,0.001)
        assertEquals(8.0,breakdown.bySource["cash_offset"]!!,0.001)
        assertEquals(40.0,breakdown.total,0.001)
        assertEquals(5,breakdown.rows.size)
        assertTrue(breakdown.rows.any { it.id=="save-zero" && it.amount == 0.0 })
        assertEquals("save-zero",breakdown.rows.first().id)
    }
}
