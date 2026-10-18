package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Golden 5-point Web SavingsPage actual-vs-proportional-target chart. */
class WebSavingsMonthlyTrendParityTest {
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
        {"seed":{"transactions":[
            {"id":"pns","date":"2026-10-04","type":"expense","amount":3,"note":"Pay & Save"},
            {"id":"future","date":"2026-10-27","type":"expense","amount":50,"note":"Pay & Save"}
        ]},"state":{"settings":{"savingsTargetRate":0.25},"events":[
            {"id":"salary","kind":"income","date":"2026-10-03","amount":200},
            {"id":"manual","kind":"saving_cash_offset","date":"2026-10-11",
             "amount":8,"savingAmount":8,"savingSource":"manual_transfer"},
            {"id":"future-event","kind":"saving_cash_offset","date":"2026-10-29",
             "amount":150,"savingAmount":150}
        ]}}
    """.trimIndent()).jsonObject)

    @Test fun fiveSamplesFollowWebDayOfMonthSamplingAndCutoff() {
        val trend=doc.webSavingsMonthlyTrend("2026-10","2026-10-18")
        assertEquals(listOf(1,8,15,22,31),trend.days)
        assertEquals(listOf(0.0,3.0,11.0,11.0,11.0),trend.actual)
        assertEquals(50.0/31.0,trend.target[0],0.001)
        assertEquals(50.0,trend.target.last(),0.001)
        assertEquals(50.0,trend.scaleMax,0.001)
        assertEquals(trend.actual.last(),doc.canonicalSavingsBreakdown("2026-10","2026-10-18").total,0.001)
    }

    @Test fun monthEndsAndZeroIncomeAreHandledWithoutSyntheticMoney() {
        val previous=doc.webSavingsMonthlyTrend("2026-02")
        assertEquals(listOf(1,8,15,22,28),previous.days)
        assertEquals(listOf(0.0,0.0,0.0,0.0,0.0),previous.actual)
        assertEquals(listOf(0.0,0.0,0.0,0.0,0.0),previous.target)
        assertEquals(1.0,previous.scaleMax,0.001)
        assertEquals(29,doc.webSavingsMonthlyTrend("2024-02").days.last())
    }

    @Test fun monthOutsideCutoffStillIncludesAllRecordsForThatCalendarMonth() {
        val trend = doc.webSavingsMonthlyTrend("2026-10","2026-09-18")
        assertEquals(211.0,trend.actual.last(),0.001)
        assertEquals(211.0,trend.scaleMax,0.001)
    }

    @Test fun cutoffBeforeAnySavingsDoesNotLeakFutureAmountsThroughTrend() {
        val trend = doc.webSavingsMonthlyTrend("2026-10","2026-10-01")
        assertEquals(listOf(0.0,0.0,0.0,0.0,0.0),trend.actual)
        assertEquals(1.0,trend.scaleMax,0.001)
    }

    @Test fun noAsOfIncludesAllHistoryWithoutDoubleCountingEvents() {
        val t=doc.webSavingsMonthlyTrend("2026-10")
        assertEquals(211.0,t.actual.last(),0.001)
        assertEquals(211.0,t.scaleMax,0.001)
    }
}
