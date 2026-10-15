package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Web flowImpactEvent uses savingAmount ?? amount, not a non-zero test. */
class WebSavingsAmountPresenceParityTest {
    private fun finance(savingAmountField: String): CanonicalFinanceDocument =
        CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[],"transactions":[]},
           "state":{"settings":{},"events":[
              {"id":"save","kind":"saving_cash_offset","date":"2026-10-15",
               "amount":40,"note":"Savings"$savingAmountField}
           ]}}
        """.trimIndent()).jsonObject)

    @Test fun explicitZeroIsNotAccidentallyCountedAsSavings() {
        val data = finance(",\"savingAmount\":0")
        assertEquals(0.0,data.monthlyFlow("2026-10").saving,0.001)
        assertEquals(0.0,data.canonicalEvents().single().savingAmount,0.001)
        assertEquals(true,data.canonicalEvents().single().savingAmountWasProvided)
    }

    @Test fun missingAndNullSavingsFieldUseWebAmountFallback() {
        val missing = finance("")
        val explicitNull = finance(",\"savingAmount\":null")
        assertEquals(40.0,missing.monthlyFlow("2026-10").saving,0.001)
        assertEquals(40.0,explicitNull.monthlyFlow("2026-10").saving,0.001)
        assertEquals(false,missing.canonicalEvents().single().savingAmountWasProvided)
        assertEquals(false,explicitNull.canonicalEvents().single().savingAmountWasProvided)
    }

    @Test fun explicitPositiveSavingsFieldTakesPrecedenceOverAmount() {
        val data = finance(",\"savingAmount\":25.50")
        assertEquals(25.50,data.monthlyFlow("2026-10").saving,0.001)
        assertEquals(40.0,data.canonicalEvents().single().amount,0.001)
    }
}
