package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WebSelectedCreditPaymentParityTest {
    private val now = "2026-09-03T12:00:00Z"
    private val fixture = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[{"id":"piraeus-main","kind":"bank","name":"Πειραιώς"}],"snapshots":[]},
       "state":{"cards":[
           {"id":"c1","kind":"credit","bankId":"piraeus","active":true},
           {"id":"c2","kind":"credit","bankId":"piraeus","active":true}
         ],
         "creditStatements":[
           {"id":"s1","cardId":"c1","openDate":"2026-08-26","closeDate":"2026-09-25","dueDate":"2026-10-05","boundaryRule":"next-cycle"},
           {"id":"s2","cardId":"c1","openDate":"2026-09-26","closeDate":"2026-10-25","dueDate":"2026-11-05","boundaryRule":"next-cycle"},
           {"id":"other","cardId":"c2","openDate":"2026-08-26","closeDate":"2026-09-25","dueDate":"2026-10-05","boundaryRule":"next-cycle"}
         ],
         "events":[
           {"id":"purchase-1","kind":"card_purchase","date":"2026-09-01","cardId":"c1","statementId":"s1","amount":20},
           {"id":"purchase-2","kind":"card_purchase","date":"2026-09-02","cardId":"c1","statementId":"s2","amount":100}
         ]}}
    """.trimIndent()).jsonObject)

    private fun paymentMutation(document: CanonicalFinanceDocument, id: String, amount: Double, statementId: String?) =
        createCanonicalTransactionEntryMutation(
            document, CanonicalTransactionEntryDraft(
                kind = "card_payment", date = "2026-09-03", amount = amount,
                fromAccountId = "piraeus-main", cardId = "c1", statementId = statementId,
            ), id, now,
        )

    private fun pay(document: CanonicalFinanceDocument, id: String, amount: Double, statementId: String?) =
        paymentMutation(document, id, amount, statementId).apply(document)

    @Test fun explicitStatementLinkPersistsAndCapsRemaining() {
        val mutation = paymentMutation(fixture, "paid-1", 15.0, "s1")
        val result = mutation.apply(fixture)
        val payment = result.state.array("events").last() as JsonObject
        assertEquals("s1", payment.string("statementId"))
        assertEquals(5.0, result.creditStatementViews("c1", "2026-09-03")
            .first { it.id == "s1" }.remaining, 0.001)
        assertEquals(result.raw, mutation.apply(result).raw)
    }

    @Test fun noUserChoiceNeverAllocatesStatement() {
        val result = pay(fixture, "unlinked", 30.0, null)
        val payment = result.state.array("events").last() as JsonObject
        assertFalse(payment.containsKey("statementId"))
        assertEquals(90.0, result.creditDebtForCardAt("c1", "2026-09-03"), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotOverpaySelectedStatementDespiteOtherOutstandingDebt() {
        pay(fixture, "overpay", 25.0, "s1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotLinkAnotherCardsStatement() {
        pay(fixture, "cross-card", 10.0, "other")
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotReopenAlreadyPaidStatement() {
        val settled = pay(fixture, "paid-all", 20.0, "s1")
        pay(settled, "again", 1.0, "s1")
    }
}
