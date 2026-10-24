package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web cashFlowForecast source/day/account golden vectors (no finance writes). */
class WebCashFlowForecastParityTest {
    private val today = LocalDate.parse("2026-10-15")
    private fun document(
        scheduled: String = """
          {"id":"overdue","dueDate":"2026-10-10","kind":"expense","amount":30,"accountId":"bank","status":"pending"},
          {"id":"income","dueDate":"2026-10-18","kind":"income","amount":100,"accountId":"bank","status":"pending"},
          {"id":"transfer","dueDate":"2026-10-17","kind":"transfer","amount":50,"fromAccountId":"bank","toAccountId":"savings","status":"pending"}
        """,
        recurring: String = """
          {"id":"subscription","name":"Πάγιο","amount":40,"firstExpectedDate":"2026-10-20",
           "active":true,"accountId":"bank","category":"Άλλο"}
        """,
        loans: String = """
          {"id":"loan","name":"Δάνειο","installment":60,"total":120,"installments":2,
           "day":"21","defaultAccountId":"bank"}
        """,
        events: String = """
          {"id":"future","date":"2026-10-22","kind":"expense","amount":25,
           "legs":[{"accountId":"bank","amount":-25}]}
        """,
    ): CanonicalFinanceDocument = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[
          {"id":"bank","kind":"bank","name":"Μισθοδοσία"},
          {"id":"savings","kind":"savings","name":"Στόχος","excludeFromAvailable":true},
          {"id":"credit","kind":"credit","name":"Πίστωση"}
         ],"snapshots":[{"date":"2026-10-15","balances":{"bank":800,"savings":200,"credit":-900}}],
         "transactions":[],"recurring":[$recurring],"loans":[$loans]},
       "state":{"settings":{},"events":[$events],"scheduled":[$scheduled]}}
    """.trimIndent()).jsonObject)

    @Test fun portfolioIncludesExcludedSavingsButNotCreditAndTransfersNetToZero() {
        val p = document().cashFlowForecast(today, 30)
        assertEquals(1000.0,p.currentPortfolio,0.001)
        assertEquals(945.0,p.projectedPortfolio,0.001)
        assertEquals(setOf("bank","savings"),p.accounts.map { it.accountId }.toSet())
        assertEquals(0.0,p.movements.first { it.id=="forecast-scheduled-transfer" }.portfolioDelta,0.001)
        assertEquals(today,p.movements.first { it.id=="forecast-scheduled-overdue" }.date)
        assertEquals(250.0,p.accounts.first { it.accountId=="savings" }.projected,0.001)
        assertEquals(mapOf("scheduled" to 3,"recurring" to 1,"loan" to 1,"dated_event" to 1),p.sourceCounts)
    }

    @Test fun longerHorizonsRespectRecurringCadenceAndRemainingLoanCount() {
        val p60=document().cashFlowForecast(today,60)
        val p90=document().cashFlowForecast(today,90)
        assertEquals(845.0,p60.projectedPortfolio,0.001)
        assertEquals(805.0,p90.projectedPortfolio,0.001)
        assertEquals(2,p60.sourceCounts["recurring"])
        assertEquals(3,p90.sourceCounts["recurring"])
        assertEquals(2,p90.sourceCounts["loan"])
        assertEquals(p90.endDate,p90.points.last().date)
    }

    @Test fun missingAccountReferencesAreOmittedAndNeverInventMoney() {
        val s="""{"id":"missing","dueDate":"2026-10-20","kind":"expense","amount":500,
            "accountId":"deleted","status":"pending"}"""
        val r="""{"id":"r","name":"R","amount":100,"day":20,"accountId":"deleted","active":true}"""
        val l="""{"id":"loan","name":"Loan","total":100,"installment":100,
            "installments":1,"day":"20","defaultAccountId":"deleted"}"""
        val p=document(scheduled=s,recurring=r,loans=l,events="").cashFlowForecast(today,30)
        assertEquals(1000.0,p.projectedPortfolio,0.001)
        assertTrue(p.insufficientData)
        assertEquals(3,p.omitted.size)
    }

    @Test fun firstLowAndNegativeCrossingPersistAfterIncomeRecovery() {
        val s="""{"id":"charge","dueDate":"2026-10-16","kind":"expense","amount":900,
            "accountId":"bank","status":"pending"},
            {"id":"recover","dueDate":"2026-10-18","kind":"income","amount":1000,
            "accountId":"bank","status":"pending"}"""
        val p=document(scheduled=s,recurring="",loans="",events="").cashFlowForecast(today,30)
        val bank=p.accounts.first { it.accountId=="bank" }
        assertEquals(LocalDate.parse("2026-10-16"),bank.firstLowDate)
        assertEquals(LocalDate.parse("2026-10-16"),bank.firstNegativeDate)
        assertEquals(-100.0,bank.minimum,0.001)
        assertEquals(100.0,p.minimumPortfolio,0.001)
        assertEquals(1100.0,p.projectedPortfolio,0.001)
    }

    @Test fun monthEndLoanForecastRestoresTheThirtyFirstAfterFebruaryInCommonAndLeapYears() {
        for ((year, february) in listOf(2027 to "2027-02-28", 2028 to "2028-02-29")) {
            val loan = """{"id":"month-end","name":"Month-end loan","installment":100,
                "total":600,"installments":6,"day":"31",
                "firstExpectedDate":"$year-01-31","defaultAccountId":"bank"}"""
            val projection = document(scheduled="", recurring="", loans=loan, events="")
                .cashFlowForecast(LocalDate.parse("$year-01-20"), 90)
            val dates = projection.movements.filter { it.source == "loan" }.map { it.date.toString() }
            assertEquals(listOf("$year-01-31", february, "$year-03-31"), dates)
            assertEquals(3, projection.sourceCounts["loan"])
        }
    }

    @Test fun settledMonthEndLoanCycleStartsNextMonthWithoutShiftingLaterDueDates() {
        val loan = """{"id":"month-end","name":"Month-end loan","installment":100,
            "total":600,"installments":6,"day":"31",
            "firstExpectedDate":"2027-01-31","defaultAccountId":"bank"}"""
        val alreadyPaid = """{"id":"loan-paid","kind":"loan_payment","date":"2027-01-31",
            "loanId":"month-end","amount":100,"installmentCount":1,"legs":[]}"""
        val projection = document(scheduled="", recurring="", loans=loan, events=alreadyPaid)
            .cashFlowForecast(LocalDate.parse("2027-01-31"), 90)
        val dates = projection.movements.filter { it.source == "loan" }.map { it.date.toString() }
        assertEquals(listOf("2027-02-28", "2027-03-31", "2027-04-30"), dates)
        assertEquals(3, projection.sourceCounts["loan"])
    }

    @Test fun omittedForecastExplanationsIdentifyTheAffectedRecordsInsteadOfInternalIds() {
        val scheduled = """
          {"id":"opaque-s-1","note":"Ενοίκιο","dueDate":"2026-10-20",
           "kind":"expense","amount":600,"accountId":"missing","status":"pending"},
          {"id":"opaque-s-2","note":"Μεταφορά στόχου","dueDate":"2026-10-21",
           "kind":"transfer","amount":40,"fromAccountId":"bank",
           "toAccountId":"missing","status":"pending"}
        """.trimIndent()
        val recurring = """
          {"id":"opaque-r-1","name":"Συνδρομή μουσικής","amount":20,
           "day":20,"active":true,"accountId":"missing"},
          {"id":"opaque-r-2","name":"Ετήσια υπηρεσία","amount":70,
           "recurrenceUnit":"year","active":true,"accountId":"bank"}
        """.trimIndent()
        val loans = """
          {"id":"opaque-l-1","name":"Δόση οχήματος","installment":100,
           "total":600,"installments":6,"day":"20","defaultAccountId":"missing"},
          {"id":"opaque-l-2","name":"Δόση σπουδών","installment":80,
           "total":400,"installments":5,"defaultAccountId":"bank"}
        """.trimIndent()
        val projection = document(scheduled, recurring, loans, events = "")
            .cashFlowForecast(today, 30)
        assertEquals(listOf(
            "Η προγραμματισμένη κίνηση «Ενοίκιο» δεν προβλήθηκε επειδή ο λογαριασμός της δεν είναι πλέον διαθέσιμος.",
            "Η προγραμματισμένη κίνηση «Μεταφορά στόχου» δεν προβλήθηκε επειδή οι λογαριασμοί της δεν είναι πλέον έγκυροι.",
            "Το πάγιο «Συνδρομή μουσικής» δεν προβλήθηκε επειδή ο λογαριασμός του δεν είναι διαθέσιμος.",
            "Το πάγιο «Ετήσια υπηρεσία» δεν προβλήθηκε επειδή δεν υπάρχει αρκετή πληροφορία ημερομηνίας.",
            "Η δόση «Δόση οχήματος» δεν προβλήθηκε επειδή ο λογαριασμός πληρωμής δεν είναι διαθέσιμος.",
            "Η δόση «Δόση σπουδών» δεν προβλήθηκε επειδή δεν υπάρχει γνωστή ημέρα πληρωμής.",
        ), projection.omitted)
        assertTrue(projection.insufficientData)
        assertEquals(1000.0, projection.projectedPortfolio, 0.001)
        assertFalse(projection.omitted.joinToString().contains("opaque-"))
    }

    @Test fun invalidHorizonRejectedInsteadOfClamped() {
        assertThrows(IllegalArgumentException::class.java) {
            document().cashFlowForecast(today,45)
        }
    }
}
