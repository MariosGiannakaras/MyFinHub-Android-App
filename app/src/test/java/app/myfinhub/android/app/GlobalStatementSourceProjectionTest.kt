package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.feature.money.canonicalStatementIdentityUnique
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The real production money projection must never erase raw statement-ID collisions. */
class GlobalStatementSourceProjectionTest {
    @Test fun malformedAndArchivedForeignRowsRemainInGlobalIdentityCounts() {
        val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[],"snapshots":[],"transactions":[]},"state":{
            "cards":[
              {"id":"active","kind":"credit","active":true,"nickname":"Active"},
              {"id":"archived","kind":"credit","active":false,"nickname":"Archived"}],
            "events":[],
            "creditStatements":[
              {"id":"shared","cardId":"active","openDate":"2026-09-01",
               "closeDate":"2026-09-30","dueDate":"2026-10-25"},
              {"id":"shared","cardId":null,"openDate":"2026-09-01",
               "closeDate":"2026-09-30","dueDate":"2026-10-25"},
              {"id":"archived-only","cardId":"archived","openDate":"2026-09-01",
               "closeDate":"2026-09-30","dueDate":"2026-10-25"},
              {"id":"active-unique","cardId":"active","openDate":"2026-09-01",
               "closeDate":"2026-09-30","dueDate":"2026-10-25"}]
          }}
        """.trimIndent()).jsonObject)
        val product = projectCanonicalProduct(doc, LocalDate.parse("2026-10-30"))
        val money = product.moneyState
        val counts = money.creditStatementSourceIdCounts
        assertNotNull(counts)
        assertEquals(2, counts?.get("shared"))
        assertEquals(1, counts?.get("archived-only"))
        assertEquals(1, counts?.get("active-unique"))
        val visible = money.creditStatementsByCard["active"].orEmpty()
        assertEquals(listOf("active-unique", "shared"), visible.map { it.id })
        assertFalse(money.creditStatementsByCard.containsKey("archived"))
        assertFalse(canonicalStatementIdentityUnique("shared", visible, counts))
        assertTrue(canonicalStatementIdentityUnique("active-unique", visible, counts))
        // The immutable source document must preserve every original row.
        assertEquals(4, doc.state["creditStatements"]?.let {
            (it as kotlinx.serialization.json.JsonArray).size
        })
    }

    @Test fun missingCardSourceStillHasExplicitEmptyProductionCountMap() {
        val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[],"snapshots":[],"transactions":[]},"state":{"cards":[],"events":[]}}
        """.trimIndent()).jsonObject)
        val product = projectCanonicalProduct(doc, LocalDate.parse("2026-10-30"))
        assertEquals(emptyMap<String, Int>(), product.moneyState.creditStatementSourceIdCounts)
    }
}
