package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.cashFlowForecast
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web PlanningPage fixture: real 30/60/90 canonical step trajectory and account warnings. */
class CanonicalForecastDetailProjectionTest {
    private val asOf = LocalDate.parse("2026-10-15")
    private val document = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[
          {"id":"payroll","kind":"bank","name":"Μισθοδοσία","bankAccountCategory":"payroll"},
          {"id":"savings","kind":"bank","name":"Αποταμίευση","bankAccountCategory":"savings"},
          {"id":"credit","kind":"credit","name":"Πιστωτική"}],
        "snapshots":[{"date":"2026-10-15",
          "balances":{"payroll":150,"savings":80,"credit":-400}}],
        "transactions":[]},"state":{"settings":{},"events":[
          {"id":"income","kind":"income","date":"2026-10-20","amount":100,
           "note":"Μισθός","legs":[{"accountId":"payroll","amount":100}]}],
        "scheduled":[
          {"id":"pay","status":"pending","kind":"expense","dueDate":"2026-10-16",
           "amount":120,"note":"Ενοίκιο","accountId":"payroll"},
          {"id":"transfer","status":"pending","kind":"transfer","dueDate":"2026-10-17",
           "amount":50,"note":"Εσωτερική μεταφορά",
           "fromAccountId":"payroll","toAccountId":"savings"}]}}
    """.trimIndent()).jsonObject)

    @Test fun exactDailyPointsAccountsAndSourceRowsMatchUnmodifiedWebForecast() {
        val state=projectCanonicalPlanState(document,asOf,null)
        assertEquals(listOf(30,60,90),state.forecastWindows.map { it.days })
        val thirty=state.forecastWindows.first()
        val pure=document.cashFlowForecast(asOf,30)
        assertEquals(pure.currentPortfolio,thirty.currentPortfolio,0.001)
        assertEquals(pure.projectedPortfolio,thirty.projectedPortfolio,0.001)
        assertEquals(pure.minimumPortfolio,thirty.minimumPortfolio,0.001)
        assertEquals(pure.minimumPortfolioDate.toString(),thirty.minimumPortfolioDateIso)
        assertEquals(pure.points.map { it.date.toString() },thirty.portfolioTimeline.map { it.dateIso })
        assertEquals(pure.points.map { it.portfolio },thirty.portfolioTimeline.map { it.portfolio })
        assertEquals(230.0,thirty.currentPortfolio,0.001)
        assertEquals(210.0,thirty.projectedPortfolio,0.001)
        assertEquals(listOf("2026-10-15","2026-10-16","2026-10-17",
            "2026-10-20","2026-11-14"),thirty.portfolioTimeline.map { it.dateIso })
        val payroll=thirty.accountBreakdown.first { it.accountId=="payroll" }
        assertEquals("Μισθοδοσία",payroll.accountLabel)
        assertEquals(-20.0,payroll.minimum,0.001)
        assertEquals("2026-10-16",payroll.firstLowDateIso)
        assertEquals("2026-10-17",payroll.firstNegativeDateIso)
        assertEquals("2026-10-15",
            thirty.accountBreakdown.first { it.accountId=="savings" }.firstLowDateIso)
        assertFalse(thirty.accountBreakdown.any { it.accountId=="credit" })
        assertEquals(listOf("forecast-scheduled-pay","forecast-scheduled-transfer",
            "forecast-event-income"),thirty.knownMovements.map { it.id })
        assertEquals(listOf(-120.0,0.0,100.0),
            thirty.knownMovements.map { it.portfolioDelta })
        assertEquals(mapOf("scheduled" to 2,"recurring" to 0,
            "loan" to 0,"dated_event" to 1),thirty.sourceCounts)
        assertFalse(thirty.insufficientData)
        for (window in state.forecastWindows) {
            val raw=document.cashFlowForecast(asOf,window.days)
            assertEquals(raw.points.size,window.portfolioTimeline.size)
            assertEquals(raw.movements.map { it.id },window.knownMovements.map { it.id })
        }
    }

    @Test fun noKnownFlowsPreservePortfolioWithoutInventingFutureMoney() {
        val empty=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[{"id":"bank","kind":"bank","name":"Μισθοδοσία"}],
             "snapshots":[{"date":"2026-10-15","balances":{"bank":150}}]},
           "state":{"events":[],"scheduled":[],"settings":{}}}
        """.trimIndent()).jsonObject)
        val state=projectCanonicalPlanState(empty,asOf,null)
        val thirty=state.forecastWindows.first()
        assertTrue(thirty.insufficientData)
        assertEquals(0,thirty.knownMovements.size)
        assertEquals(150.0,thirty.projectedPortfolio,0.001)
        assertEquals(listOf("bank"),thirty.accountBreakdown.map { it.accountId })
        assertEquals(empty.cashFlowForecast(asOf,30).points.map { it.date.toString() },
            thirty.portfolioTimeline.map { it.dateIso })
        assertTrue(state.forecastWindows.last().knownMovements.isEmpty())
    }
}
