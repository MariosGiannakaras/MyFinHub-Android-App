package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebReportLoanBurdenParityTest {
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"loans":[
        {"id":"large","name":"Μακρύ δάνειο","total":1800,
         "installment":100,"installments":18,"paidCount":2,
         "day":"20","longTermRecurring":true},
        {"id":"short","name":"Σύντομο","total":200,
         "installment":50,"installments":4,"day":"3"},
        {"id":"self","name":"HELP self","kind":"self-loan",
         "total":2400,"installment":200,"installments":12,"day":"4"},
        {"id":"disabled","name":"Disabled","total":2000,
         "installment":100,"installments":20,"day":"3"},
        {"id":"small","name":"Μικρό υπόλοιπο","total":2900,
         "installment":250,"installments":12,"paidCount":11,"day":"10"}
      ]},"state":{"settings":{},"loanOverrides":{
        "disabled":{"id":"disabled","name":"Disabled","total":2000,
          "installment":100,"installments":20,"day":"3","longTermRecurring":false}
      },"events":[
        {"id":"future","kind":"expense","date":"2026-10-23",
         "loanId":"large","amount":100,"installmentCount":1}
      ]}}
    """.trimIndent()).jsonObject)

    @Test fun webLongTermLoanBurdenKeepsOnlyCurrentEligibleNextDues() {
        val before=doc.raw
        val result=doc.canonicalReportLoanBurden("2026-10-19")
        assertEquals(2,result.count)
        assertEquals(listOf("small","large"),result.rows.map { it.id })
        assertEquals(listOf(150.0,100.0),result.rows.map { it.amount })
        assertEquals(250.0,result.total,0.001)
        assertEquals(1600.0,result.rows.last().outstanding,0.001)
        assertEquals(1,result.rows.first().remainingInstallments)
        assertEquals(before,doc.raw)
    }

    @Test fun datedFutureLoanPaymentsAreNotCountedBeforeTheyOccur() {
        val early=doc.canonicalReportLoanBurden("2026-10-19")
        val later=doc.canonicalReportLoanBurden("2026-10-24")
        assertEquals(1600.0,early.rows.last().outstanding,0.001)
        assertEquals(1500.0,later.rows.last().outstanding,0.001)
        assertTrue(later.total>0)
    }
}
