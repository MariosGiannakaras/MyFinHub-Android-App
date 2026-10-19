package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.canonicalReportLoanBurden
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_MONTH
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_30_DAYS
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebReportLoanBurdenInsightsProjectionTest {
    @Test fun monthlyReportBurdenMatchesSourceAndIsReadOnly() {
        val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"loans":[{"id":"loan","name":"Loan",
            "total":1200,"installments":12,"installment":100,
            "day":"15"}]},"state":{"settings":{},"events":[]}}
        """.trimIndent()).jsonObject)
        val before=doc.raw
        val date=LocalDate.parse("2026-10-19")
        val src=doc.canonicalReportLoanBurden(date.toString())
        val state=projectCanonicalInsightsState(doc,date)
        val monthly=state.periodScope(INSIGHTS_PERIOD_MONTH).loanBurden!!
        assertEquals(src.total,monthly.total,0.001)
        assertEquals(src.count,monthly.count)
        assertEquals(src.rows.map { it.id },monthly.rows.map { it.id })
        assertNull(state.periodScope(INSIGHTS_PERIOD_30_DAYS).loanBurden)
        assertEquals(before,doc.raw)
    }
}
