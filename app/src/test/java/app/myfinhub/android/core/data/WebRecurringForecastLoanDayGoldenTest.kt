package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web recurring.ts, loans.ts and forecast.ts shared historical day/date golden vectors. */
class WebRecurringForecastLoanDayGoldenTest {
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{
        "accounts":[{"id":"payroll","name":"Salary","kind":"bank","bankAccountCategory":"payroll"}],
        "snapshots":[{"date":"2026-10-01","balances":{"payroll":500}}],
        "recurring":[
          {"id":"bad-first","name":"Bad first","amount":20,
            "firstExpectedDate":"2026-10-XX","day":12,"active":true,
            "accountId":"payroll"},
          {"id":"day-fallback","name":"Fallback","amount":40,
            "day":12,"active":true,"accountId":"payroll"}
        ],
        "loans":[
          {"id":"greek-day","name":"Loan day","total":160,
            "installment":40,"installments":4,"paidCount":1,
            "day":"15η","defaultAccountId":"payroll"}
        ],
        "transactions":[]
      },"state":{"events":[],"scheduled":[],"settings":{}}}
    """.trimIndent()).jsonObject)

    @Test fun malformedAuthoritativeRecurringAnchorNeverFallsBackToDay() {
        val recurring = doc.allCanonicalRecurringItems().associateBy { it.id }
        val asOf = LocalDate.parse("2026-10-14")
        assertEquals(null, doc.canonicalTypicalRecurringPaymentDay(recurring["bad-first"]!!))
        assertEquals(null, doc.nextCanonicalRecurringDate(recurring["bad-first"]!!, asOf))
        assertEquals(12, doc.canonicalTypicalRecurringPaymentDay(recurring["day-fallback"]!!))
        assertEquals(LocalDate.parse("2026-11-12"),
            doc.nextCanonicalRecurringDate(recurring["day-fallback"]!!,asOf))
    }

    @Test fun loanForecastUsesSameGreekDayAndOutstandingAsMoneyProjection() {
        val asOf = LocalDate.parse("2026-10-14")
        val balance = doc.canonicalLoanBalanceRows(asOf.toString()).single()
        assertEquals(15, balance.typicalPaymentDay)
        assertEquals(120.0, balance.remaining, 0.001)
        assertEquals(3, balance.remainingInstallments)
        val forecast = doc.cashFlowForecast(asOf,30)
        val loans = forecast.movements.filter { it.source == "loan" }
        assertEquals(listOf(LocalDate.parse("2026-10-15")),loans.map { it.date })
        assertEquals(-40.0, loans.single().portfolioDelta, 0.001)
        assertEquals(1, forecast.sourceCounts["loan"])
        assertEquals(1, forecast.omitted.count { it == "Το πάγιο «Bad first» δεν προβλήθηκε επειδή δεν υπάρχει αρκετή πληροφορία ημερομηνίας." })
    }

    @Test fun unavailableRecurringDateIsOmittedAndDoesNotInventACharge() {
        val result = doc.cashFlowForecast(LocalDate.parse("2026-10-14"),30)
        assertTrue(result.omitted.any { it == "Το πάγιο «Bad first» δεν προβλήθηκε επειδή δεν υπάρχει αρκετή πληροφορία ημερομηνίας." })
        assertEquals(420.0, result.projectedPortfolio, 0.001)
        assertEquals(500.0, result.currentPortfolio, 0.001)
    }
}
