package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.canonicalReportCategoryMomentum
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_30_DAYS
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_90_DAYS
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_MONTH
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebCategoryMomentumInsightsProjectionTest {
    @Test fun monthlyInsightsIncludeAllTwelveWebReportCategoriesNotDefaultTen() {
        // Web ReportsPage requests categoryMomentum(..., 100, asOf).
        // Verify it does not silently use the selector's default limit=10.
        val events = (1..12).joinToString(",") { index ->
            """{"id":"expense-$index","kind":"expense","date":"2026-10-12",
                "amount":$index,"category":"Category-$index"}"""
        }
        val doc = CanonicalFinanceDocument(Json.parseToJsonElement(
            """{"seed":{"transactions":[]},"state":{"settings":{},"events":[$events]}}"""
        ).jsonObject)
        val original = doc.raw
        val date = LocalDate.parse("2026-10-19")
        val expected = doc.canonicalReportCategoryMomentum("2026-10", limit=100,
            asOf=date.toString())
        assertEquals(12, expected.size)
        val actual = projectCanonicalInsightsState(doc, date)
            .periodScope(INSIGHTS_PERIOD_MONTH).categoryMomentum
        assertEquals(12, actual.size)
        assertEquals(expected.map { it.name }, actual.map { it.name })
        assertEquals(expected.map { it.value }, actual.map { it.currentAmount })
        assertEquals(78.0, actual.sumOf { it.currentAmount }, 0.00001)
        assertEquals(original, doc.raw)
    }

    @Test fun canonicalWebMonthlyCategoryMomentumDoesNotLeakToSlidingRanges() {
        val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"transactions":[]},"state":{"settings":{},"events":[
            {"id":"prev","kind":"expense","date":"2026-09-10","amount":10,
             "category":"Τρόφιμα"},
            {"id":"now","kind":"expense","date":"2026-10-14","amount":30,
             "category":"Τρόφιμα"},
            {"id":"future","kind":"expense","date":"2026-10-24","amount":500,
             "category":"Υγεία"}
          ]}}
        """.trimIndent()).jsonObject)
        val original=doc.raw
        val date=LocalDate.parse("2026-10-19")
        val expected=doc.canonicalReportCategoryMomentum("2026-10",asOf=date.toString())
        val screen=projectCanonicalInsightsState(doc,date)
        val monthly=screen.periodScope(INSIGHTS_PERIOD_MONTH).categoryMomentum
        assertEquals(expected.map { it.name },monthly.map { it.name })
        assertEquals(expected.map { it.value },monthly.map { it.currentAmount })
        assertEquals(expected.map { it.previous },monthly.map { it.previousAmount })
        assertEquals(expected.map { it.change },monthly.map { it.relativeChange })
        assertTrue(screen.periodScope(INSIGHTS_PERIOD_30_DAYS).categoryMomentum.isEmpty())
        assertTrue(screen.periodScope(INSIGHTS_PERIOD_90_DAYS).categoryMomentum.isEmpty())
        assertEquals(original,doc.raw)
    }
}
