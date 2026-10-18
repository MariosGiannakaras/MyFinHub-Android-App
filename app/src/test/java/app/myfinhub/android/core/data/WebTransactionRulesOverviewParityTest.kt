package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web Settings > Rules ordering and read-only historical safety goldens. */
class WebTransactionRulesOverviewParityTest {
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
        {"seed":{"accounts":[{"id":"bank","name":"Μισθοδοσία","kind":"bank"}]},
         "state":{"settings":{"expenseCategories":["Τρόφιμα","Άλλο"],
            "expenseCategoryTree":[{"name":"Τρόφιμα","subcategories":["Σούπερ μάρκετ"]}]},
            "transactionRules":[
              {"id":"paused","name":"Παλαιός","priority":20,"enabled":false,
                "scopes":["manual"],"match":{"description":"petrol"},
                "action":{"category":"Άλλο"}},
              {"id":"active","name":"Μάρκετ","priority":5,"enabled":true,
                "scopes":["manual","imported","review"],
                "match":{"description":"market","mode":"equals","accountId":"bank"},
                "action":{"category":"Τρόφιμα","subcategory":"Σούπερ μάρκετ"}},
              {"id":"invalid","name":"Άγνωστος","priority":21,"enabled":true,
                "scopes":["review"],"match":{"accountId":"deleted-account"},
                "action":{"note":"Επαλήθευση"}}
            ],
            "events":[{"id":"past","kind":"expense","date":"2026-10-01",
                "category":"Άλλο","note":"market","amount":5}]}}
    """.trimIndent()).jsonObject)

    @Test fun orderedSummariesPreservePriorityScopeAndPausedState() {
        val rules=doc.canonicalTransactionRuleOverview()
        assertEquals(listOf("active","paused","invalid"),rules.map { it.id })
        assertEquals(listOf(true,false,true),rules.map { it.enabled })
        assertEquals(listOf(5.0,20.0,21.0),rules.map { it.priority })
        assertTrue(rules.first().condition.contains("είναι ακριβώς «market»"))
        assertTrue(rules.first().condition.contains("Μισθοδοσία"))
        assertTrue(rules.first().action.contains("Σούπερ μάρκετ"))
        assertEquals("κάθε νέα υποστηριζόμενη κίνηση",rules.first().scopeLabel)
        assertEquals("χειροκίνητη καταχώριση",rules[1].scopeLabel)
        assertNull(rules.first().invalidReason)
        assertNotNull(rules.last().invalidReason)
    }

    @Test fun overviewAndMatchingUseLocaleOrderForEqualGreekPriorities() {
        val rules=Json.parseToJsonElement("""
            {"seed":{},"state":{"settings":{},"transactionRules":[
                {"id":"Ζήτα","name":"Zeta","priority":5,"enabled":true,
                 "scopes":["manual"],"match":{"description":"shop"},"action":{"note":"B"}},
                {"id":"άλφα","name":"Alpha","priority":5,"enabled":true,
                 "scopes":["manual"],"match":{"description":"shop"},"action":{"note":"A"}}
            ]}}
        """.trimIndent()).jsonObject
        val document=CanonicalFinanceDocument(rules)
        assertEquals(listOf("άλφα","Ζήτα"),
            document.canonicalTransactionRuleOverview().map { it.id })
        val event=Json.parseToJsonElement(
            """{"kind":"expense","source":"user","note":"shop"}"""
        ).jsonObject
        assertEquals(listOf("άλφα","Ζήτα"),
            document.canonicalMatchingTransactionRuleIds(event))
    }

    @Test fun invalidRuleFlagsDoNotRewriteExistingTransactions() {
        val before=doc.raw
        val rows=doc.canonicalTransactionRuleOverview()
        assertEquals(3,rows.size)
        assertEquals(before,doc.raw)
        assertEquals("Ο λογαριασμός της συνθήκης δεν είναι πλέον διαθέσιμος.",
            rows.last().invalidReason)
    }
}
