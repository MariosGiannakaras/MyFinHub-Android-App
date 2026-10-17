package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web domain.ts effectiveLegacyTransactions keeps every historical source row. */
class WebLegacyDuplicateIdProjectionParityTest {
    private fun fixture(deleted: String = "[]") = CanonicalFinanceDocument(
        Json.parseToJsonElement("""
          {"seed":{"transactions":[
            {"id":"same","date":"2026-10-02","type":"expense",
             "amount":10,"category":"Τρόφιμα","accountId":"bank"},
            {"id":"same","date":"2026-10-03","type":"expense",
             "amount":20,"category":"Τρόφιμα","accountId":"bank"},
            {"id":"other","date":"2026-10-04","type":"expense",
             "amount":5,"category":"Υγεία","accountId":"bank"}
          ]},"state":{"deleted":$deleted,"overrides":{
            "same":{"id":"same","date":"2026-10-05","type":"expense",
             "amount":30,"category":"Τρόφιμα","accountId":"bank"}
          }}}
        """.trimIndent()).jsonObject,
    )

    @Test fun sharedOverrideAppliesToEachOriginalRowNotJustLastMatchingId() {
        val doc = fixture()
        val rows = doc.effectiveLegacyTransactions()
        assertEquals(3,rows.size)
        assertEquals(listOf("other","same","same"),rows.map { it.id })
        assertEquals(listOf(5.0,30.0,30.0),rows.map { it.amount })
        assertEquals(65.0,doc.monthlyFlow("2026-10").expense,0.001)
        assertEquals(60.0,doc.categoryBudgetSpending("2026-10")["Τρόφιμα"]!!,0.001)
        assertEquals(60.0,doc.categoryTotals("2026-10")["Τρόφιμα"]!!,0.001)
    }

    @Test fun DeletionFiltersAllMatchingSourceRowsEvenWithStaleOverride() {
        val doc=fixture(deleted="""["same"]""")
        assertEquals(listOf("other"),doc.effectiveLegacyTransactions().map { it.id })
        assertEquals(5.0,doc.monthlyFlow("2026-10").expense,0.001)
        assertTrue(doc.categoryBudgetSpending("2026-10")["Τρόφιμα"] == null)
    }
}
