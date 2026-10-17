package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Web src/lib/forecast.ts effectiveLoans and loanMovements retain each loan
 * occurrence, even with the same historical ID. Read-only forecasting must
 * not use associateBy(id), since that erases row-local paidCount/remaining.
 */
class WebDuplicateLoanForecastRowParityTest {
    private fun finance(reverse: Boolean): CanonicalFinanceDocument {
        val unpaid = """
          {"id":"shared-loan","name":"Ανεξόφλητο","total":80,"installment":40,
           "installments":2,"paidCount":1,"day":"18","defaultAccountId":"salary"}
        """.trimIndent()
        val settled = """
          {"id":"shared-loan","name":"Εξοφλημένο","total":80,"installment":40,
           "installments":2,"paidCount":2,"day":"18","defaultAccountId":"salary"}
        """.trimIndent()
        val seedLoans = if (reverse) "$settled,$unpaid" else "$unpaid,$settled"
        return CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{
            "accounts":[{"id":"salary","kind":"bank","bankAccountCategory":"payroll"}],
            "snapshots":[{"date":"2026-10-01","balances":{"salary":500}}],
            "loans":[$seedLoans],
            "transactions":[]
           },"state":{"events":[],"settings":{}}}
        """.trimIndent()).jsonObject)
    }

    @Test fun earlierUnpaidDuplicateIsNotErasedByLaterSettledRow() {
        val document = finance(reverse = false)
        val rows = document.canonicalLoanBalanceRows("2026-10-17")
        assertEquals(listOf(1, 0), rows.map { it.remainingInstallments })
        assertEquals(listOf(40.0, 0.0), rows.map { it.remaining })
        val result = document.cashFlowForecast(LocalDate.parse("2026-10-17"),30)
        val loanMoves = result.movements.filter { it.source == "loan" }
        assertEquals(1,loanMoves.size)
        assertEquals("Ανεξόφλητο", loanMoves.single().label)
        assertEquals(LocalDate.parse("2026-10-18"),loanMoves.single().date)
        assertEquals(-40.0,loanMoves.single().portfolioDelta,0.001)
        assertEquals(460.0,result.projectedPortfolio,0.001)
        assertEquals(1,result.sourceCounts["loan"])
    }

    @Test fun laterUnpaidDuplicateIsNotOverAppliedToSettledRow() {
        val document = finance(reverse = true)
        val rows = document.canonicalLoanBalanceRows("2026-10-17")
        assertEquals(listOf(0, 1), rows.map { it.remainingInstallments })
        val result = document.cashFlowForecast(LocalDate.parse("2026-10-17"),30)
        val loanMoves = result.movements.filter { it.source == "loan" }
        assertEquals(1,loanMoves.size)
        assertEquals("Ανεξόφλητο", loanMoves.single().label)
        assertEquals(460.0,result.projectedPortfolio,0.001)
        assertEquals(1,result.sourceCounts["loan"])
        assertTrue(result.omitted.isEmpty())
    }

    private fun fromLoans(loans: String, settings: String = "{}"): CanonicalFinanceDocument =
        CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{
            "accounts":[{"id":"salary","kind":"bank","bankAccountCategory":"payroll"}],
            "snapshots":[{"date":"2026-10-01","balances":{"salary":500}}],
            "loans":[$loans],
            "transactions":[]
          },"state":{"events":[],"settings":$settings}}
        """.trimIndent()).jsonObject)

    @Test fun bothOpenDuplicateRowsContributeIndependentlyAcrossHorizons() {
        val document = fromLoans("""
          {"id":"repeated","name":"Πρώτο","total":80,"installment":40,
           "installments":2,"paidCount":0,"day":"18","defaultAccountId":"salary"},
          {"id":"repeated","name":"Δεύτερο","total":80,"installment":40,
           "installments":2,"paidCount":1,"day":"18","defaultAccountId":"salary"}
        """.trimIndent())
        val asOf = LocalDate.parse("2026-10-17")
        val thirty = document.cashFlowForecast(asOf, 30)
        val sixty = document.cashFlowForecast(asOf, 60)
        val ninety = document.cashFlowForecast(asOf, 90)
        assertEquals(listOf("Πρώτο", "Δεύτερο"),
            thirty.movements.filter { it.source == "loan" }.map { it.label })
        assertEquals(2, thirty.sourceCounts["loan"])
        assertEquals(420.0, thirty.projectedPortfolio, 0.001)
        assertEquals(3, sixty.sourceCounts["loan"])
        assertEquals(380.0, sixty.projectedPortfolio, 0.001)
        assertEquals(3, ninety.sourceCounts["loan"])
        assertEquals(380.0, ninety.projectedPortfolio, 0.001)
        assertTrue(thirty.omitted.isEmpty())
    }

    @Test fun emptyLegacyAccountIdUsesWebDefaultLoanAccount() {
        val document = fromLoans("""
          {"id":"blank-account","name":"Με προεπιλογή","total":25,"installment":25,
           "installments":1,"day":"18","defaultAccountId":""}
        """.trimIndent(), settings = """{"defaultLoanAccount":"salary"}""")
        val forecast = document.cashFlowForecast(LocalDate.parse("2026-10-17"), 30)
        assertEquals(1, forecast.sourceCounts["loan"])
        assertEquals("salary", forecast.movements.single().legs.single().accountId)
        assertEquals(475.0, forecast.projectedPortfolio, 0.001)
        assertTrue(forecast.omitted.isEmpty())
    }

    @Test fun negativeLegacyInstallmentCannotCreateAnInventedPositiveDebit() {
        val document = fromLoans("""
          {"id":"negative","name":"Αρνητική δόση","total":100,"installment":-50,
           "installments":2,"day":"18","defaultAccountId":"salary"}
        """.trimIndent())
        val forecast = document.cashFlowForecast(LocalDate.parse("2026-10-17"), 90)
        assertEquals(0, forecast.sourceCounts["loan"])
        assertEquals(500.0, forecast.projectedPortfolio, 0.001)
    }

    @Test fun webForecastUsesRemainingInstallmentCountEvenWhenDebtWasForgiven() {
        // Web forecast.ts checks loanRemainingInstallments, not loanOutstanding.
        // This is a read-only parity vector; real payment mutations have stricter guards.
        val document = fromLoans("""
          {"id":"forgiven","name":"Διαγραφή οφειλής","total":30,"installment":30,
           "forgivenAmount":30,"installments":1,"paidCount":0,
           "day":"18","defaultAccountId":"salary"}
        """.trimIndent())
        assertEquals(0.0, document.canonicalLoanBalanceRows("2026-10-17").single().remaining, 0.001)
        val forecast = document.cashFlowForecast(LocalDate.parse("2026-10-17"), 30)
        assertEquals(1, forecast.sourceCounts["loan"])
        assertEquals(470.0, forecast.projectedPortfolio, 0.001)
    }
}
