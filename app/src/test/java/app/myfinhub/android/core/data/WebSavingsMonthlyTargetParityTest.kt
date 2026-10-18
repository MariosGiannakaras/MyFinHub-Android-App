package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Web SavingsPage rate target, signed operational flow, as-of and prior-month goldens. */
class WebSavingsMonthlyTargetParityTest {
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
        {"seed":{"transactions":[
          {"id":"pay-save","type":"expense","date":"2026-10-04","amount":3,"note":"Pay & Save"},
          {"id":"future","type":"expense","date":"2026-10-24","amount":300,"note":"Pay & Save"}
        ]},"state":{"settings":{"savingsTargetRate":0.25},"events":[
          {"id":"salary","kind":"income","date":"2026-10-10","amount":200},
          {"id":"manual","kind":"saving_cash_offset","date":"2026-10-11","amount":8,
           "savingAmount":8,"savingSource":"manual_transfer"},
          {"id":"previous","kind":"saving_cash_offset","date":"2026-09-25","amount":20,
           "savingAmount":20},
          {"id":"following","kind":"saving_cash_offset","date":"2026-11-02","amount":800}
        ]}}
    """.trimIndent()).jsonObject)

    @Test fun monthlyTargetTracksRealSavingsRateNotAccountBalance() {
        val snapshot = doc.webSavingsMonthlyTargetSnapshot("2026-10","2026-10-18")
        assertEquals(200.0,snapshot.income,0.001)
        assertEquals(11.0,snapshot.saved,0.001)
        assertEquals(0.25,snapshot.targetRate,0.001)
        assertEquals(50.0,snapshot.targetAmount,0.001)
        assertEquals(22.0,snapshot.progressPercent!!,0.001)
        assertEquals(20.0,snapshot.previousMonthSaved,0.001)
        assertEquals("2026-10",snapshot.month)
    }

    @Test fun previousMonthIgnoresCurrentMonthAsOfCutoffAndFutureMonth() {
        assertEquals(20.0,doc.webSavingsMonthlyTargetSnapshot("2026-10","2026-10-04").previousMonthSaved,0.001)
        assertEquals(0.0,doc.webSavingsMonthlyTargetSnapshot("2026-10","2026-10-04").income,0.001)
        assertEquals(0.0,doc.webSavingsMonthlyTargetSnapshot("2026-10","2026-10-04").targetAmount,0.001)
        assertEquals(0.0,doc.webSavingsMonthlyTargetSnapshot("2026-10","2026-10-04").progressPercent!!,0.001)
    }

    @Test fun nullishTargetDefaultsTo20PercentButExplicitZeroDoesNot() {
        val missing = CanonicalFinanceDocument(Json.parseToJsonElement(
            """{"seed":{},"state":{"settings":{},"events":[]}}"""
        ).jsonObject)
        assertEquals(0.2,missing.webSavingsMonthlyTargetSnapshot("2026-10").targetRate,0.001)
        val zero = CanonicalFinanceDocument(Json.parseToJsonElement(
            """{"seed":{},"state":{"settings":{"savingsTargetRate":0},"events":[]}}"""
        ).jsonObject)
        val performance=zero.webSavingsMonthlyTargetSnapshot("2026-10")
        assertEquals(0.0,performance.targetRate,0.001)
        assertNull(performance.progressPercent)
        assertEquals(0.0,performance.targetAmount,0.001)
    }
}
