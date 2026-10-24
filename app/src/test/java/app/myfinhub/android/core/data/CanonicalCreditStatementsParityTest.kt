package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanonicalCreditStatementsParityTest {
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[],"snapshots":[]},"state":{
        "creditStatements":[
          {"id":"s-paid","cardId":"card1","openDate":"2026-08-01","closeDate":"2026-08-25","dueDate":"2026-09-05","boundaryRule":"include-closing-day"},
          {"id":"s-open","cardId":"card1","openDate":"2026-09-26","closeDate":"2026-10-25","dueDate":"2026-11-05","boundaryRule":"next-cycle"},
          {"id":"s-closed","cardId":"card1","openDate":"2026-08-26","closeDate":"2026-09-25","dueDate":"2026-10-20","boundaryRule":"next-cycle"},
          {"id":"other-card","cardId":"card2","openDate":"2026-08-26","closeDate":"2026-09-25","dueDate":"2026-10-20","boundaryRule":"next-cycle"}
        ],
        "events":[
          {"id":"p1","date":"2026-09-07","kind":"card_purchase","cardId":"card1","statementId":"s-closed","amount":100,"note":"A","legs":[]},
          {"id":"p2","date":"2026-09-08","kind":"card_purchase","cardId":"card1","statementId":"s-closed","amount":35.5,"note":"B","legs":[]},
          {"id":"r1","date":"2026-10-10","kind":"card_payment","cardId":"card1","statementId":"s-closed","amount":40,"note":"Partial repayment","legs":[]},
          {"id":"r2","date":"2026-10-23","kind":"card_payment","cardId":"card1","statementId":"s-closed","amount":95.5,"note":"Later repayment","legs":[]},
          {"id":"p3","date":"2026-10-10","kind":"card_purchase","cardId":"card1","statementId":"s-open","amount":30,"note":"New","legs":[]},
          {"id":"legacy","date":"2026-10-09","kind":"card_purchase","cardId":"card1","amount":12,"note":"Old","legs":[]}
        ]
      }}
    """.trimIndent()).jsonObject)

    @Test fun remainingStatusAndAsOfExcludeFutureRepayment() {
        val views = doc.creditStatementViews("card1","2026-10-14")
        assertEquals(listOf("s-open","s-closed","s-paid"),views.map { it.id })
        assertEquals("open",views[0].status)
        assertEquals(30.0,views[0].remaining,0.001)
        assertEquals("closed",views[1].status)
        assertEquals(135.5,views[1].purchaseTotal,0.001)
        assertEquals(40.0,views[1].paymentTotal,0.001)
        assertEquals(95.5,views[1].remaining,0.001)
        assertEquals(listOf("p1","p2"),views[1].purchaseIds)
        assertEquals(listOf("r1"),views[1].paymentIds)
        assertEquals("paid",views[2].status)
        assertEquals("s-closed",doc.recommendedPayableStatement("card1","2026-10-14")?.id)
    }

    @Test fun dueThenPaidAndLinkageNeverSilentlyDisappears() {
        assertEquals("due",doc.creditStatementViews("card1","2026-10-20")[1].status)
        assertEquals("paid",doc.creditStatementViews("card1","2026-10-23")[1].status)
        assertEquals("s-open",doc.recommendedPayableStatement("card1","2026-10-23")?.id)
        assertEquals(listOf("legacy"),doc.unlinkedCreditStatementEventIds("card1"))
        assertEquals(emptyList<String>(),doc.unlinkedCreditStatementEventIds("card2"))
        assertNull(doc.recommendedPayableStatement("non-existent","2026-10-14"))
    }
    @Test fun webHistoricalBlankOrNullStatementLinksRemainVisibleForManualReconciliation() {
        val historical = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[]},"state":{"events":[
            {"id":"blank","kind":"card_purchase","date":"2026-10-01","cardId":"card1","statementId":"","amount":20},
            {"id":"missing","kind":"card_payment","date":"2026-10-02","cardId":"card1","amount":5},
            {"id":"null","kind":"card_purchase","date":"2026-10-03","cardId":"card1","statementId":null,"amount":3},
            {"id":"linked","kind":"card_purchase","date":"2026-10-04","cardId":"card1","statementId":"known","amount":10},
            {"id":"whitespace","kind":"card_payment","date":"2026-10-05","cardId":"card1","statementId":" ","amount":2},
            {"id":"different-card","kind":"card_purchase","date":"2026-10-06","cardId":"card2","amount":8},
            {"id":"unrelated","kind":"expense","date":"2026-10-07","cardId":"card1","amount":2}
          ]}}
        """.trimIndent()).jsonObject)
        // JS !event.statementId is true for missing/null/empty, but not a nonempty string.
        assertEquals(listOf("blank", "missing", "null"),
            historical.unlinkedCreditStatementEventIds("card1"))
        assertEquals(listOf("different-card"),
            historical.unlinkedCreditStatementEventIds("card2"))
    }

    @Test fun webStatementMoneyRoundingUsesHalfUpRatherThanKotlinTiesToEven() {
        val historical = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[]},"state":{
            "creditStatements":[
              {"id":"legacy-fraction","cardId":"card1","openDate":"2026-09-01",
                "closeDate":"2026-10-25","dueDate":"2026-11-05",
                "boundaryRule":"next-cycle"}
            ],
            "events":[
              {"id":"purchase","kind":"card_purchase","date":"2026-10-02",
                "cardId":"card1","statementId":"legacy-fraction","amount":1.125},
              {"id":"payment","kind":"card_payment","date":"2026-10-03",
                "cardId":"card1","statementId":"legacy-fraction","amount":0.125},
              {"id":"future-payment","kind":"card_payment","date":"2026-10-25",
                "cardId":"card1","statementId":"legacy-fraction","amount":1}
            ]
          }}
        """.trimIndent()).jsonObject)
        // Historical imported sub-cent values can be read without changing canonical write rules.
        // Web roundMoney(1.125) = 1.13, roundMoney(0.125) = 0.13.
        val beforeFuturePayment = historical.creditStatementViews("card1", "2026-10-14").single()
        assertEquals(1.13, beforeFuturePayment.purchaseTotal, 0.0000001)
        assertEquals(0.13, beforeFuturePayment.paymentTotal, 0.0000001)
        assertEquals(1.0, beforeFuturePayment.remaining, 0.0000001)
        assertEquals(listOf("payment"), beforeFuturePayment.paymentIds)
        assertEquals("legacy-fraction",
            historical.recommendedPayableStatement("card1", "2026-10-14")?.id)
        val afterPayment = historical.creditStatementViews("card1", "2026-10-26").single()
        assertEquals(listOf("payment", "future-payment"), afterPayment.paymentIds)
        assertEquals(0.0, afterPayment.remaining, 0.0000001)
        assertEquals("paid", afterPayment.status)
    }

    @Test fun webMixedFundingExpenseContributesOnlyCreditShareToStatement() {
        val mixed = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[]},"state":{
            "creditStatements":[{"id":"s1","cardId":"visa","openDate":"2026-09-26",
              "closeDate":"2026-10-25","dueDate":"2026-11-05","boundaryRule":"next-cycle"}],
            "events":[
              {"id":"mixed","kind":"expense","cardId":"visa","date":"2026-10-12",
               "statementId":"s1","amount":130,"funding":[{"kind":"cash","amount":90},{"kind":"credit","amount":40}],
               "creditDelta":-40,"createdAt":"2026-10-12T10:00:00Z"},
              {"id":"plain","kind":"expense","cardId":"visa","date":"2026-10-13",
               "statementId":"s1","amount":20,"creditDelta":-20},
              {"id":"card","kind":"card_purchase","cardId":"visa","date":"2026-10-14",
               "statementId":"s1","amount":10},
              {"id":"payment","kind":"card_payment","cardId":"visa","date":"2026-10-15",
               "statementId":"s1","amount":25},
              {"id":"unlinked-mixed","kind":"expense","cardId":"visa","date":"2026-10-16",
               "amount":60,"funding":[{"kind":"credit","amount":15}],"creditDelta":-15},
              {"id":"unlinked-plain","kind":"expense","cardId":"visa","date":"2026-10-17",
               "amount":20}
            ]
          }}
        """.trimIndent()).jsonObject)
        val view = mixed.creditStatementViews("visa", "2026-10-20").single()
        assertEquals(listOf("mixed", "card"), view.purchaseIds)
        assertEquals(50.0, view.purchaseTotal, 0.00001)
        assertEquals(25.0, view.paymentTotal, 0.00001)
        assertEquals(25.0, view.remaining, 0.00001)
        assertEquals(listOf("unlinked-mixed"), mixed.unlinkedCreditStatementEventIds("visa"))
    }

}
