package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Web loans.ts loanPaymentEvents: missing/null dates do not satisfy date <= asOf. */
class WebLoanMissingDateAsOfParityTest {
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[{"id":"bank","name":"Payroll","kind":"bank"}],
        "snapshots":[{"date":"2026-10-01","balances":{"bank":500}}],
        "loans":[{"id":"l","name":"Loan","total":400,
          "installment":100,"installments":4,"day":"21",
          "defaultAccountId":"bank"}],
        "transactions":[],"recurring":[]},
       "state":{"events":[
         {"id":"undated","kind":"expense","amount":100,
          "loanId":"l","installmentCount":1,"legs":[]},
         {"id":"null-date","kind":"expense","date":null,"amount":25,
          "loanId":"l","installmentCount":1,"legs":[]},
         {"id":"past","kind":"expense","date":"2026-10-10","amount":100,
          "loanId":"l","installmentCount":1,"legs":[]},
         {"id":"future","kind":"expense","date":"2026-11-05","amount":50,
          "loanId":"l","installmentCount":1,"legs":[]}
       ],"settings":{}}}
    """.trimIndent()).jsonObject)
    @Test fun missingAndNullDatesAreExcludedFromAsOfButCountAllTime() {
        val current=doc.canonicalLoanBalanceRows("2026-10-17").single()
        assertEquals(300.0,current.remaining,0.001)
        assertEquals(3,current.remainingInstallments)
        assertEquals(10,current.typicalPaymentDay)
        val all=doc.canonicalLoanBalanceRows().single()
        assertEquals(125.0,all.remaining,0.001)
        assertEquals(0,all.remainingInstallments)
    }

    @Test fun loanForecastSharesSameDateScopedPaidInstallmentCount() {
        val current=doc.cashFlowForecast(LocalDate.parse("2026-10-17"),30)
        assertEquals(1,current.sourceCounts["loan"])
        assertEquals(listOf("2026-11-10"),
            current.movements.filter { it.source=="loan" }
                .map { it.date.toString() })
        val past=doc.canonicalLoanBalanceRows("2026-10-09").single()
        assertEquals(400.0,past.remaining,0.001)
        assertEquals(4,past.remainingInstallments)
    }
}
