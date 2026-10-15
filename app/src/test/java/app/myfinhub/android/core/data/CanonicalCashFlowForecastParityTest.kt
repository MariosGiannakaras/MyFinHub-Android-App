package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web forecast.ts golden account portfolio and movement vectors. */
class CanonicalCashFlowForecastParityTest {
    private fun document(
        scheduled: String = "[]",
        recurring: String = "[]",
        loans: String = "[]",
        events: String = "[]",
    ) = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[
        {"id":"bank","name":"Bank","kind":"bank"},
        {"id":"savings","name":"Savings","kind":"savings","excludeFromAvailable":true}
       ],"transactions":[],
       "snapshots":[{"date":"2026-08-17","balances":{"bank":1000,"savings":500}}],
       "recurring":[],"loans":[]},
       "state":{"settings":{"defaultLoanAccount":"bank"},"events":$events,
         "scheduled":$scheduled,"recurringCustom":$recurring,"customLoans":$loans}}
    """.trimIndent()).jsonObject)

    @Test fun transfersMoveAccountsWithoutChangingPortfolio() {
        val d=document(scheduled="""[
          {"id":"t","dueDate":"2026-08-22","kind":"transfer","amount":200,"status":"pending",
            "fromAccountId":"bank","toAccountId":"savings","note":"Transfer"}]""")
        val f=d.canonicalCashFlowForecast("2026-08-17",30)
        assertEquals(1500.0,f.currentPortfolio,0.001)
        assertEquals(1500.0,f.projectedPortfolio,0.001)
        assertEquals(0.0,f.movements.single().portfolioDelta,0.001)
        assertEquals(800.0,f.accounts.first { it.accountId=="bank" }.projected,0.001)
    }

    @Test fun annualRecurringDoesNotRepeatMonthly() {
        val d=document(recurring="""[
          {"id":"annual","name":"Annual plan","amount":120,
           "firstExpectedDate":"2023-09-03","day":3,"recurrenceUnit":"year",
           "accountId":"bank","category":"Bills","active":true}]""")
        val f=d.canonicalCashFlowForecast("2026-08-25",90)
        assertEquals(1,f.sourceCounts["recurring"])
        assertEquals("2026-09-03",f.movements.single().date)
        assertEquals(-120.0,f.movements.single().portfolioDelta,0.001)
    }

    @Test fun scheduledRecurringAndLoanAreForecastIndependently() {
        val d=document(
            scheduled="""[{"id":"inc","dueDate":"2026-08-25","kind":"income","amount":300,
             "status":"pending","note":"Income","accountId":"bank"}]""",
            recurring="""[{"id":"internet","name":"Internet","amount":40,"day":20,
             "accountId":"bank","category":"Bills","active":true}]""",
            loans="""[{"id":"laptop","name":"Laptop","total":600,"installment":100,
             "installments":6,"paidCount":2,"day":"25","defaultAccountId":"bank","kind":"loan"}]""",
        )
        val f=d.canonicalCashFlowForecast("2026-08-17",30)
        assertEquals(1,f.sourceCounts["scheduled"])
        assertEquals(1,f.sourceCounts["recurring"])
        assertEquals(1,f.sourceCounts["loan"])
        assertEquals(1660.0,f.projectedPortfolio,0.001)
    }

    @Test fun staleAccountIsOmittedAndNoBalanceIsInvented() {
        val d=document(scheduled="""[{"id":"stale","dueDate":"2026-08-18","kind":"expense",
           "amount":50,"status":"pending","accountId":"absent","note":"Old account"}]""")
        val f=d.canonicalCashFlowForecast("2026-08-17",30)
        assertEquals(1500.0,f.projectedPortfolio,0.001)
        assertTrue(f.omitted.single().contains("Old account"))
        assertEquals(true,f.insufficientData)
    }

    @Test fun overdueAndFutureEventAreAppliedOnlyAsForecast() {
        val d=document(
            scheduled="""[{"id":"late","dueDate":"2026-08-10","kind":"expense","amount":100,
             "status":"pending","accountId":"bank","note":"Late"}]""",
            events="""[{"id":"future","date":"2026-08-20","kind":"income","amount":60,
             "legs":[{"accountId":"bank","amount":60}]}]""",
        )
        val f=d.canonicalCashFlowForecast("2026-08-17",30)
        assertEquals("2026-08-17",f.movements.first().date)
        assertEquals(1460.0,f.projectedPortfolio,0.001)
        assertEquals(1400.0,f.minimumPortfolio,0.001)
    }

    @Test fun lowAndNegativeBalanceWarningsTrackFirstDueDate() {
        val d=document(scheduled="""[{"id":"rent","dueDate":"2026-08-20","kind":"expense",
          "amount":1050,"status":"pending","accountId":"bank","note":"Rent"}]""")
        val f=d.canonicalCashFlowForecast("2026-08-17",30)
        val account=f.accounts.first { it.accountId=="bank" }
        assertEquals("2026-08-20",account.firstLowDate)
        assertEquals("2026-08-20",account.firstNegativeDate)
        assertEquals(-50.0,account.minimum,0.001)
    }
}
