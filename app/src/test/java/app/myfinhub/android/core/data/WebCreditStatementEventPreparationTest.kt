package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebCreditStatementEventPreparationTest {
    private val now = "2026-10-15T11:45:00Z"
    private val data = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[]},"state":{"cards":[
        {"id":"visa","kind":"credit","active":true,
          "statementClosingDay":25,"statementDueDay":5,"statementBoundaryRule":"next-cycle"},
        {"id":"debit","kind":"debit","active":true}
      ]}}
    """.trimIndent()).jsonObject)

    private fun event(id: String, date: String, kind: String, card: String, link: String? = null): JsonObject =
        JsonObject(buildMap {
            put("id", JsonPrimitive(id))
            put("kind", JsonPrimitive(kind))
            put("date", JsonPrimitive(date))
            put("cardId", JsonPrimitive(card))
            put("amount", JsonPrimitive(25.0))
            put("legs", kotlinx.serialization.json.JsonArray(emptyList()))
            link?.let { put("statementId", JsonPrimitive(it)) }
        })

    private fun append(doc: CanonicalFinanceDocument, e: JsonObject): CanonicalFinanceDocument =
        AppendCanonicalEvent(e, now).apply(doc)

    @Test fun purchasesUseWebNextCycleRuleAndReuseStatementOnReplay() {
        val first = append(data, event("p1","2026-10-24","card_purchase","visa"))
        val expectedId = "visa:2026-10-25"
        assertEquals(expectedId, (first.state.array("events").first() as JsonObject).string("statementId"))
        val record = first.state.array("creditStatements").single() as JsonObject
        assertEquals("2026-09-25", record.string("openDate"))
        assertEquals("2026-10-25", record.string("closeDate"))
        assertEquals("2026-11-05", record.string("dueDate"))
        assertEquals("next-cycle",record.string("boundaryRule"))

        val withClosingDay = append(first, event("p2","2026-10-25","card_purchase","visa"))
        assertEquals(listOf("visa:2026-10-25","visa:2026-11-25"),
            withClosingDay.state.array("creditStatements").map { (it as JsonObject).string("id") })
        val rerun = append(withClosingDay, event("p2","2026-10-25","card_purchase","visa"))
        assertEquals(withClosingDay.raw,rerun.raw)
    }

    @Test fun paymentsWithoutStatementStayUnlinkedAndValidPaymentLinkIsRetained() {
        val cycle = append(data,event("p1","2026-10-24","card_purchase","visa"))
        val unlinked = append(cycle,event("pay1","2026-10-25","card_payment","visa"))
        assertFalse((unlinked.state.array("events").last() as JsonObject).containsKey("statementId"))
        val linked = append(unlinked,event("pay2","2026-10-25","card_payment","visa","visa:2026-10-25"))
        assertEquals("visa:2026-10-25",
            (linked.state.array("events").last() as JsonObject).string("statementId"))
        assertEquals(1,linked.state.array("creditStatements").size)
    }

    @Test fun invalidCrossCardLinksAreStrippedWithoutCreatingFakeFinancialEvents() {
        val cycle = append(data,event("p1","2026-10-24","card_purchase","visa"))
        val bad = append(cycle,event("pay1","2026-10-25","card_payment","debit","visa:2026-10-25"))
        assertFalse((bad.state.array("events").last() as JsonObject).containsKey("statementId"))
        val debitPurchase = append(bad,event("debit-p1","2026-10-24","card_purchase","debit"))
        assertEquals(1,debitPurchase.state.array("creditStatements").size)
        assertFalse((debitPurchase.state.array("events").last() as JsonObject).containsKey("statementId"))
    }
}
