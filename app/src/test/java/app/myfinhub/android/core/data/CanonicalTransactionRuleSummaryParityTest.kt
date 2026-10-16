package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web transaction-rule overview uses stable priority/ID while preserving merchant privacy. */
class CanonicalTransactionRuleSummaryParityTest {
    private val document=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[]},"state":{"transactionRules":[
        {"id":"later","name":"Αγορά από συγκεκριμένο κατάστημα","priority":10,
         "enabled":true,"scopes":["manual","review"],"match":{"merchant":"PRIVATE"}},
        {"id":"disabled","name":"Παλιός κανόνας","priority":1,
         "enabled":false,"scopes":["imported"]},
        {"id":"equal","name":"Ίση προτεραιότητα","priority":10,
         "enabled":true,"scopes":["manual","manual","unsafe"]}
      ]}}
    """.trimIndent()).jsonObject)

    @Test fun sortsByPriorityAndIdWithSafeScopesWithoutMutatingHistory() {
        val rows=document.transactionRuleSummaries()
        assertEquals(listOf("disabled","equal","later"),rows.map { it.id })
        assertEquals(listOf("manual"),rows[1].scopes)
        assertEquals(listOf("manual","review"),rows[2].scopes)
        assertFalse(rows[0].enabled)
        assertTrue(rows[1].enabled)
        assertFalse(rows.last().toString().contains("PRIVATE"))
        assertFalse(rows.last().toString().contains("Αγορά"))
    }
}
