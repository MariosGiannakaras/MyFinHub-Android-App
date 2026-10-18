package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Web recurringUpcoming ordering and recurringPayments historical summary.
 * Active rows are sorted by due date then Greek name; inactive rows retain
 * their original seed/custom order. This is strictly read-only.
 */
class WebRecurringUpcomingOrderParityTest {
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"recurring":[
        {"id":"later","name":"Μηνιαίο παλιό","amount":12,"day":10,"active":true},
        {"id":"zeta","name":"Ζήτα","amount":20,"day":20,"active":true},
        {"id":"alpha","name":"Άλφα","amount":25,"day":20,"active":true},
        {"id":"undated","name":"Χωρίς ημερομηνία","amount":10,"active":true},
        {"id":"paused","name":"Σε παύση","amount":10,"active":false,"status":"paused"},
        {"id":"stopped","name":"Σταματημένο","amount":10,"active":false,"status":"stopped"}
      ]},"state":{"recurringCustom":[
        {"id":"custom","name":"Custom","amount":20,"day":19,"active":true},
        {"id":"custom-paused","name":"Custom Paused","amount":10,"active":false,"status":"paused"}
      ],"events":[
        {"id":"payment-old","kind":"expense","recurringId":"later",
         "date":"2026-09-10","amount":12,"createdAt":"2026-09-11T10:00:00Z"},
        {"id":"payment-new","kind":"expense","recurringId":"later",
         "date":"2026-10-04","amount":12,"createdAt":"2026-10-04T11:00:00Z"}
      ]}}
    """.trimIndent()).jsonObject)

    @Test fun webUpcomingSortsByDueDateThenGreekNameAndInactiveKeepsSourceOrder() {
        val old=doc.raw
        val ordered=doc.canonicalWebRecurringManagementOrder(LocalDate.parse("2026-10-18"))
        assertEquals(listOf("custom","alpha","zeta","later","undated",
            "paused","stopped","custom-paused"),ordered.map { it.id })
        assertEquals(listOf("paused","stopped","custom-paused"),
            ordered.filter { it.effectiveStatus!="active" }.map { it.id })
        assertEquals(old,doc.raw)
    }

    @Test fun webLinkedPaymentsKeepLatestDateAndUnmodifiedHistory() {
        val history=doc.canonicalRecurringPaymentHistory("later")
        assertEquals(2,history.paymentCount)
        assertEquals("2026-10-04",history.lastPaymentDateIso)
        val row=doc.allCanonicalRecurringItems().first { it.id=="later" }
        assertEquals(7,doc.canonicalTypicalRecurringPaymentDay(row))
        assertEquals("2026-11-07",doc.nextCanonicalRecurringDate(row,
            LocalDate.parse("2026-10-18"))?.toString())
        assertEquals(0,doc.canonicalRecurringPaymentHistory("unknown").paymentCount)
        assertTrue(doc.canonicalRecurringPaymentHistory("unknown").lastPaymentDateIso==null)
    }

    @Test fun duplicateHistoricalIdsRemainSeparateReadOnlyItems() {
        val duplicate=CanonicalFinanceDocument(Json.parseToJsonElement("""
         {"seed":{"recurring":[
            {"id":"same","name":"Ζήτα","day":12,"active":true},
            {"id":"same","name":"Άλφα","day":12,"active":true}
         ]},"state":{"events":[]}}
        """.trimIndent()).jsonObject)
        val before=duplicate.raw
        assertEquals(listOf("Άλφα","Ζήτα"),
            duplicate.canonicalWebRecurringManagementOrder(LocalDate.parse("2026-10-10"))
                .map { it.name })
        assertEquals(before,duplicate.raw)
    }
}
