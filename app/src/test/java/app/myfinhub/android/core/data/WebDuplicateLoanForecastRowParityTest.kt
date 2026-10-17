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
}
