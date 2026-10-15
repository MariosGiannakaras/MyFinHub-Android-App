package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalCreditCycleDateParityTest {
    private fun date(s: String) = LocalDate.parse(s)

    @Test fun closingDayBoundaryMatchesWebForBothRules() {
        assertEquals(date("2026-10-25"),
            statementCloseDateForPurchase(date("2026-10-25"),25,"include-closing-day"))
        assertEquals(date("2026-11-25"),
            statementCloseDateForPurchase(date("2026-10-25"),25,"next-cycle"))
        assertEquals(date("2026-10-25"),
            statementCloseDateForPurchase(date("2026-10-24"),25,"next-cycle"))
        assertEquals(date("2026-11-25"),
            statementCloseDateForPurchase(date("2026-10-26"),25,"include-closing-day"))
    }

    @Test fun leapYearAndShortMonthsClampClosingAndDueDatesIndependently() {
        assertEquals(date("2027-02-28"),
            statementCloseDateForPurchase(date("2027-01-31"),31,"next-cycle"))
        assertEquals(date("2028-02-29"),
            statementCloseDateForPurchase(date("2028-02-20"),31,"include-closing-day"))
        assertEquals(date("2028-03-31"),
            statementCloseDateForPurchase(date("2028-02-29"),31,"next-cycle"))
        assertEquals(date("2027-02-28"),
            statementDueDateForClose(date("2027-01-31"),31))
        assertEquals(date("2026-10-30"),
            statementDueDateForClose(date("2026-10-25"),30))
        assertEquals(date("2026-11-05"),
            statementDueDateForClose(date("2026-10-25"),5))
    }

    @Test fun cycleOpensExactlyAtPreviousMonthBoundaryByRule() {
        assertEquals(date("2026-03-01"),
            statementOpenDateForClose(date("2026-03-31"),31))
        assertEquals(date("2026-02-28"),
            statementOpenDateForClose(date("2026-03-31"),31,"next-cycle"))
        assertEquals("credit-1:2026-10-25",
            canonicalCreditStatementId("credit-1",date("2026-10-25")))
    }

    @Test fun invalidDatesAndIncompleteCreditCardConfigurationFailClosed() {
        for (day in listOf(0,32)) {
            assertTrue(runCatching { statementCloseDateForPurchase(date("2026-10-01"),day,"next-cycle") }.isFailure)
            assertTrue(runCatching { statementDueDateForClose(date("2026-10-25"),day) }.isFailure)
        }
        assertTrue(runCatching { statementCloseDateForPurchase(date("2026-10-25"),25,"unexpected") }.isFailure)
        val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[]},"state":{"cards":[
             {"id":"valid","kind":"credit","network":"visa","active":true,
              "statementClosingDay":25,"statementDueDay":5,"statementBoundaryRule":"next-cycle"},
             {"id":"debit","kind":"debit","network":"visa","active":true,
              "statementClosingDay":25,"statementDueDay":5,"statementBoundaryRule":"next-cycle"},
             {"id":"missing","kind":"credit","network":"visa","active":true,
              "statementClosingDay":25,"statementDueDay":5},
             {"id":"fractional","kind":"credit","network":"visa","active":true,
              "statementClosingDay":25.5,"statementDueDay":5,"statementBoundaryRule":"next-cycle"}
          ]}}
        """.trimIndent()).jsonObject)
        val cards=doc.canonicalCards().associateBy { it.id }
        assertEquals(CanonicalStatementConfiguration(25,5,"next-cycle"),
            cards.getValue("valid").statementConfiguration())
        assertNull(cards.getValue("debit").statementConfiguration())
        assertNull(cards.getValue("missing").statementConfiguration())
        assertNull(cards.getValue("fractional").statementConfiguration())
    }
}
