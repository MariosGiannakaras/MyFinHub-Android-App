package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Web ContextualQuickAdd recurringId-linked normal expense and recurring.ts due dates. */
class WebLinkedRecurringPaymentMutationParityTest {
    private val now="2026-10-17T10:00:00Z"
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{
        "accounts":[{"id":"salary","name":"Μισθοδοσία","kind":"bank",
          "bankAccountCategory":"payroll"}],
        "snapshots":[{"date":"2026-10-01","balances":{"salary":500}}],
        "transactions":[],
        "recurring":[
          {"id":"rent","name":"Ενοίκιο","amount":100,"day":20,
           "accountId":"salary","category":"Σπίτι","active":true},
          {"id":"paused","name":"Παλιό","amount":10,"day":16,
           "accountId":"salary","category":"Άλλο","active":true}
        ]
      },"state":{
        "recurringOverrides":{"paused":{"id":"paused","name":"Νέο ποσό",
          "amount":20,"day":16,"accountId":"salary",
          "category":"Άλλο","active":false,"status":"paused"}},
        "recurringCustom":[
          {"id":"custom","name":"Cloud","amount":30,"day":18,
           "accountId":"salary","category":"Ψηφιακά","active":true}
        ],"events":[],"settings":{}
      }}
    """.trimIndent()).jsonObject)

    @Test fun linkedRecurringExpenseSettlesCurrentMonthAndRecomputesTypicalDay() {
        val item=doc.allCanonicalRecurringItems().first { it.id=="rent" }
        assertEquals(LocalDate.parse("2026-10-20"),
            doc.nextCanonicalRecurringDate(item,LocalDate.parse("2026-10-10")))
        val intent=createCanonicalRecurringPaymentMutation(doc,"rent","2026-10-05",
            "salary","stable-rent",now)
        val paid=intent.apply(doc)
        val event=paid.canonicalEvents().single()
        assertEquals("expense",event.kind)
        assertEquals(100.0,event.amount,0.001)
        assertEquals("Σπίτι",event.category)
        assertEquals("rent",(paid.state.array("events").single() as JsonObject).string("recurringId"))
        assertEquals(400.0,paid.accountBalances("2026-10-17")["salary"]!!,0.001)
        assertEquals(100.0,paid.monthlyFlow("2026-10","2026-10-17").expense,0.001)
        assertEquals(5,paid.canonicalTypicalRecurringPaymentDay(item))
        assertEquals(LocalDate.parse("2026-11-05"),
            paid.nextCanonicalRecurringDate(item,LocalDate.parse("2026-10-10")))
        assertEquals(paid.raw,intent.apply(paid).raw)
    }

    @Test fun customAndSeedOverridePaymentsKeepIdentityWithoutDuplicateSchedules() {
        val custom=createCanonicalRecurringPaymentMutation(doc,"custom","2026-10-17",
            "salary","custom-charge",now,amount=32.0).apply(doc)
        assertEquals(32.0,custom.canonicalEvents().single().amount,0.001)
        assertEquals("custom",(custom.state.array("events").single() as JsonObject)
            .string("recurringId"))
        val override=createCanonicalRecurringPaymentMutation(doc,"paused","2026-10-17",
            "salary","override-charge",now).apply(doc)
        assertEquals(20.0,override.canonicalEvents().single().amount,0.001)
        assertEquals("paused",(override.state.array("events").single() as JsonObject)
            .string("recurringId"))
        assertEquals(3,doc.allCanonicalRecurringItems().size)
    }

    @Test fun rejectsUnknownIdWrongKindInvalidAccountAndBadAmount() {
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalRecurringPaymentMutation(doc,"unknown","2026-10-17",
                "salary","bad-id",now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalRecurringPaymentMutation(doc,"rent","2026-10-17",
                "unavailable","bad-account",now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalRecurringPaymentMutation(doc,"rent","2026-10-17",
                "salary","bad-money",now,amount=-20.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalTransactionEntryMutation(doc,
                CanonicalTransactionEntryDraft(kind="transfer",date="2026-10-17",
                    amount=5.0,fromAccountId="salary",toAccountId="salary",
                    recurringId="rent"),"bad-kind",now)
        }
    }
}
