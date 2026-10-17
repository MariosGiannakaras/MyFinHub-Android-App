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

    @Test
    fun refundHeavyRangeKeepsSignedRawNetWhileDisplayingNonnegativeExpense() {
        val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"transactions":[
            {"id":"review","date":"2026-10-02","type":"expense","amount":40,
             "category":"Τρόφιμα"}
          ]},"state":{"reviewDecisions":{
            "review":{"status":"confirmed","semanticKind":"refund","category":""}
          },"events":[
            {"id":"income","kind":"income","date":"2026-10-03","amount":100},
            {"id":"refund","kind":"refund","date":"2026-10-04","amount":90,
             "category":"Τρόφιμα"}
          ]}}
        """.trimIndent()).jsonObject)
        val month = doc.monthlyFlow("2026-10")
        val range = doc.flowBetween("2026-10-01","2026-10-31")
        assertEquals(100.0,range.income,0.001)
        assertEquals(0.0,range.expense,0.001)
        assertEquals(230.0,range.net,0.001)
        assertEquals(130.0,range.refunds,0.001)
        assertEquals(month,range)
        val contributions = doc.categoryContributionsBetween("2026-10-01","2026-10-31")
        assertEquals(listOf("review","refund"),contributions.map { it.transactionId })
        assertEquals(listOf("Τρόφιμα","Τρόφιμα"),contributions.map { it.category })
        assertEquals(listOf(-40.0,-90.0),contributions.map { it.amount })
    }

    @Test
    fun whitespaceCategoryRemainsHistoricalKeyButExplicitEmptyDecisionFallsBack() {
        val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"transactions":[
            {"id":"legacy","date":"2026-10-02","type":"expense","amount":10,
             "category":"Αρχική"}
          ]},"state":{"reviewDecisions":{
            "legacy":{"status":"confirmed","semanticKind":"expense","category":""}
          },"events":[
            {"id":"space","kind":"expense","date":"2026-10-03","amount":5,
             "category":" "}
          ]}}
        """.trimIndent()).jsonObject)
        val rows = doc.categoryContributionsBetween("2026-10-01","2026-10-31")
        assertEquals(listOf("Αρχική"," "),rows.map { it.category })
        assertEquals(10.0,doc.categoryTotalsBetween("2026-10-01","2026-10-31")["Αρχική"]!!,0.001)
        assertEquals(5.0,doc.categoryTotalsBetween("2026-10-01","2026-10-31")[" "]!!,0.001)
    }

    @Test
    fun monthAndDateScopeShareExactWebFlowForExplicitZeroSavingAndReviewedSplit() {
        val data = CanonicalFinanceDocument(Json.parseToJsonElement("""
            {"seed":{"transactions":[
              {"id":"legacy","type":"expense","date":"2026-10-03",
               "amount":40,"category":"Τρόφιμα"}
            ]},"state":{"reviewDecisions":{
                "legacy":{"status":"confirmed","semanticKind":"split","parts":[
                  {"id":"food","kind":"expense","category":"Τρόφιμα","amount":30},
                  {"id":"refund","kind":"refund","category":"Τρόφιμα","amount":10}
                ]}
              },"events":[
                {"id":"payroll","kind":"income","date":"2026-10-04","amount":200},
                {"id":"purchase","kind":"card_purchase","date":"2026-10-05",
                 "amount":20,"category":"Τρόφιμα"},
                {"id":"zero","kind":"saving_cash_offset","date":"2026-10-06",
                 "amount":300,"savingAmount":0},
                {"id":"missing","kind":"saving_cash_offset","date":"2026-10-07","amount":7},
                {"id":"refund-event","kind":"refund","date":"2026-10-08",
                 "amount":5,"category":"Τρόφιμα"},
                {"id":"payment","kind":"card_payment","date":"2026-10-09",
                 "amount":50,"category":"Τρόφιμα"}
              ]}}
        """.trimIndent()).jsonObject)
        val month = data.monthlyFlow("2026-10")
        val range = data.flowBetween("2026-10-01", "2026-10-31")
        assertEquals(month, range)
        assertEquals(200.0, range.income, 0.001)
        assertEquals(35.0, range.expense, 0.001)
        assertEquals(7.0, range.saving, 0.001)
        assertEquals(15.0, range.refunds, 0.001)
        assertEquals(35.0, data.categoryTotalsBetween("2026-10-01", "2026-10-31")["Τρόφιμα"]!!, 0.001)
    }
}
