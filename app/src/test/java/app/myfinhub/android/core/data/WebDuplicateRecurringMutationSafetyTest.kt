package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Web allRecurringItems preserves duplicate historic rows, not safe write targets. */
class WebDuplicateRecurringMutationSafetyTest {
    private val now="2026-10-17T14:00:00Z"
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[{"id":"salary","kind":"bank","bankAccountCategory":"payroll"}],
        "snapshots":[{"date":"2026-10-01","balances":{"salary":500}}],
        "recurring":[
          {"id":"same","name":"First","amount":30,"day":15,"active":true,
            "accountId":"salary","category":"Σπίτι"},
          {"id":"same","name":"Second","amount":60,"day":20,"active":true,
            "accountId":"salary","category":"Πάγια"},
          {"id":"unique","name":"Unique","amount":20,"day":12,"active":true,
            "accountId":"salary","category":"Τρόφιμα"}
        ],"transactions":[]},
       "state":{"events":[],"recurringCustom":[
          {"id":"same","name":"Custom duplicate","amount":40,"day":22,"active":true,
            "accountId":"salary","category":"Άλλο"}
        ]}}
    """.trimIndent()).jsonObject)

    @Test fun repeatsStayVisibleAsIndependentHistoricalRows() {
        val items=doc.allCanonicalRecurringItems()
        assertEquals(4,items.size)
        assertEquals(listOf("First","Second","Custom duplicate"),items.filter { it.id=="same" }.map { it.name })
        assertEquals(130.0,items.filter { it.id=="same" }.sumOf { it.amount },0.001)
    }

    @Test fun cannotPauseOrPayAmbiguousRecurringIdIncludingDirectBypass() {
        assertThrows(IllegalArgumentException::class.java) {
            SetCanonicalRecurringStatus("same","paused",now).apply(doc)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalRecurringPaymentMutation(doc,"same","2026-10-17","salary","bad-pay",now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            createCanonicalTransactionEntryMutation(doc,CanonicalTransactionEntryDraft(
                kind="expense",date="2026-10-17",amount=30.0,
                accountId="salary",recurringId="same",
            ),"bad-direct",now)
        }
        assertEquals(500.0,doc.accountBalances("2026-10-17")["salary"]!!,0.001)
        assertEquals(0,doc.canonicalEvents().size)
    }

    @Test fun uniqueRecurringStillSupportsStatusAndLinkedPaymentReplay() {
        val stopped=SetCanonicalRecurringStatus("unique","stopped",now).apply(doc)
        assertEquals("stopped",stopped.allCanonicalRecurringItems().first { it.id=="unique" }.effectiveStatus)
        val action=createCanonicalRecurringPaymentMutation(doc,"unique","2026-10-17",
            "salary","unique-payment",now)
        val paid=action.apply(doc)
        assertEquals("unique",paid.state.array("events").first().let {
            (it as kotlinx.serialization.json.JsonObject).string("recurringId")
        })
        assertEquals(480.0,paid.accountBalances("2026-10-17")["salary"]!!,0.001)
        assertEquals(paid.raw,action.apply(paid).raw)
    }
}
