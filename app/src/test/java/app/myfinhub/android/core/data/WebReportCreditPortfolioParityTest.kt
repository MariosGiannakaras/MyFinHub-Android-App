package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web reportCreditCardSnapshots: archived balances remain liabilities. */
class WebReportCreditPortfolioParityTest {
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
    {"seed":{},"state":{"settings":{"creditLimit":800},"cards":[
      {"id":"a","kind":"credit","nickname":"Άλφα","active":true,
       "creditLimit":1000,"bankId":"alpha","createdAt":"2026-01-01"},
      {"id":"b","kind":"credit","nickname":"Βήτα","active":false,
       "creditLimit":500,"bankId":"beta","createdAt":"2026-02-01"},
      {"id":"c","kind":"credit","nickname":"Γάμμα","active":true,
       "bankId":"gamma","createdAt":"2026-03-01"},
      {"id":"d","kind":"debit","nickname":"Χρεωστική","active":true}
    ],"events":[
      {"id":"ea1","date":"2026-10-01","kind":"card_purchase","cardId":"a",
       "amount":400,"creditDelta":-400},
      {"id":"ea2","date":"2026-10-02","kind":"card_payment","cardId":"a",
       "amount":100,"creditDelta":100},
      {"id":"eb1","date":"2026-09-10","kind":"card_purchase","cardId":"b",
       "amount":200,"creditDelta":-200},
      {"id":"ec1","date":"2026-10-04","kind":"card_purchase","cardId":"c",
       "amount":900,"creditDelta":-900},
      {"id":"future","date":"2026-10-25","kind":"card_purchase","cardId":"a",
       "amount":750,"creditDelta":-750}
    ]}}
    """.trimIndent()).jsonObject)

    @Test fun archivedCardsRemainInLiabilityButDoNotContributeLimits() {
        val before = doc.raw
        val report = doc.canonicalReportCreditPortfolio("2026-10-20")
        assertEquals(3, report.totalCards)
        assertEquals(2, report.activeCards)
        assertEquals(listOf("c","a","b"),report.cards.map { it.id })
        assertEquals(listOf(900.0,300.0,200.0),report.cards.map { it.debt })
        assertEquals(1400.0,report.debt,0.001)
        assertEquals(1800.0,report.limit,0.001)
        assertEquals(400.0,report.available,0.001)
        assertEquals(1400.0/1800.0,report.usage,0.001)
        assertEquals(0.0,report.cards.last().limit,0.001)
        assertEquals(0.0,report.cards.last().available,0.001)
        assertEquals(900.0/800.0,report.cards.first().usage,0.001)
        assertEquals(before,doc.raw)
    }

    @Test fun noCardsMeansZeroPortfolioWithoutManufacturedCredit() {
        val empty=CanonicalFinanceDocument(Json.parseToJsonElement(
            """{"seed":{},"state":{"settings":{},"cards":[]}}"""
        ).jsonObject)
        val report=empty.canonicalReportCreditPortfolio("2026-10-20")
        assertEquals(0,report.totalCards)
        assertEquals(0.0,report.debt,0.001)
        assertEquals(0.0,report.usage,0.001)
        assertTrue(report.cards.isEmpty())
    }
}
