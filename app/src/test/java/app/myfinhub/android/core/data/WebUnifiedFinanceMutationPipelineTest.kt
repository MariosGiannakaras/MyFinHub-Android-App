package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web-derived finance vectors through one canonical new-event/offline replay pipeline. */
class WebUnifiedFinanceMutationPipelineTest {
    private val now = "2026-10-15T12:00:00Z"
    private val finance = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"app":"MyFinHub","schemaVersion":3,"updatedAt":"2026-10-15T00:00:00Z",
       "unknownRoot":"keep",
       "seed":{"accounts":[
         {"id":"payroll-42","kind":"bank","name":"Μισθοδοσία","providerId":"piraeus"},
         {"id":"cash-wallet","kind":"cash","name":"Μετρητά"}
       ],"transactions":[],"snapshots":[]},
       "state":{"settings":{
         "expenseCategories":["Άλλο"],
         "incomeCategories":["Μισθός"]
       },"transactionRules":[
         {"id":"market-food","priority":1,"enabled":true,"scopes":["manual"],
          "match":{"description":"market","mode":"contains"},
          "action":{"category":"Τρόφιμα"}}
       ],"cards":[
         {"id":"visa","kind":"credit","active":true,"bankId":"piraeus"}
       ],"events":[
         {"id":"existing-purchase","kind":"card_purchase","date":"2026-10-12",
          "cardId":"visa","amount":50,"creditDelta":-50}
       ],"unknownState":"keep"}}
    """.trimIndent()).jsonObject)

    private fun compareLegacyWithModern(
        draft: CanonicalEventDraft,
        core: CanonicalTransactionEntryDraft,
        id: String,
    ): CanonicalFinanceDocument {
        val legacy = createCanonicalEventMutation(finance, draft, id, now)
        val canonical = createCanonicalTransactionEntryMutation(finance, core, id, now)
        assertTrue(legacy is AppendCanonicalEvent)
        assertTrue(canonical is AppendCanonicalEvent)
        assertEquals((canonical as AppendCanonicalEvent).event, (legacy as AppendCanonicalEvent).event)
        val first = legacy.apply(finance)
        assertEquals(first.raw, legacy.apply(first).raw)
        assertEquals("keep",first.raw["unknownRoot"]?.toString()?.trim('"'))
        assertEquals("keep",first.state.string("unknownState"))
        return first
    }

    @Test fun legacyExpenseUsesSameWebRuleFirstMatchAndStableReplay() {
        val legacy = CanonicalEventDraft(
            kind="expense", date="2026-10-15", amount=12.345, note="Corner MARKET",
            category="Άλλο", accountId="payroll-42",
        )
        val result=compareLegacyWithModern(
            legacy,
            CanonicalTransactionEntryDraft(
                kind=legacy.kind,date=legacy.date,amount=12.35,note=legacy.note,
                category=legacy.category,accountId=legacy.accountId,
            ),
            "expense-new",
        )
        val added=result.canonicalEvents().single { it.id=="expense-new" }
        assertEquals("Τρόφιμα",added.category)
        assertEquals(12.35,added.amount,0.001)
        assertEquals(-12.35,added.legs.single().amount,0.001)
        assertEquals(1,result.state.array("transactionRules").size)
    }

    @Test fun legacyTransferReusesStrictBalancedLegs() {
        val draft=CanonicalEventDraft(
            kind="transfer",date="2026-10-15",amount=17.40,note="Μεταφορά",
            fromAccountId="payroll-42",toAccountId="cash-wallet",
        )
        val result=compareLegacyWithModern(draft,CanonicalTransactionEntryDraft(
            kind=draft.kind,date=draft.date,amount=draft.amount,note=draft.note,
            fromAccountId=draft.fromAccountId,toAccountId=draft.toAccountId,
        ),"transfer-new")
        assertEquals(listOf(-17.40,17.40),
            result.canonicalEvents().single { it.id=="transfer-new" }.legs.map { it.amount })
    }

    @Test fun legacySplitMaintainsExactCentsAndSharesCanonicalValidator() {
        val split=listOf(
            CanonicalSplitDraft("p1","Food","Τρόφιμα",amount=5.05),
            CanonicalSplitDraft("p2","Other","",amount=6.15),
        )
        val draft=CanonicalEventDraft(
            kind="split",date="2026-10-15",amount=11.20,note="Split",
            accountId="payroll-42",parts=split,
        )
        val result=compareLegacyWithModern(draft,CanonicalTransactionEntryDraft(
            kind=draft.kind,date=draft.date,amount=draft.amount,note=draft.note,
            accountId=draft.accountId,
            parts=split.map { CanonicalTransactionSplitDraft(
                id=it.id,label=it.label,category=it.category.ifBlank { "Άλλο" },
                subcategory=it.subcategory,amount=it.amount) },
        ),"split-new")
        val event=result.canonicalEvents().single { it.id=="split-new" }
        assertEquals(11.20,event.amount,0.001)
        assertEquals(-11.20,event.legs.single().amount,0.001)
        assertEquals(listOf("Τρόφιμα","Άλλο"),event.parts.map { it.category })
        assertEquals(1120L,event.parts.sumOf { moneyToCents(it.amount) })
    }

    @Test fun legacyCardPaymentEnforcesProviderIdentityAndDebtLimits() {
        val draft=CanonicalEventDraft(
            kind="card_payment",date="2026-10-15",amount=12.0,note="Αποπληρωμή",
            fromAccountId="payroll-42",cardId="visa",
        )
        val result=compareLegacyWithModern(draft,CanonicalTransactionEntryDraft(
            kind=draft.kind,date=draft.date,amount=draft.amount,note=draft.note,
            fromAccountId=draft.fromAccountId,cardId=draft.cardId,
        ),"payment-new")
        assertEquals(38.0,result.creditDebtForCardAt("visa","2026-10-15"),0.001)
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalEventMutation(finance,draft.copy(amount=51.0),"overpay",now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalEventMutation(finance,draft.copy(fromAccountId="cash-wallet"),"wrong-bank",now)
        }
    }

    @Test fun legacySplitRejectsIncorrectSumWithoutModifyingServerHistory() {
        val draft=CanonicalEventDraft(
            kind="split",date="2026-10-15",amount=11.21,note="Split",
            accountId="payroll-42",parts=listOf(
                CanonicalSplitDraft("a","","Τρόφιμα",amount=5.05),
                CanonicalSplitDraft("b","","Άλλο",amount=6.15),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalEventMutation(finance,draft,"invalid-split",now)
        }
        assertEquals(1,finance.canonicalEvents().size)
    }

    @Test fun frozenQueuedEventDoesNotReapplyChangedRulesDuringReplay() {
        val mutation=createCanonicalEventMutation(finance,CanonicalEventDraft(
            kind="expense",date="2026-10-15",amount=10.0,note="Market",
            category="Άλλο",accountId="payroll-42",
        ),"stable-queued-event",now)
        val changed=CanonicalFinanceDocument(JsonObject(finance.raw + (
            "state" to JsonObject(finance.state + (
                "transactionRules" to JsonArray(emptyList())
            ))
        )))
        val replayed=mutation.apply(changed)
        val event=replayed.canonicalEvents().first { it.id=="stable-queued-event" }
        assertEquals("Τρόφιμα",event.category)
        assertEquals(replayed.raw,mutation.apply(replayed).raw)
    }
}
