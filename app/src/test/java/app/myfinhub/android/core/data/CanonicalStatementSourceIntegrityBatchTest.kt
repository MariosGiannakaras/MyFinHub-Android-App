package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Read-only real-source statement integrity. Valid Web-compatible records remain unchanged. */
class CanonicalStatementSourceIntegrityBatchTest {
    private fun document(statements: String, events: String) =
        CanonicalFinanceDocument(Json.parseToJsonElement("""
            {"seed":{"accounts":[],"snapshots":[]},"state":{
              "creditStatements":$statements,"events":$events
            }}
        """.trimIndent()).jsonObject)

    private fun statement(id: String, cardId: String, dueDate: String = "2026-11-05") =
        """{"id":"$id","cardId":"$cardId","openDate":"2026-09-26",
             "closeDate":"2026-10-25","dueDate":"$dueDate",
             "boundaryRule":"next-cycle"}"""

    @Test fun collidingStatementIdsCannotBorrowAnotherCardsMoneyOrBecomePayable() {
        val doc = document(
            "[${statement("shared", "card-A")},${statement("shared", "card-B")}]",
            """[
              {"id":"pa","kind":"card_purchase","cardId":"card-A","statementId":"shared",
               "amount":40,"date":"2026-10-10"},
              {"id":"pb","kind":"card_purchase","cardId":"card-B","statementId":"shared",
               "amount":900,"date":"2026-10-11"}
            ]""",
        )
        assertEquals(2, doc.canonicalStatementSourceIdCounts()["shared"])
        for (card in listOf("card-A", "card-B")) {
            val view = doc.creditStatementViews(card, "2026-11-06").single()
            assertEquals("invalid", view.status)
            assertFalse(view.sourceIntegrityValid)
            assertTrue(view.purchaseTotal.isNaN())
            assertTrue(view.paymentTotal.isNaN())
            assertTrue(view.remaining.isNaN())
            assertNull(doc.recommendedPayableStatement(card, "2026-11-06"))
        }
    }

    @Test fun explicitWrongCardEventNeverInflatesOrSettlesAnotherCardsStatement() {
        val doc = document(
            "[${statement("unique", "card-A")}]",
            """[
              {"id":"owned","kind":"card_purchase","cardId":"card-A","statementId":"unique",
               "amount":40,"date":"2026-10-10"},
              {"id":"foreign","kind":"card_payment","cardId":"card-B","statementId":"unique",
               "amount":900,"date":"2026-10-11"}
            ]""",
        )
        val view = doc.creditStatementViews("card-A", "2026-11-06").single()
        assertEquals(listOf("owned"), view.purchaseIds)
        assertTrue(view.paymentIds.isEmpty())
        assertFalse(view.sourceIntegrityValid)
        assertEquals("invalid", view.status)
        assertTrue(view.remaining.isNaN())
        assertNull(doc.recommendedPayableStatement("card-A", "2026-11-06"))
    }

    @Test fun historicalMissingCardIdCanStillUseItsUniqueExplicitStatement() {
        val doc = document(
            "[${statement("legacy", "old-card")}]",
            """[
              {"id":"purchase-legacy","kind":"card_purchase","statementId":"legacy",
               "amount":55,"date":"2026-10-10"},
              {"id":"pay-legacy","kind":"card_payment","statementId":"legacy",
               "amount":5,"date":"2026-10-11"}
            ]""",
        )
        val view = doc.creditStatementViews("old-card", "2026-11-06").single()
        assertTrue(view.sourceIntegrityValid)
        assertEquals("due", view.status)
        assertEquals(listOf("purchase-legacy"), view.purchaseIds)
        assertEquals(listOf("pay-legacy"), view.paymentIds)
        assertEquals(50.0, view.remaining, 0.001)
        assertEquals("legacy", doc.recommendedPayableStatement("old-card", "2026-11-06")?.id)
    }

    @Test fun invalidImportedMoneyCannotBePresentedAsPaidOrOfferedForPayment() {
        val amount = document(
            "[${statement("bad-number", "card-A")}]",
            """[{"id":"bad","kind":"card_purchase","cardId":"card-A",
                 "statementId":"bad-number","amount":1e309,"date":"2026-10-10"}]""",
        )
        val view = amount.creditStatementViews("card-A", "2026-11-06").single()
        assertFalse(view.sourceIntegrityValid)
        assertEquals("invalid", view.status)
        assertTrue(view.remaining.isNaN())
        assertNull(amount.recommendedPayableStatement("card-A", "2026-11-06"))

        val negative = document(
            "[${statement("bad-repayment", "card-A")}]",
            """[
              {"id":"bought","kind":"card_purchase","cardId":"card-A",
               "statementId":"bad-repayment","amount":20,"date":"2026-10-10"},
              {"id":"bad-pay","kind":"card_payment","cardId":"card-A",
               "statementId":"bad-repayment","amount":-100,"date":"2026-10-11"}
            ]""",
        )
        assertFalse(negative.creditStatementViews("card-A", "2026-11-06").single().sourceIntegrityValid)
        assertNull(negative.recommendedPayableStatement("card-A", "2026-11-06"))
    }

    @Test fun impossibleStatementDateInvalidatesOnlyAffectedCycle() {
        val doc = document(
            "[${statement("bad-date", "card-A", "2026-02-30")},${statement("safe", "card-A")}]",
            """[
              {"id":"bad-purchase","kind":"card_purchase","cardId":"card-A",
               "statementId":"bad-date","amount":50,"date":"2026-10-12"},
              {"id":"safe-purchase","kind":"card_purchase","cardId":"card-A",
               "statementId":"safe","amount":20,"date":"2026-10-13"}
            ]""",
        )
        val views = doc.creditStatementViews("card-A", "2026-11-06").associateBy { it.id }
        assertEquals("invalid", views.getValue("bad-date").status)
        assertFalse(views.getValue("bad-date").sourceIntegrityValid)
        assertTrue(views.getValue("safe").sourceIntegrityValid)
        assertEquals(20.0, views.getValue("safe").remaining, 0.001)
        assertEquals("safe", doc.recommendedPayableStatement("card-A", "2026-11-06")?.id)
    }
}
