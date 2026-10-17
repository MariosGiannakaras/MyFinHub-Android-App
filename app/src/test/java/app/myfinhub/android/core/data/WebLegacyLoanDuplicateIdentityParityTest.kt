package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Web loans.ts: effective seed rows aren't deduped by ID; overrides replace each source row. */
class WebLegacyLoanDuplicateIdentityParityTest {
    private fun doc(overrides: String = "{}"): CanonicalFinanceDocument =
        CanonicalFinanceDocument(Json.parseToJsonElement("""
            {"seed":{"loans":[
              {"id":"dup","name":"Historical A","total":100,
                "installment":50,"installments":2,"day":"12"},
              {"id":"dup","name":"Historical B","total":300,
                "installment":100,"installments":3,"day":"14"},
              {"id":"single","name":"Other","total":40,
                "installment":20,"installments":2,"day":"16"}
            ]},"state":{"loanOverrides":$overrides,
              "customLoans":[
                {"id":"dup","name":"Custom same ID","total":80,
                  "installment":40,"installments":2,"day":"20"}
              ],"events":[]}}
        """.trimIndent()).jsonObject)

    @Test fun historicSeedRowsAndCustomLoanWithRepeatedIdStayDistinct() {
        val rows=doc().canonicalLoanBalanceRows()
        assertEquals(listOf("Historical A","Historical B","Other","Custom same ID"),
            rows.map { it.name })
        assertEquals(listOf(100.0,300.0,40.0,80.0),rows.map { it.remaining })
        assertEquals(520.0,doc().loanOutstanding(),0.001)
    }

    @Test fun oneSeedOverrideAppliesToEveryMatchingHistoricalRowWithoutDroppingCustom() {
        val overridden=doc("""{
          "dup":{"id":"dup","name":"Edited seed","total":210,
            "installment":70,"installments":3,"day":"22"},
          "orphan":{"id":"orphan","name":"Should not appear","total":9999}
        }""")
        val rows=overridden.canonicalLoanBalanceRows("2026-10-17")
        assertEquals(listOf("Edited seed","Edited seed","Other","Custom same ID"),
            rows.map { it.name })
        assertEquals(listOf(210.0,210.0,40.0,80.0),rows.map { it.remaining })
        assertEquals(540.0,overridden.loanOutstanding("2026-10-17"),0.001)
        assertFalse(rows.any { it.id=="orphan" })
    }

    @Test fun duplicateIdentitiesKeepIndependentCountsWhileSharingLinkedHistory() {
        val withPaid=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"loans":[
            {"id":"dup","name":"A","total":150,"installment":50,"installments":3},
            {"id":"dup","name":"B","total":90,"installment":30,"installments":3}
          ]},"state":{"events":[
            {"id":"paid","loanId":"dup","date":"2026-10-12",
             "kind":"expense","amount":20,"installmentCount":1}
          ]}}
        """.trimIndent()).jsonObject)
        val rows=withPaid.canonicalLoanBalanceRows("2026-10-17")
        assertEquals(listOf(130.0,70.0),rows.map { it.remaining })
        assertEquals(listOf(2,2),rows.map { it.remainingInstallments })
    }
}
