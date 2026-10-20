package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.canonicalOperationalReportSnapshot
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_30_DAYS
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_90_DAYS
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_MONTH
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebOperationalReportInsightsProjectionTest {
    @Test fun monthProjectionMapsCanonicalReportWithoutChangingLedger() {
        val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
        {"seed":{"accounts":[{"id":"cash","name":"Μετρητά","kind":"cash"}],
          "snapshots":[{"date":"2026-10-01","balances":{"cash":300}}],
          "transactions":[]},"state":{"settings":{"monthlyBudget":350},
          "events":[{"id":"spent","date":"2026-10-10","kind":"expense",
                     "amount":45,"category":"Έξοδος"}]}}
        """.trimIndent()).jsonObject)
        val original=doc.raw
        val today=LocalDate.parse("2026-10-20")
        val expected=doc.canonicalOperationalReportSnapshot("2026-10",today.toString())
        val screen=projectCanonicalInsightsState(doc,today)
        val monthly=screen.periodScope(INSIGHTS_PERIOD_MONTH).operationalReport!!
        assertEquals(expected.flow.income,monthly.income,0.001)
        assertEquals(expected.flow.expense,monthly.expense,0.001)
        assertEquals(expected.previous.expense,monthly.previousExpense,0.001)
        assertEquals(expected.creditAvailable,monthly.creditAvailable,0.001)
        assertEquals(expected.receivables,monthly.receivables,0.001)
        assertEquals(expected.budgetRemaining,monthly.budgetRemaining,0.001)
        assertEquals(listOf("cash"),monthly.accountBalances.map { it.id })
        assertEquals(300.0,monthly.accountBalances.single().balance,0.001)
        assertNull(screen.periodScope(INSIGHTS_PERIOD_30_DAYS).operationalReport)
        assertNull(screen.periodScope(INSIGHTS_PERIOD_90_DAYS).operationalReport)
        assertEquals(original,doc.raw)
    }
}
