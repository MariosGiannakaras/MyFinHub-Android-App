package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/** Golden cases derived from Web budgets-rules.test.ts and transactionRules.ts. */
class CanonicalTransactionRulesParityTest {
    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject
    private fun rule(
        id: String,
        priority: Int,
        match: JsonObject = obj("""{"description":"market","mode":"contains"}"""),
        action: JsonObject = obj("""{"category":"Τρόφιμα"}"""),
        scopes: List<String> = listOf("manual"),
        enabled: Boolean = true,
    ): JsonObject = JsonObject(mapOf(
        "id" to JsonPrimitive(id),
        "priority" to JsonPrimitive(priority),
        "enabled" to JsonPrimitive(enabled),
        "scopes" to JsonArray(scopes.map { JsonPrimitive(it) }),
        "match" to match,
        "action" to action,
    ))

    private fun fixture(vararg rules: JsonObject): CanonicalFinanceDocument {
        val base = obj("""{"seed":{"accounts":[{"id":"bank-main","name":"Main","kind":"bank"}]},"state":{
          "settings":{"expenseCategories":["Άλλο"],"incomeCategories":["Μισθός"]},
          "events":[{"id":"history","kind":"expense","note":"market","category":"Άλλο"}]
        }}""")
        val state = JsonObject(base["state"]!!.jsonObject + ("transactionRules" to JsonArray(rules.toList())))
        return CanonicalFinanceDocument(JsonObject(base + ("state" to state)))
    }

    private fun event(
        kind: String = "expense",
        category: String = "Άλλο",
        note: String = "Corner Market",
        source: String = "user",
        account: String = "bank-main",
    ): JsonObject = JsonObject(mapOf(
        "id" to JsonPrimitive("new"),
        "kind" to JsonPrimitive(kind),
        "note" to JsonPrimitive(note),
        "category" to JsonPrimitive(category),
        "source" to JsonPrimitive(source),
        "accountId" to JsonPrimitive(account),
    ))

    @Test fun firstMatchUsesPriorityThenIdNotSourceArrayOrder() {
        val data = fixture(rule("later",20),
            rule("winner-b",10,action=obj("""{"category":"Υγεία"}""")),rule("winner",10))
        val e = event()
        assertEquals(listOf("winner","winner-b","later"),data.canonicalMatchingTransactionRuleIds(e))
        assertEquals("Τρόφιμα",data.applyCanonicalTransactionRules(e).string("category"))
    }

    @Test fun equalPriorityGreekIdsUseWebLocaleComparisonRatherThanCodePointOrdering() {
        val data = fixture(
            rule("Ζήτα",10, action=obj("""{"category":"Δεύτερη"}""")),
            rule("άλφα",10, action=obj("""{"category":"Πρώτη"}""")),
        )
        val event = event()
        assertEquals(listOf("άλφα","Ζήτα"),data.canonicalMatchingTransactionRuleIds(event))
        assertEquals("Πρώτη",data.applyCanonicalTransactionRules(event).string("category"))
    }

    @Test fun explicitCategoryAndHistoricalEventsRemainUntouched() {
        val data = fixture(rule("food",1))
        val snapshot = data.state.array("events")
        val e = event(category="Υγεία")
        assertSame(e,data.applyCanonicalTransactionRules(e))
        assertEquals(snapshot,data.state.array("events"))
    }

    @Test fun scopeEqualsMerchantAndAccountConditionsMatchWeb() {
        val onlyImport = rule("import",1,scopes=listOf("imported"))
        val exact = rule("exact",2,
            match=obj("""{"description":"market","mode":"equals","merchant":"market","accountId":"bank-main"}"""))
        val data = fixture(onlyImport,exact)
        assertEquals(emptyList<String>(),data.canonicalMatchingTransactionRuleIds(event()))
        assertEquals(listOf("exact"),data.canonicalMatchingTransactionRuleIds(event(note="MARKET")))
        assertEquals(listOf("import"),data.canonicalMatchingTransactionRuleIds(event(source="migration")))
        assertEquals(emptyList<String>(),data.canonicalMatchingTransactionRuleIds(event(note="market",account="other")))
    }

    @Test fun incomeKeepsOwnTaxonomyAndAllowsNoteOnlyRule() {
        val categorical = rule("category-first",1,match=obj("""{"accountId":"bank-main"}"""))
        val note = rule("note",2,match=obj("""{"accountId":"bank-main"}"""),
            action=obj("""{"note":"Αυτόματη σημείωση"}"""))
        val data = fixture(categorical,note)
        val income = event(kind="income",category="Μισθός",note="")
        assertEquals(listOf("note"),data.canonicalMatchingTransactionRuleIds(income))
        val applied = data.applyCanonicalTransactionRules(income)
        assertEquals("Μισθός",applied.string("category"))
        assertEquals("Αυτόματη σημείωση",applied.string("note"))
        assertEquals("Τρόφιμα",data.applyCanonicalTransactionRules(event(kind="refund")).string("category"))
    }

    @Test fun rulesApplyOnNewExpenseOnceAndQueuedMutationReplaysWithoutChangingHistory() {
        val data = fixture(rule("market-food",1))
        val mutation = createCanonicalTransactionEntryMutation(
            data, CanonicalTransactionEntryDraft(
                kind = "expense", date = "2026-10-15", amount = 12.0,
                accountId = "bank-main", note = "Corner Market", category = "Άλλο",
            ), "stable-expense-1", "2026-10-15T10:00:00Z",
        )
        val result = mutation.apply(data)
        assertEquals("Τρόφιμα",result.canonicalEvents().first { it.id == "stable-expense-1" }.category)
        assertEquals("Άλλο",(result.state.array("events").first() as JsonObject).string("category"))
        assertEquals(result.raw,mutation.apply(result).raw)
    }

    @Test fun accountMatchedIncomeNoteOnlyRuleRunsBeforeDefaultNoteFallback() {
        val noteRule = rule("account-income-note",1,
            match=obj("""{"accountId":"bank-main"}"""),
            action=obj("""{"note":"Αυτόματη σημείωση"}"""))
        val data = fixture(noteRule)
        val mutation = createCanonicalTransactionEntryMutation(
            data, CanonicalTransactionEntryDraft(
                kind = "income", date = "2026-10-15", amount = 100.0,
                accountId = "bank-main", note = "", category = "Μισθός",
            ), "stable-income-1", "2026-10-15T10:00:00Z",
        )
        val result = mutation.apply(data)
        val income = result.canonicalEvents().first { it.id == "stable-income-1" }
        assertEquals("Μισθός",income.category)
        assertEquals("Αυτόματη σημείωση",income.note)
    }

    @Test fun historicalEmptyExpenseDefaultFallsBackToWebOtherForRulePreviewAndApply() {
        val food = rule("food",1,match=obj("""{"description":"market","mode":"contains"}"""))
        val base = fixture(food)
        val settings = obj("""{"expenseCategories":[""],"incomeCategories":[""]}""")
        val modified = CanonicalFinanceDocument(JsonObject(base.raw + ("state" to
            JsonObject(base.state + ("settings" to settings)))))
        val e = event(kind="expense",category="Άλλο",note="Corner Market")
        assertEquals(listOf("food"),modified.canonicalMatchingTransactionRuleIds(e))
        assertEquals("Τρόφιμα",modified.applyCanonicalTransactionRules(e).string("category"))
        assertEquals("Άλλο",e.string("category"))
    }

    @Test fun blankRuleDefaultStillRespectsExplicitUserCategoryAndHistory() {
        val base = fixture(rule("food",1))
        val settings = obj("""{"expenseCategories":[""],"incomeCategories":["Μισθός"]}""")
        val doc = CanonicalFinanceDocument(JsonObject(base.raw + ("state" to
            JsonObject(base.state + ("settings" to settings)))))
        val e = event(category="Υγεία")
        assertSame(e,doc.applyCanonicalTransactionRules(e))
        assertEquals("Άλλο",(doc.state.array("events").single() as JsonObject).string("category"))
    }

    @Test fun disabledRulesAndTransfersHaveNoEffect() {
        val data=fixture(rule("disabled",1,enabled=false))
        val e=event()
        assertSame(e,data.applyCanonicalTransactionRules(e))
        val transfer=event(kind="transfer")
        assertEquals(emptyList<String>(),data.canonicalMatchingTransactionRuleIds(transfer))
        assertFalse(data.applyCanonicalTransactionRules(transfer).containsKey("subcategory"))
    }
}
