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
}
