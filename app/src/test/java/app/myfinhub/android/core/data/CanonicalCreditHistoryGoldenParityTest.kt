package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Golden cases from Web cards.ts and creditStatements.ts. No finance mutations.
 * The permanent-deletion feature is deliberately not called from these tests.
 */
class CanonicalCreditHistoryGoldenParityTest {
    private fun document(cards: String, deleted: String, events: String, statements: String = "[]") =
        CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[],"snapshots":[]},"state":{
            "cards":$cards,"deletedCards":$deleted,"events":$events,
            "creditStatements":$statements
          }}
        """.trimIndent()).jsonObject)

    @Test
    fun legacyDebtOwnerRemainsOldestCreditIdentityAfterArchiveAndDeletion() {
        val data = document(
            cards = """[
              {"id":"new-credit","kind":"credit","createdAt":"2026-06-01T00:00:00Z","active":true},
              {"id":"old-debit","kind":"debit","createdAt":"2024-01-01T00:00:00Z","active":true}
            ]""",
            deleted = """[
              {"id":"old-credit","kind":"credit","createdAt":"2025-05-01T00:00:00Z","deletedAt":"2026-07-01T00:00:00Z"}
            ]""",
            events = """[
              {"id":"old-purchase","kind":"card_purchase","date":"2026-05-02","amount":100},
              {"id":"new-purchase","kind":"card_purchase","date":"2026-05-03","cardId":"new-credit","amount":220},
              {"id":"old-payment","kind":"card_payment","date":"2026-05-04","amount":30},
              {"id":"new-payment","kind":"card_payment","date":"2026-05-05","cardId":"new-credit","amount":60},
              {"id":"future-old","kind":"card_payment","date":"2026-06-02","amount":1000}
            ]""",
        )
        assertEquals(70.0, data.creditDebtForCardAt("old-credit","2026-06-01"), 0.001)
        assertEquals(160.0, data.creditDebtForCardAt("new-credit","2026-06-01"), 0.001)
        assertEquals(0.0, data.creditDebtForCardAt("old-debit","2026-06-01"), 0.001)
        // Deleting the oldest card must not silently re-attribute its old debt.
        assertEquals(70.0, data.creditDebtForCardAt("old-credit","2026-05-31"), 0.001)
    }

    @Test
    fun linkedStatementUsesWebChronologyEvenWhenRawEventsAreShuffled() {
        val data = document(
            cards = """[{"id":"card-a","kind":"credit","createdAt":"2026-01-01T00:00:00Z"}]""",
            deleted = "[]",
            statements = """[
              {"id":"stmt-a","cardId":"card-a","openDate":"2026-09-26",
                "closeDate":"2026-10-25","dueDate":"2026-11-05",
                "boundaryRule":"next-cycle"}
            ]""",
            events = """[
              {"id":"pay-later","date":"2026-10-13","createdAt":"2026-10-13T09:00:00Z",
               "kind":"card_payment","cardId":"card-a","statementId":"stmt-a","amount":5},
              {"id":"purchase-b","date":"2026-10-11","createdAt":"2026-10-11T15:00:00Z",
               "kind":"card_purchase","cardId":"card-a","statementId":"stmt-a","amount":20},
              {"id":"purchase-c","date":"2026-10-11","createdAt":"2026-10-11T15:00:00Z",
               "kind":"card_purchase","cardId":"card-a","statementId":"stmt-a","amount":10},
              {"id":"purchase-a","date":"2026-10-11","createdAt":"2026-10-11T09:00:00Z",
               "kind":"card_purchase","cardId":"card-a","statementId":"stmt-a","amount":15},
              {"id":"pay-early","date":"2026-10-12","createdAt":"2026-10-12T09:00:00Z",
               "kind":"card_payment","cardId":"card-a","statementId":"stmt-a","amount":4},
              {"id":"unlinked","date":"2026-10-10","createdAt":"2026-10-10T09:00:00Z",
               "kind":"card_purchase","cardId":"card-a","amount":13}
            ]""",
        )
        val view = data.creditStatementViews("card-a","2026-10-14").single()
        assertEquals(listOf("purchase-a","purchase-b","purchase-c"), view.purchaseIds)
        assertEquals(listOf("pay-early","pay-later"), view.paymentIds)
        assertEquals(45.0, view.purchaseTotal, 0.001)
        assertEquals(9.0, view.paymentTotal, 0.001)
        assertEquals(36.0, view.remaining, 0.001)
        assertEquals("open", view.status)
        assertEquals(listOf("unlinked"), data.unlinkedCreditStatementEventIds("card-a"))
        assertEquals("stmt-a", data.recommendedPayableStatement("card-a","2026-10-14")?.id)
        assertEquals(listOf("purchase-a","purchase-b","purchase-c"), data.creditStatementViews("card-a","2026-10-12").single().purchaseIds)
        assertEquals(listOf("pay-early"), data.creditStatementViews("card-a","2026-10-12").single().paymentIds)
        assertNull(data.recommendedPayableStatement("unknown","2026-10-14"))
    }

    @Test
    fun tieBreaksOldestCardIdentityByIdLikeWeb() {
        val data = document(
            cards = """[
              {"id":"z-credit","kind":"credit","createdAt":"2026-01-01T00:00:00Z"},
              {"id":"b-credit","kind":"credit","createdAt":"2026-01-01T00:00:00Z"}
            ]""",
            deleted = """[
              {"id":"a-credit","kind":"credit","createdAt":"2026-01-01T00:00:00Z","deletedAt":"2026-02-01T00:00:00Z"}
            ]""",
            events = """[{"id":"legacy","kind":"card_purchase","date":"2026-02-02","amount":37}]""",
        )
        assertEquals(37.0, data.creditDebtForCardAt("a-credit","2026-02-03"), 0.001)
        assertEquals(0.0, data.creditDebtForCardAt("b-credit","2026-02-03"), 0.001)
        assertEquals(0.0, data.creditDebtForCardAt("z-credit","2026-02-03"), 0.001)
    }
}
