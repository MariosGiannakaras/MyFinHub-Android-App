package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Web effectiveLoans processes each source loan independently. A repeated ID
 * must not misrepresent the second row's HELP/status/accounting mode in Money.
 */
class WebDuplicateLoanReadOnlyMetadataParityTest {
    @Test fun duplicateHistoricalLoanRowsRetainTheirOwnModesAndDefaultAccounts() {
        val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[
            {"id":"payroll","kind":"bank","bankAccountCategory":"payroll"},
            {"id":"saving","kind":"savings"}
           ],"snapshots":[{"date":"2026-10-01","balances":{"payroll":500,"saving":100}}],
           "loans":[
              {"id":"shared","name":"Bank liability","total":120,"installment":40,
               "installments":3,"accountingMode":"liability-repayment",
               "defaultAccountId":"payroll"},
              {"id":"shared","name":"ΒΟΗΘΕΙΑ","kind":"self-loan","source":"self-loan",
               "total":80,"installment":40,"installments":2,
               "defaultAccountId":"saving"}
           ],"recurring":[],"transactions":[],"lending":[]},"state":{
             "events":[],"settings":{},"cards":[],"scheduled":[]}}
        """.trimIndent()).jsonObject)
        val today=LocalDate.parse("2026-10-17")
        val money=projectCanonicalProduct(doc,today).moneyState
        val rows=money.loans
        assertEquals(2,rows.size)
        assertEquals(listOf("Bank liability","ΒΟΗΘΕΙΑ"),rows.map { it.name })
        assertEquals(listOf(false,true),rows.map { it.isSelfLoan })
        assertEquals(listOf(true,false),rows.map { it.liabilityRepayment })
        assertEquals(listOf("payroll","saving"),rows.map { it.defaultAccountId })
        assertEquals(200.0,money.loanOutstanding,0.001)
    }
}
