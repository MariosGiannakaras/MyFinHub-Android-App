package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web cards.ts / reports.ts active limits, archived debt and dated credit flows. */
class WebReportCreditPortfolioParityTest {
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
    {"seed":{},"state":{"settings":{"creditLimit":900},"cards":[
      {"id":"one","kind":"credit","nickname":"Ζήτα","bankId":"piraeus",
       "creditLimit":700,"active":true,"createdAt":"2026-01-01T00:00:00Z"},
      {"id":"two","kind":"credit","nickname":"Άλφα","bankId":"alpha",
       "active":true,"createdAt":"2026-02-01T00:00:00Z"},
      {"id":"old","kind":"credit","nickname":"Παλιό","bankId":"piraeus",
       "active":false,"creditLimit":500,"createdAt":"2026-03-01T00:00:00Z"}
    ],"events":[
      {"id":"p1","kind":"card_purchase","cardId":"one","amount":400,"date":"2026-10-04"},
      {"id":"p2","kind":"card_payment","cardId":"one","amount":100,"date":"2026-10-09"},
      {"id":"p3","kind":"card_purchase","cardId":"two","amount":250,"date":"2026-10-11"},
      {"id":"p4","kind":"card_purchase","cardId":"old","amount":150,"date":"2026-10-05"},
      {"id":"f","kind":"card_purchase","cardId":"two","amount":800,"date":"2026-10-26"}
    ]}}
    """.trimIndent()).jsonObject)

    @Test fun archivedDebtCountsButArchivedLimitDoesNotAndFuturePurchasesAreExcluded() {
        val original=doc.raw
        val report=doc.canonicalReportCreditPortfolio("2026-10-19")
        assertEquals(2,report.activeCards)
        assertEquals(3,report.totalCards)
        assertEquals(700.0,report.debt,0.001)
        assertEquals(1600.0,report.limit,0.001)
        assertEquals(900.0,report.available,0.001)
        assertEquals(700.0/1600.0,report.usage,0.0001)
        assertEquals(listOf("one","two","old"),report.cards.map { it.id })
        assertEquals(0.0,report.cards.last().limit,0.001)
        assertEquals(0.0,report.cards.last().available,0.001)
        assertEquals(900.0,report.cards[1].limit,0.001)
        assertEquals(original,doc.raw)
    }

    @Test fun noCardsOrOnlyArchivedDoNotFakeAvailableCredit() {
        val none=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{},"state":{"settings":{"creditLimit":500},"cards":[],"events":[]}}
        """.trimIndent()).jsonObject).canonicalReportCreditPortfolio("2026-10-19")
        assertEquals(0,none.totalCards)
        assertEquals(0.0,none.available,0.001)
        val old=doc.canonicalReportCreditPortfolio("2026-10-06")
        assertTrue(old.cards.any { it.id=="old" && it.debt>0.0 && it.limit==0.0 })
    }
}
