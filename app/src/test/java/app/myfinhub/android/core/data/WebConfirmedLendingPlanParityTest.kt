package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web lendingPlans.ts confirmed records are schedules, never repaid ledger events. */
class WebConfirmedLendingPlanParityTest {
    private fun finance(plans: String, repaid: Double = 0.0): CanonicalFinanceDocument {
        val events = if (repaid > 0.0) """
            [{"id":"repay-1","kind":"repayment","date":"2026-08-20",
              "person":"Alex","amount":$repaid,"receivableDelta":-${repaid}}]
        """.trimIndent() else "[]"
        return CanonicalFinanceDocument(Json.parseToJsonElement("""
            {"seed":{"lending":[{"person":"Alex","outstanding":100,
              "entries":[{"date":"2026-01-01","lent":100}]}]},
             "state":{"events":$events,"lendingPlans":$plans}}
        """.trimIndent()).jsonObject)
    }
    private val plan = """
        [{"id":"lending-plan-1","person":"Alex","asOf":"2026-08-17",
          "firstDueDate":"2026-10-31","total":100,"installments":3,
          "everyMonths":1,"confirmedAt":"2026-08-17T10:00:00.000Z"}]
    """.trimIndent()

    @Test fun validPlanPreservesExactCommittedMetadataAndCannotRecordIncome() {
        val data = finance(plan)
        val before = data.raw
        val rows = data.canonicalConfirmedLendingPlanRows()
        assertEquals(1,rows.size)
        val one = rows.single()
        assertEquals("Alex",one.person)
        assertEquals(100.0,one.total!!,0.00001)
        assertEquals(3,one.installments)
        assertEquals(1,one.everyMonths)
        assertEquals("2026-10-31",one.firstDueDate)
        assertNull(one.validationError)
        assertTrue(data.canonicalEvents().isEmpty())
        assertEquals(before,data.raw)
    }

    @Test fun confirmedScheduleCanExceedNewOutstandingAfterARealRepayment() {
        val data = finance(plan,repaid = 60.0)
        val before = data.raw
        val planned = data.canonicalConfirmedLendingPlanRows().single()
        val outstanding = data.canonicalLendingPersonRows("2026-08-21")
            .single { it.person == "Alex" }.outstanding
        assertEquals(100.0,planned.total!!,0.00001)
        assertEquals(40.0,outstanding,0.00001)
        assertEquals(1,data.canonicalEvents().size)
        assertEquals(before,data.raw)
    }

    @Test fun duplicateActivePlansFailClosedInsteadOfChoosingArbitraryId() {
        val duplicate = plan.dropLast(2) + """,
          {"id":"other","person":"Alex","asOf":"2026-08-17",
           "firstDueDate":"2026-11-30","total":50,"installments":3,
           "everyMonths":1,"confirmedAt":"2026-08-17T10:00:00Z"}]"""
        val rows = finance(duplicate).canonicalConfirmedLendingPlanRows()
        assertEquals(2,rows.size)
        assertTrue(rows.all { it.total == null && it.validationError != null })
    }

    @Test fun subcentAndInvalidDatesOrCadenceFailClosedWithoutAmounts() {
        val bad = listOf(
            plan.replace("\"total\":100", "\"total\":100.001"),
            plan.replace("2026-10-31","2026-02-30"),
            plan.replace("\"installments\":3", "\"installments\":61"),
            plan.replace("\"everyMonths\":1", "\"everyMonths\":0"),
            plan.replace("2026-08-17T10:00:00.000Z","bad-date"),
        )
        bad.forEach { text ->
            val row = finance(text).canonicalConfirmedLendingPlanRows().single()
            assertNull(row.total)
            assertTrue(row.validationError != null)
        }
    }
}
