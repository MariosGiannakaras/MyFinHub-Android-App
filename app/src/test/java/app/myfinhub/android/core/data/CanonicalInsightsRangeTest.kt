package app.myfinhub.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class CanonicalInsightsRangeTest {
    @Test
    fun contributionsIncludeOnlyActualCategoryPartsAndRefunds() {
        val split = CanonicalFinanceDocument(Json.parseToJsonElement("""
            {"seed":{},"state":{"events":[
              {"id":"split","kind":"split","date":"2026-09-01","amount":100,"parts":[
                {"id":"food","category":"Τρόφιμα","amount":20},
                {"id":"travel","category":"Μεταφορές","amount":80}]},
              {"id":"refund","kind":"refund","date":"2026-09-10","amount":5,"category":"Τρόφιμα"},
              {"id":"payment","kind":"card_payment","date":"2026-09-10","amount":100,"category":"Τρόφιμα"}
            ]}}
        """.trimIndent()).jsonObject)
        val food = split.categoryContributionsBetween("2026-09-01", "2026-09-10").filter { it.category == "Τρόφιμα" }
        assertEquals(listOf("split", "refund"), food.map { it.transactionId })
        assertEquals(listOf(20.0, -5.0), food.map { it.amount })
        assertEquals(15.0, split.categoryTotalsBetween("2026-09-01", "2026-09-10").getValue("Τρόφιμα"), 0.001)
    }

    private val document = canonicalFixture()

    @Test
    fun flowBetween_usesInclusiveRangeWithExistingCanonicalSemantics() {
        val firstTenDays = document.flowBetween("2026-08-01", "2026-08-10")
        val throughFifteenth = document.flowBetween("2026-08-01", "2026-08-15")

        assertEquals(0.0, firstTenDays.income, 0.001)
        assertEquals(120.0, firstTenDays.expense, 0.001)
        assertEquals(200.0, throughFifteenth.income, 0.001)
        assertEquals(150.0, throughFifteenth.expense, 0.001)
    }

    @Test
    fun categoryTotalsBetween_respectsRangeAndRefundSemantics() {
        val firstTenDays = document.categoryTotalsBetween("2026-08-01", "2026-08-10")
        val throughEighteenth = document.categoryTotalsBetween("2026-08-01", "2026-08-18")

        assertEquals(120.0, firstTenDays.getValue("Τρόφιμα"), 0.001)
        assertEquals(145.0, throughEighteenth.getValue("Τρόφιμα"), 0.001)
        assertEquals(10.0, throughEighteenth.getValue("Έξοδος"), 0.001)
    }

    @Test
    fun reversedRange_isEmptyInsteadOfLeakingAnotherPeriod() {
        val flow = document.flowBetween("2026-08-10", "2026-08-01")
        val categories = document.categoryTotalsBetween("2026-08-10", "2026-08-01")

        assertEquals(0.0, flow.income, 0.001)
        assertEquals(0.0, flow.expense, 0.001)
        assertTrue(categories.isEmpty())
    }
}
