package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web tests/lending-plans.test.ts as-of receivable, end-of-month and exact-cent goldens. */
class WebLendingSettlementProjectionParityTest {
    private fun finance() = CanonicalFinanceDocument(Json.parseToJsonElement("""
       {"seed":{"accounts":[{"id":"cash","kind":"cash"}],
         "lending":[{"person":"Alex","outstanding":100,"entries":[
           {"date":"2026-01-01","lent":100,"repaid":0}
         ]}]},
        "state":{"events":[],"settings":{}}}
    """.trimIndent()).jsonObject)

    @Test fun octoberThirtyFirstThreePaymentsClampAndRemainCentExact() {
        val data = finance()
        val before = data.raw
        val proposal = data.webLendingSettlementProjection(
            " Alex ", "2026-08-17", "2026-10-31", 100.0, 3)
        assertEquals("Alex", proposal.person)
        assertEquals(100.0, proposal.outstanding, 0.00001)
        assertEquals(listOf("2026-10-31","2026-11-30","2026-12-31"),
            proposal.schedule.map { it.dueDate })
        assertEquals(listOf(33.33,33.33,33.34), proposal.schedule.map { it.amount })
        assertEquals(100.0, proposal.schedule.sumOf { it.amount }, 0.00001)
        assertTrue(proposal.note.contains("καμία οικονομική κίνηση"))
        assertEquals(before, data.raw)
        assertTrue(data.canonicalEvents().isEmpty())
    }

    @Test fun refusesUnverifiedPersonOrAmountsAboveRealOutstanding() {
        val data = finance()
        assertThrows(IllegalArgumentException::class.java) {
            data.webLendingSettlementProjection(
                "Unknown", "2026-08-17", "2026-10-31", 50.0, 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            data.webLendingSettlementProjection(
                "Alex", "2026-08-17", "2026-10-31", 100.01, 3)
        }
    }

    @Test fun invalidDatesAndSubcentOrNonPositiveMoneyFailClosed() {
        val data = finance()
        for (due in listOf("2026-08-16","2026-02-30","2026-11","not-date")) {
            assertThrows(IllegalArgumentException::class.java) {
                data.webLendingSettlementProjection("Alex","2026-08-17",due,20.0,2)
            }
        }
        for (amount in listOf(0.0,-2.0,50.001)) {
            assertThrows(IllegalArgumentException::class.java) {
                data.webLendingSettlementProjection("Alex","2026-08-17",
                    "2026-10-31",amount,2)
            }
        }
    }

    @Test fun installmentCountCadenceAndMinimumCentAreValidated() {
        val data = finance()
        for (count in listOf(0,61)) {
            assertThrows(IllegalArgumentException::class.java) {
                data.webLendingSettlementProjection("Alex","2026-08-17",
                    "2026-10-31",10.0,count)
            }
        }
        for (months in listOf(0,25)) {
            assertThrows(IllegalArgumentException::class.java) {
                data.webLendingSettlementProjection("Alex","2026-08-17",
                    "2026-10-31",10.0,2,months)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            data.webLendingSettlementProjection("Alex","2026-08-17",
                "2026-10-31",0.03,5)
        }
    }

    @Test fun recurringEveryTwoMonthsUsesOriginalDayAndDoesNotPostMoney() {
        val data = finance()
        val before = data.raw
        val proposal = data.webLendingSettlementProjection(
            "Alex","2026-08-17","2026-10-31",10.0,3,2)
        assertEquals(listOf("2026-10-31","2026-12-31","2027-02-28"),
            proposal.schedule.map { it.dueDate })
        assertEquals(listOf(3.33,3.33,3.34),proposal.schedule.map { it.amount })
        assertEquals(before,data.raw)
    }
}
