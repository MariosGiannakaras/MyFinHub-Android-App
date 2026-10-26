package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden Web savings.ts history and mobile SavingsPage note presentation. */
class WebSavingsHistoryPresentationParityTest {
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"transactions":[
        {"id":"old-a","date":"2026-10-02","type":"expense","amount":12,
         "note":"Pay & Save","fromAccountId":"cash","toAccountId":"savings"},
        {"id":"dup","date":"2026-10-04","type":"expense","amount":7,
         "category":"Pay Save","note":"","fromAccountId":"cash"},
        {"id":"future","date":"2026-10-29","type":"expense","amount":99,
         "note":"Pay & Save"}
      ]},"state":{"events":[
        {"id":"dup","date":"2026-10-05","kind":"saving_cash_offset","amount":20,
         "savingAmount":0,"savingSource":"pay_and_save","note":"Pay & Save"},
        {"id":"manual","date":"2026-10-06","kind":"saving_cash_offset","amount":18,
         "savingAmount":11,"savingSource":"manual_transfer",
         "note":"Ένα δώρο","fromAccountId":"payroll","toAccountId":"saving"},
        {"id":"generic","date":"2026-10-07","kind":"saving_cash_offset","amount":8,
         "savingSource":"cash_offset","note":"Σύνθετη αποταμίευση με μετρητά"},
        {"id":"accent","date":"2026-10-08","kind":"saving_cash_offset","amount":9,
         "savingSource":"manual_transfer","note":"ΜΕΤΑΦΟΡΑ ΣΤΗΝ ΑΚΡΗ"},
        {"id":"future-event","date":"2026-10-30","kind":"saving_cash_offset",
         "amount":123,"savingSource":"pay_and_save","note":"Pay & Save"}
      ]}}
    """.trimIndent()).jsonObject)

    @Test fun dateScopedHistoryContainsEveryWebSourceAndPreservesAccountRoute() {
        val breakdown = doc.canonicalSavingsBreakdown("2026-10","2026-10-18")
        assertEquals(listOf("accent","generic","manual","dup","dup","old-a"),breakdown.rows.map { it.id })
        assertEquals(listOf(9.0,8.0,11.0,0.0,7.0,12.0),breakdown.rows.map { it.amount })
        assertEquals(19.0,breakdown.bySource.getValue("pay_and_save"),0.001)
        assertEquals(20.0,breakdown.bySource.getValue("manual_transfer"),0.001)
        assertEquals(8.0,breakdown.bySource.getValue("cash_offset"),0.001)
        assertEquals(47.0,breakdown.total,0.001)
        assertEquals("payroll",breakdown.rows.single { it.id=="manual" }.fromAccountId)
        assertEquals("saving",breakdown.rows.single { it.id=="manual" }.toAccountId)
        assertEquals("cash",breakdown.rows.single { it.id=="old-a" }.fromAccountId)
        assertEquals("savings",breakdown.rows.single { it.id=="old-a" }.toAccountId)
        assertTrue(breakdown.rows.filter { it.id=="dup" }.map { it.origin }.toSet()==setOf("legacy","event"))
    }

    @Test fun sameDaySavingsHistoryIdsFollowWebLocaleOrderWithoutMutatingMoney() {
        // Web savings.ts sorts descending by ID.localeCompare, so uppercase B
        // follows lowercase a in ascending order and appears FIRST descending.
        val imported = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[],"transactions":[]},"state":{"events":[
            {"id":"a-small","date":"2026-10-16","kind":"saving_cash_offset",
             "amount":10,"savingSource":"manual_transfer","note":"Manual"},
            {"id":"B-large","date":"2026-10-16","kind":"saving_cash_offset",
             "amount":20,"savingSource":"pay_and_save","note":"Pay & Save"},
            {"id":"old","date":"2026-10-15","kind":"saving_cash_offset",
             "amount":5,"savingSource":"cash_offset","note":"Other"},
            {"id":"not-a-saving","date":"2026-10-16","kind":"expense",
             "amount":900,"note":"Not included"}
          ]}}
        """.trimIndent()).jsonObject)
        val original = imported.raw
        val breakdown = imported.canonicalSavingsBreakdown("2026-10", "2026-10-16")
        assertEquals(listOf("B-large", "a-small", "old"), breakdown.rows.map { it.id })
        assertEquals(listOf(20.0, 10.0, 5.0), breakdown.rows.map { it.amount })
        assertEquals(20.0, breakdown.bySource.getValue("pay_and_save"), 0.00001)
        assertEquals(10.0, breakdown.bySource.getValue("manual_transfer"), 0.00001)
        assertEquals(5.0, breakdown.bySource.getValue("cash_offset"), 0.00001)
        assertEquals(35.0, breakdown.total, 0.00001)
        assertEquals(original, imported.raw)
    }

    @Test fun mechanismOnlyNotesAreNotMisrepresentedAsPersonalNotes() {
        val rows=doc.canonicalSavingsBreakdown("2026-10","2026-10-18").rows.associateBy { it.id }
        val manual=canonicalSavingsHistoryPresentation(rows.getValue("manual"))
        assertEquals("Ένα δώρο",manual.primary)
        assertEquals("Μεταφορά στην αποταμίευση",manual.sourceLabel)
        assertTrue(manual.hasUserNote)
        for(id in listOf("accent","generic","old-a")){
            val p=canonicalSavingsHistoryPresentation(rows.getValue(id))
            assertEquals(p.sourceLabel,p.primary)
            assertFalse(p.hasUserNote)
        }
    }

    @Test fun monthOutsideAsOfPeriodIsUnrestrictedAsInWeb() {
        assertEquals(8,doc.canonicalSavingsBreakdown("2026-10","2026-09-18").rows.size)
        assertEquals(6,doc.canonicalSavingsBreakdown("2026-10","2026-10-18").rows.size)
    }
}
