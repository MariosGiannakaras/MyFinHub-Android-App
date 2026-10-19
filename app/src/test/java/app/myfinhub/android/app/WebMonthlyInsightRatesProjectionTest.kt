package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_MONTH
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_30_DAYS
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Web reports.ts reportInsightModel rates use 3 full prior calendar months. */
class WebMonthlyInsightRatesProjectionTest {
    private val document=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[],"transactions":[
        {"id":"aug-out","type":"expense","date":"2026-08-12","amount":100},
        {"id":"sep-income","type":"income","date":"2026-09-04","amount":900},
        {"id":"sep-out","type":"expense","date":"2026-09-11","amount":200},
        {"id":"oct-income","type":"income","date":"2026-10-07","amount":1000},
        {"id":"oct-out","type":"expense","date":"2026-10-14","amount":300},
        {"id":"oct-future","type":"expense","date":"2026-10-26","amount":900}
      ]},"state":{"events":[
        {"id":"sep-save","kind":"saving_cash_offset",
         "date":"2026-09-09","amount":45,"savingAmount":45},
        {"id":"oct-save","kind":"saving_cash_offset",
         "date":"2026-10-09","amount":100,"savingAmount":100}
      ],"settings":{}}}
    """.trimIndent()).jsonObject)

    @Test fun currentSavingsRateAndFullPriorThreeMonthMeanMatchWeb() {
        val before=document.raw
        val view=projectCanonicalInsightsState(document,LocalDate.parse("2026-10-19"))
        val rates=view.periodScope(INSIGHTS_PERIOD_MONTH).monthlyRates!!
        assertEquals(0.1,rates.savingsRate!!,0.0001)
        assertEquals(0.05,rates.previousSavingsRate!!,0.0001)
        assertEquals(100.0,rates.trailingExpenseAverage,0.001)
        assertEquals(2.0,rates.expenseVsTrailingAverage!!,0.0001)
        assertEquals((100.0-45.0)/45.0,rates.savingChange!!,0.0001)
        assertNull(view.periodScope(INSIGHTS_PERIOD_30_DAYS).monthlyRates)
        assertEquals(before,document.raw)
    }

    @Test fun zeroIncomeGivesUnavailableSavingsRateNotSyntheticZero() {
        val zero=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"transactions":[]},"state":{"events":[],"settings":{}}}
        """.trimIndent()).jsonObject)
        val rates=projectCanonicalInsightsState(zero,LocalDate.parse("2026-10-19"))
            .periodScope(INSIGHTS_PERIOD_MONTH).monthlyRates!!
        assertNull(rates.savingsRate)
        assertNull(rates.previousSavingsRate)
        assertNull(rates.expenseVsTrailingAverage)
        assertEquals(0.0,rates.trailingExpenseAverage,0.001)
        assertEquals(0.0,rates.savingChange!!,0.001)
    }
}
