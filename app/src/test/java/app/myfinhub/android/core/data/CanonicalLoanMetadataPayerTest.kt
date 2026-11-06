package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalLoanMetadataPayerTest {
    private fun doc(accounts: String): CanonicalFinanceDocument =
        CanonicalFinanceDocument(Json.parseToJsonElement("""
            {"seed":{"accounts":[$accounts],"loans":[]},
             "state":{"settings":{},"customLoans":[]}}
        """.trimIndent()).jsonObject)

    private val bank = """{"id":"salary","name":"Μισθοδοσία","kind":"bank"}"""
    private val duplicateCredit =
        """{"id":"salary","name":"Duplicate credit ID","kind":"credit"}"""
    private val cash = """{"id":"cash","name":"Μετρητά","kind":"cash"}"""

    private fun mutation(): UpsertCanonicalLoanMetadata {
        val loan=Json.parseToJsonElement("""
            {"id":"loan-metadata","name":"Laptop","source":"user",
             "kind":"installment","total":180,"installment":60,"installments":3,
             "defaultAccountId":"salary","expenseCategory":"Άλλο",
             "accountingMode":"expense-per-installment","paidCount":0,
             "forgivenAmount":0,"firstExpectedDate":"2026-11-10"}
        """.trimIndent()).jsonObject
        return UpsertCanonicalLoanMetadata(
            source="custom",loan=loan,expected=null,nowIso="2026-11-06T18:00:00Z",
        )
    }

    @Test fun onlyGloballyUniqueVerifiedNoncreditAccountsMayFundLoanMetadata() {
        val valid=doc("$bank,$cash")
        assertTrue(valid.hasUniqueLoanMetadataPayer("salary"))
        assertTrue(valid.hasUniqueLoanMetadataPayer("cash"))
        assertFalse(valid.hasUniqueLoanMetadataPayer("removed"))
        assertFalse(valid.hasUniqueLoanMetadataPayer(""))
        assertFalse(valid.hasUniqueLoanMetadataPayer(" salary "))
        assertFalse(doc("$bank,$bank").hasUniqueLoanMetadataPayer("salary"))
        assertFalse(doc("$bank,$duplicateCredit").hasUniqueLoanMetadataPayer("salary"))
        assertFalse(doc(duplicateCredit).hasUniqueLoanMetadataPayer("salary"))
    }

    @Test fun atomicCanonicalUpsertRefusesCrossKindOrHistoricalDuplicatePayer() {
        assertTrue(runCatching { mutation().apply(doc("$bank,$duplicateCredit")) }.isFailure)
        assertTrue(runCatching { mutation().apply(doc("$bank,$bank")) }.isFailure)
        val before=doc("$bank,$cash")
        val after=mutation().apply(before)
        assertTrue(after.state.array("customLoans").isNotEmpty())
        assertTrue(before.state.array("customLoans").isEmpty())
    }
}
