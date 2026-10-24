package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web loans.ts loanPaidAmount honors immutable imported legacyPaidAmount. */
class WebLegacyLoanPaidAmountParityTest {
    private val document = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"loans":[
        {"id":"negotiated","name":"Renegotiated bank loan","total":1000,
          "installments":10,"installment":100,"paidCount":3,
          "legacyPaidAmount":180},
        {"id":"fully-forgiven-baseline","name":"Legacy zero principal","total":1000,
          "installments":10,"installment":100,"paidCount":3,
          "legacyPaidAmount":0},
        {"id":"original","name":"No historical amount override","total":1000,
          "installments":10,"installment":100,"paidCount":3},
        {"id":"cent-rounding","name":"Imported fractional legacy","total":100,
          "installments":10,"installment":10,"paidCount":2,
          "legacyPaidAmount":19.995}
      ]},"state":{"events":[
        {"id":"linked","kind":"expense","loanId":"negotiated",
          "date":"2026-10-15","amount":50,"installmentCount":1}
      ]}}
    """.trimIndent()).jsonObject)

    @Test fun explicitLegacyPaidAmountOverridesNominalInstallmentsAcrossAsOfSnapshots() {
        val original = document.raw
        val before = document.canonicalLoanBalanceRows("2026-10-12").associateBy { it.id }
        val after = document.canonicalLoanBalanceRows("2026-10-20").associateBy { it.id }
        assertEquals(820.0, before.getValue("negotiated").remaining, 0.00001)
        assertEquals(770.0, after.getValue("negotiated").remaining, 0.00001)
        assertEquals(7, before.getValue("negotiated").remainingInstallments)
        assertEquals(6, after.getValue("negotiated").remainingInstallments)
        assertEquals(1000.0, before.getValue("fully-forgiven-baseline").remaining, 0.00001)
        assertEquals(700.0, before.getValue("original").remaining, 0.00001)
        // Web Math.round(19.995 * 100) / 100 may round to 20.00 (binary
        // representation is authoritative); Android must use the same formula.
        assertEquals(80.0, before.getValue("cent-rounding").remaining, 0.01)
        assertEquals(original, document.raw)
    }

    @Test fun effectiveMonthlyLiabilityAndOutstandingAgreeWithWebAsOf() {
        val rows = document.canonicalLoanBalanceRows("2026-10-20")
        assertEquals(770.0, rows.first { it.id == "negotiated" }.remaining, 0.00001)
        assertEquals(rows.sumOf { it.remaining }, document.loanOutstanding("2026-10-20"), 0.00001)
        assertTrue(rows.all { it.remaining >= 0.0 })
    }
}
