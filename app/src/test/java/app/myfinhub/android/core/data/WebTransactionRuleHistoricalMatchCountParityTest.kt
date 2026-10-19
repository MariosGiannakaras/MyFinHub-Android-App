package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Web transactionRuleMatchingEvents(data,rule) forces the one rule enabled
 * without mutating any historic event. Scoped kinds and income safeguards
 * must exactly match previewTransactionRules.
 */
class WebTransactionRuleHistoricalMatchCountParityTest {
    private val document=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"transactions":[
        {"id":"old-legacy","type":"expense","date":"2026-10-11",
         "amount":15,"note":"MARKET","accountId":"cash"}
      ]},"state":{"settings":{"expenseCategories":["Τρόφιμα"]},
       "transactionRules":[
         {"id":"disabled","name":"Market", "priority":0, "enabled":false,
          "scopes":["manual"],"match":{"description":" MARKET ","mode":"equals",
          "accountId":"cash"},"action":{"category":"Τρόφιμα"}},
         {"id":"income-only","name":"Income note", "priority":1,"enabled":true,
          "scopes":["manual"],"match":{"description":"bonus"},
          "action":{"note":"Checked"}},
         {"id":"income-category","name":"Forbidden income category",
          "priority":2,"enabled":true,"scopes":["manual"],
          "match":{"description":"bonus"},"action":{"category":"Τρόφιμα"}}
       ],
       "events":[
         {"id":"m1","kind":"expense","date":"2026-10-11",
          "note":"market","accountId":"cash","amount":15},
         {"id":"m2","kind":"refund","date":"2026-10-12",
          "note":"MARKET","fromAccountId":"cash","amount":5},
         {"id":"m3","kind":"card_purchase","date":"2026-10-12",
          "note":"market","accountId":"bank","amount":5},
         {"id":"review","kind":"expense","source":"review",
          "date":"2026-10-12","note":"MARKET","accountId":"cash","amount":2},
         {"id":"migration","kind":"expense","source":"migration",
          "date":"2026-10-12","note":"MARKET","accountId":"cash","amount":2},
         {"id":"income","kind":"income","date":"2026-10-13",
          "note":"BONUS","accountId":"cash","amount":900},
         {"id":"transfer","kind":"transfer","date":"2026-10-13",
          "note":"MARKET","accountId":"cash","amount":900}
       ]}}
    """.trimIndent()).jsonObject)

    @Test fun previewCountsDisabledRuleAndOnlyCurrentEventsByWebScopeKindAndAccount() {
        val original=document.raw
        val rows=document.canonicalTransactionRuleOverview()
        assertEquals(listOf("disabled","income-only","income-category"),rows.map { it.id })
        assertEquals(listOf(2,1,0),rows.map { it.historicalMatchCount })
        assertTrue(rows.first().enabled.not())
        assertEquals(original,document.raw)
    }

    @Test fun historicalMatchCountIncludesImportedAndReviewOnlyWhenRuleScopeAllowsThem() {
        val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{},"state":{"transactionRules":[
            {"id":"review","name":"Review", "enabled":true,"priority":0,
             "scopes":["review"],"match":{"description":"market"},
             "action":{"note":"reviewed"}},
            {"id":"import","name":"Imported", "enabled":false,"priority":1,
             "scopes":["imported"],"match":{"description":"market"},
             "action":{"category":"Τρόφιμα"}}
          ],"events":[
            {"id":"r1","kind":"expense","source":"review","date":"2026-10-09",
             "amount":1,"note":"market"},
            {"id":"i1","kind":"expense","source":"migration","date":"2026-10-09",
             "amount":1,"note":"market"},
            {"id":"m1","kind":"expense","date":"2026-10-09",
             "amount":1,"note":"market"}
          ]}}
        """.trimIndent()).jsonObject)
        val before=doc.raw
        val list=doc.canonicalTransactionRuleOverview()
        assertEquals(listOf(1,1),list.map { it.historicalMatchCount })
        assertEquals(before,doc.raw)
    }

    @Test fun disabledRuleStillExcludedFromFirstMatchSubmission() {
        val newEntry=Json.parseToJsonElement("""
          {"kind":"expense","note":"market","accountId":"cash","amount":12}
        """).jsonObject
        assertTrue(document.canonicalMatchingTransactionRuleIds(newEntry).isEmpty())
        assertEquals(newEntry,document.applyCanonicalTransactionRules(newEntry))
    }
}
