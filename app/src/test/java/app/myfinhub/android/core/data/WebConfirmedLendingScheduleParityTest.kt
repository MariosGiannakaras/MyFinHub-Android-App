package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cross-client lendingPlans: validated saved intentions are not receipts. */
class WebConfirmedLendingScheduleParityTest {
    private fun data(): CanonicalFinanceDocument =
        CanonicalFinanceDocument(Json.parseToJsonElement("""
            {"seed":{"lending":[{"person":"Alex","outstanding":100,
                "entries":[{"date":"2026-01-01","lent":100}]}]},
             "state":{"events":[{"id":"repay","kind":"repayment","date":"2026-09-20",
                "person":"Alex","amount":60,"receivableDelta":-60}],
               "lendingPlans":[{"id":"confirmed-1","person":"Alex","asOf":"2026-08-17",
                 "firstDueDate":"2026-10-31","total":100,"installments":3,
                 "everyMonths":1,"confirmedAt":"2026-08-17T10:00:00Z"}]}}
        """.trimIndent()).jsonObject)

    @Test fun savedScheduleKeepsOriginalTotalDespiteRealRepayment() {
        val data = data()
        val before = data.raw
        val confirmed = data.canonicalConfirmedLendingPlanRows().single()
        val schedule = confirmed.canonicalConfirmedSchedule("2026-11-30")!!
        assertEquals(listOf("2026-10-31","2026-11-30","2026-12-31"),
            schedule.installments.map { it.dueDate })
        assertEquals(listOf(33.33,33.33,33.34),
            schedule.installments.map { it.amount })
        assertEquals(100.0,schedule.installments.sumOf { it.amount },0.00001)
        assertEquals("planned",schedule.installments.last().status)
        assertEquals(1,schedule.pastDueDates)
        assertEquals(40.0,data.canonicalLendingPersonRows("2026-11-30")
            .single { it.person == "Alex" }.outstanding,0.00001)
        assertEquals(1,data.canonicalEvents().size)
        assertEquals(before,data.raw)
    }

    @Test fun equalDueDateIsNotPastAndNoAsOfNeverAssumesPayment() {
        val confirmed=data().canonicalConfirmedLendingPlanRows().single()
        assertEquals(0,confirmed.canonicalConfirmedSchedule("2026-10-31")!!.pastDueDates)
        assertEquals(1,confirmed.canonicalConfirmedSchedule("2026-11-30")!!.pastDueDates)
        assertEquals(2,confirmed.canonicalConfirmedSchedule("2026-12-01")!!.pastDueDates)
        val unknown=confirmed.canonicalConfirmedSchedule(null)!!
        assertNull(unknown.asOfDate)
        assertEquals(0,unknown.pastDueDates)
    }

    @Test fun sixtyInstallmentBimonthlyCalendarAndCentsStayExact() {
        val all=canonicalLendingInstallmentSchedule("2026-10-31",60.0,60,2)
        assertEquals(60,all.size)
        assertEquals("2026-10-31",all.first().dueDate)
        assertEquals("2026-12-31",all[1].dueDate)
        assertEquals("2027-02-28",all[2].dueDate)
        assertEquals("2036-08-31",all.last().dueDate)
        assertTrue(all.all { it.amount == 1.0 && it.status == "planned" })
        assertEquals(60.0,all.sumOf { it.amount },0.00001)
    }

    @Test fun malformedOrUntrustedPlanCannotProduceImaginedSchedule() {
        val data=data()
        val row=data.canonicalConfirmedLendingPlanRows().single()
        assertNull(row.canonicalConfirmedSchedule("2026-02-30"))
        assertNull(row.copy(validationError="Απαιτείται έλεγχος.")
            .canonicalConfirmedSchedule("2026-12-01"))
        assertNull(row.copy(firstDueDate="2026-02-30")
            .canonicalConfirmedSchedule("2026-12-01"))
        assertNull(row.copy(total=0.001).canonicalConfirmedSchedule("2026-12-01"))
        assertNull(row.copy(installments=100).canonicalConfirmedSchedule("2026-12-01"))
        assertEquals(1,data.canonicalEvents().size)
    }

    @Test fun savedAndProposedSchedulesUseIdenticalCalendarAllocator() {
        val data=data()
        val stored=data.canonicalConfirmedLendingPlanRows().single()
            .canonicalConfirmedSchedule("2026-10-27")!!.installments
        val proposed=canonicalLendingSettlementProposal(
            "Alex",100.0,"2026-08-17","2026-10-31",100.0,3)
        assertEquals(proposed.schedule,stored)
    }
}
